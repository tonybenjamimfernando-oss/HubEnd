// GoPA v2: Ed25519-authenticated audit attestations, fixed peer-set quorum, TLS-only transport.
package main

import (
	"crypto/ed25519"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

type Attestation struct {
	ArtifactHash    string   `json:"artifactHash"`
	Verdict         string   `json:"verdict"`
	EvidenceHashes  []string `json:"evidenceHashes"`
	EvidenceRoot    string   `json:"evidenceRoot"`
	IssuedAtEpochMs int64    `json:"issuedAtEpochMs"`
	PeerID          string   `json:"peerId"`
	SignatureBase64 string   `json:"signatureBase64"`
}
type Consensus struct {
	ArtifactHash       string   `json:"artifactHash"`
	FinalVerdict       string   `json:"finalVerdict"`
	RequiredQuorum     int      `json:"requiredQuorum"`
	TotalEligiblePeers int      `json:"totalEligiblePeers"`
	TotalValid         int      `json:"totalValid"`
	AgreeingPeers      int      `json:"agreeingPeers"`
	Accepted           bool     `json:"accepted"`
	Dissenters         []string `json:"dissenters"`
	Reason             string   `json:"reason"`
}
type Ledger struct {
	mu       sync.RWMutex
	peers    map[string]ed25519.PublicKey
	receipts map[string]map[string]Attestation
	path     string
}

func validHash(s string) bool { b, e := hex.DecodeString(s); return e == nil && len(b) == sha256.Size }
func canonical(a Attestation) []byte {
	return []byte(strings.Join([]string{"GOPA-ATTESTATION-V1", strings.ToLower(a.ArtifactHash), a.Verdict, strings.ToLower(a.EvidenceRoot), fmt.Sprint(a.IssuedAtEpochMs), a.PeerID, lowerJoin(a.EvidenceHashes)}, "\n"))
}
func lowerJoin(hashes []string) string {
	out := make([]string, len(hashes))
	for i, h := range hashes {
		out[i] = strings.ToLower(h)
	}
	return strings.Join(out, ",")
}
func merkle(hashes []string) string {
	level := make([][]byte, 0, len(hashes))
	for _, h := range hashes {
		b, _ := hex.DecodeString(strings.ToLower(h))
		level = append(level, b)
	}
	for len(level) > 1 {
		next := make([][]byte, 0, (len(level)+1)/2)
		for i := 0; i < len(level); i += 2 {
			r := level[i]
			if i+1 < len(level) {
				r = level[i+1]
			}
			x := append([]byte{1}, append(append([]byte{}, level[i]...), r...)...)
			d := sha256.Sum256(x)
			next = append(next, d[:])
		}
		level = next
	}
	return hex.EncodeToString(level[0])
}
func (l *Ledger) verify(a Attestation, now time.Time) error {
	if len(a.PeerID) == 0 || len(a.PeerID) > 128 || len(a.SignatureBase64) > 256 {
		return errors.New("invalid peer identity or signature size")
	}
	if !validHash(a.ArtifactHash) || !validHash(a.EvidenceRoot) || len(a.EvidenceHashes) == 0 || len(a.EvidenceHashes) > 10000 {
		return errors.New("invalid hashes/evidence count")
	}
	for _, h := range a.EvidenceHashes {
		if !validHash(h) {
			return errors.New("invalid evidence hash")
		}
	}
	if !strings.EqualFold(merkle(a.EvidenceHashes), a.EvidenceRoot) {
		return errors.New("merkle root mismatch")
	}
	if a.Verdict != "PASS" && a.Verdict != "FAIL" && a.Verdict != "SUSPICIOUS" {
		return errors.New("invalid verdict")
	}
	if a.IssuedAtEpochMs < 0 || a.IssuedAtEpochMs > now.Add(5*time.Minute).UnixMilli() {
		return errors.New("attestation timestamp outside accepted window")
	}
	pub, ok := l.peers[a.PeerID]
	if !ok {
		return errors.New("unknown peer")
	}
	sig, e := base64.StdEncoding.DecodeString(a.SignatureBase64)
	if e != nil || !ed25519.Verify(pub, canonical(a), sig) {
		return errors.New("invalid signature")
	}
	return nil
}
func (l *Ledger) ingest(a Attestation) error {
	if e := l.verify(a, time.Now()); e != nil {
		return e
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	a.ArtifactHash = strings.ToLower(a.ArtifactHash)
	for i := range a.EvidenceHashes {
		a.EvidenceHashes[i] = strings.ToLower(a.EvidenceHashes[i])
	}
	a.EvidenceRoot = strings.ToLower(a.EvidenceRoot)
	total := 0
	for _, group := range l.receipts {
		total += len(group)
	}
	if total >= 1_000_000 {
		return errors.New("ledger receipt limit reached")
	}
	byPeer := l.receipts[a.ArtifactHash]
	if byPeer == nil {
		byPeer = map[string]Attestation{}
		l.receipts[a.ArtifactHash] = byPeer
	}
	if old, ok := byPeer[a.PeerID]; ok {
		if string(canonical(old)) == string(canonical(a)) && old.SignatureBase64 == a.SignatureBase64 {
			return nil
		}
		return errors.New("peer already attested this artifact; immutable receipt")
	}
	byPeer[a.PeerID] = a
	if l.path != "" {
		if e := l.persistLocked(); e != nil {
			delete(byPeer, a.PeerID)
			return e
		}
	}
	return nil
}
func (l *Ledger) persistLocked() error {
	if l.path == "" {
		return nil
	}
	if e := os.MkdirAll(filepath.Dir(l.path), 0700); e != nil {
		return e
	}
	all := []Attestation{}
	for _, m := range l.receipts {
		for _, a := range m {
			all = append(all, a)
		}
	}
	b, e := json.MarshalIndent(all, "", "  ")
	if e != nil {
		return e
	}
	tmp := l.path + ".tmp"
	if e = os.WriteFile(tmp, b, 0600); e != nil {
		return e
	}
	return os.Rename(tmp, l.path)
}
func loadLedger(l *Ledger) error {
	if l.path == "" {
		return nil
	}
	b, e := os.ReadFile(l.path)
	if os.IsNotExist(e) {
		return nil
	}
	if e != nil {
		return e
	}
	var rows []Attestation
	if e = json.Unmarshal(b, &rows); e != nil {
		return e
	}
	for _, a := range rows {
		if e = l.verify(a, time.Now()); e != nil {
			return fmt.Errorf("persisted receipt invalid: %w", e)
		}
		m := l.receipts[a.ArtifactHash]
		if m == nil {
			m = map[string]Attestation{}
			l.receipts[a.ArtifactHash] = m
		}
		if _, ok := m[a.PeerID]; ok {
			return errors.New("duplicate persisted peer receipt")
		}
		m[a.PeerID] = a
	}
	return nil
}
func (l *Ledger) consensus(hash string) Consensus {
	eligible := len(l.peers)
	quorum := (2*eligible)/3 + 1
	l.mu.RLock()
	defer l.mu.RUnlock()
	m := l.receipts[strings.ToLower(hash)]
	type group struct {
		verdict, root string
		peers         []string
	}
	groups := map[string]*group{}
	valid := 0
	for id, a := range m {
		if l.verify(a, time.Now()) != nil {
			continue
		}
		valid++
		k := a.Verdict + ":" + a.EvidenceRoot
		g := groups[k]
		if g == nil {
			g = &group{verdict: a.Verdict, root: a.EvidenceRoot}
			groups[k] = g
		}
		g.peers = append(g.peers, id)
	}
	var win *group
	for _, g := range groups {
		if win == nil || len(g.peers) > len(win.peers) {
			win = g
		}
	}
	c := Consensus{ArtifactHash: strings.ToLower(hash), FinalVerdict: "NO_CONSENSUS", RequiredQuorum: quorum, TotalEligiblePeers: eligible, TotalValid: valid}
	if win == nil {
		c.Reason = "no valid attestations"
		return c
	}
	c.AgreeingPeers = len(win.peers)
	sort.Strings(win.peers)
	for id, a := range m {
		if l.verify(a, time.Now()) == nil && (a.Verdict != win.verdict || a.EvidenceRoot != win.root) {
			c.Dissenters = append(c.Dissenters, id)
		}
	}
	sort.Strings(c.Dissenters)
	if c.AgreeingPeers >= quorum {
		c.Accepted = true
		c.FinalVerdict = win.verdict
		c.Reason = "fixed eligible-peer quorum reached"
	} else {
		c.Reason = "insufficient distinct eligible peers"
	}
	return c
}

type Server struct {
	ledger *Ledger
	token  string
	peers  []string
	client *http.Client
}

func (s *Server) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		got := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
		if len(got) != len(s.token) || subtle.ConstantTimeCompare([]byte(got), []byte(s.token)) != 1 {
			http.Error(w, "unauthorized", 401)
			return
		}
		next(w, r)
	}
}
func jsonOut(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}
func (s *Server) attest(w http.ResponseWriter, r *http.Request) {
	if r.Method != "POST" {
		http.Error(w, "method not allowed", 405)
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, 2<<20)
	defer r.Body.Close()
	var a Attestation
	decoder := json.NewDecoder(r.Body)
	if e := decoder.Decode(&a); e != nil {
		http.Error(w, "invalid json", 400)
		return
	}
	if e := decoder.Decode(&struct{}{}); e != io.EOF {
		http.Error(w, "trailing JSON data", 400)
		return
	}
	if e := s.ledger.ingest(a); e != nil {
		http.Error(w, e.Error(), 401)
		return
	}
	w.WriteHeader(201)
	jsonOut(w, map[string]string{"status": "accepted"})
}
func (s *Server) consensus(w http.ResponseWriter, r *http.Request) {
	h := strings.TrimPrefix(r.URL.Path, "/consensus/")
	if !validHash(h) {
		http.Error(w, "invalid artifact hash", 400)
		return
	}
	jsonOut(w, s.ledger.consensus(h))
}
func (s *Server) delta(w http.ResponseWriter, r *http.Request) {
	if len(r.URL.RawQuery) > 8192 {
		http.Error(w, "known set too large", http.StatusRequestURITooLong)
		return
	}
	known := map[string]bool{}
	for _, v := range strings.Split(r.URL.Query().Get("known"), ",") {
		known[v] = true
	}
	s.ledger.mu.RLock()
	defer s.ledger.mu.RUnlock()
	out := []Attestation{}
	for _, m := range s.ledger.receipts {
		for id, a := range m {
			if !known[id+":"+a.ArtifactHash] {
				out = append(out, a)
			}
		}
	}
	jsonOut(w, out)
}
func (s *Server) gossipLoop() {
	ticker := time.NewTicker(20 * time.Second)
	defer ticker.Stop()
	for range ticker.C {
		for _, peer := range s.peers {
			go func(base string) {
				s.ledger.mu.RLock()
				known := []string{}
				for _, m := range s.ledger.receipts {
					for id, a := range m {
						known = append(known, id+":"+a.ArtifactHash)
					}
				}
				s.ledger.mu.RUnlock()
				q := url.Values{"known": {strings.Join(known, ",")}}
				req, e := http.NewRequest("GET", strings.TrimRight(base, "/")+"/gossip/delta?"+q.Encode(), nil)
				if e != nil {
					return
				}
				req.Header.Set("Authorization", "Bearer "+s.token)
				resp, e := s.client.Do(req)
				if e != nil {
					return
				}
				defer resp.Body.Close()
				if resp.StatusCode != 200 {
					return
				}
				var rows []Attestation
				if json.NewDecoder(io.LimitReader(resp.Body, 8<<20)).Decode(&rows) != nil {
					return
				}
				for _, a := range rows {
					if e := s.ledger.ingest(a); e == nil {
						log.Printf("gossip accepted peer=%s artifact=%s", a.PeerID, a.ArtifactHash)
					}
				}
			}(peer)
		}
	}
}
func main() {
	listen := flag.String("listen", ":8443", "listen address")
	peersFile := flag.String("peer-keys", "", "JSON map peer ID to base64 X.509 Ed25519 public key")
	peersFlag := flag.String("peers", "", "comma-separated HTTPS peer URLs")
	token := flag.String("api-token", os.Getenv("GOPA_API_TOKEN"), "shared transport bearer token (32+ chars)")
	cert := flag.String("tls-cert", "", "TLS certificate PEM")
	key := flag.String("tls-key", "", "TLS private key PEM")
	tlsCA := flag.String("tls-ca", "", "optional PEM CA/certificate for outbound peer TLS")
	ledgerPath := flag.String("ledger", "./data/gopa-ledger.json", "durable ledger path")
	flag.Parse()
	if len(*token) < 32 || *cert == "" || *key == "" || *peersFile == "" {
		log.Fatal("required: -api-token (32+ chars), -tls-cert, -tls-key, -peer-keys")
	}
	raw, e := os.ReadFile(*peersFile)
	if e != nil {
		log.Fatal(e)
	}
	var encoded map[string]string
	if e = json.Unmarshal(raw, &encoded); e != nil || len(encoded) == 0 {
		log.Fatal("invalid peer key registry")
	}
	pubs := map[string]ed25519.PublicKey{}
	for id, b64 := range encoded {
		der, e := base64.StdEncoding.DecodeString(b64)
		if e != nil {
			log.Fatal(e)
		}
		k, e := x509.ParsePKIXPublicKey(der)
		if e != nil {
			log.Fatal(e)
		}
		p, ok := k.(ed25519.PublicKey)
		if !ok {
			log.Fatalf("peer %s key is not Ed25519", id)
		}
		pubs[id] = p
	}
	l := &Ledger{peers: pubs, receipts: map[string]map[string]Attestation{}, path: *ledgerPath}
	if e = loadLedger(l); e != nil {
		log.Fatal(e)
	}
	peers := []string{}
	if *peersFlag != "" {
		peers = strings.Split(*peersFlag, ",")
	}
	for _, p := range peers {
		if !strings.HasPrefix(p, "https://") {
			log.Fatalf("gossip peer must use https: %s", p)
		}
	}
	roots, err := x509.SystemCertPool()
	if err != nil || roots == nil {
		roots = x509.NewCertPool()
	}
	if *tlsCA != "" {
		pemBytes, readErr := os.ReadFile(*tlsCA)
		if readErr != nil {
			log.Fatal(readErr)
		}
		block, _ := pem.Decode(pemBytes)
		if block == nil || !roots.AppendCertsFromPEM(pemBytes) {
			log.Fatal("invalid TLS CA PEM")
		}
	}
	transport := &http.Transport{TLSClientConfig: &tls.Config{MinVersion: tls.VersionTLS12, RootCAs: roots}}
	s := &Server{ledger: l, token: *token, peers: peers, client: &http.Client{Timeout: 10 * time.Second, Transport: transport}}
	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) { jsonOut(w, map[string]string{"status": "ok"}) })
	mux.HandleFunc("/attest", s.auth(s.attest))
	mux.HandleFunc("/consensus/", s.auth(s.consensus))
	mux.HandleFunc("/gossip/delta", s.auth(s.delta))
	go s.gossipLoop()
	srv := &http.Server{Addr: *listen, Handler: mux, TLSConfig: &tls.Config{MinVersion: tls.VersionTLS12}, ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 15 * time.Second, WriteTimeout: 15 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 16 << 10}
	log.Printf("GoPA TLS server on %s with %d eligible peers", *listen, len(pubs))
	log.Fatal(srv.ListenAndServeTLS(*cert, *key))
}
