package main

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"path/filepath"
	"testing"
	"time"
)

func hash(s string) string { h := sha256.Sum256([]byte(s)); return hex.EncodeToString(h[:]) }
func testLedger(t *testing.T, n int) (*Ledger, []ed25519.PrivateKey) {
	t.Helper()
	peers := map[string]ed25519.PublicKey{}
	privs := []ed25519.PrivateKey{}
	for i := 0; i < n; i++ {
		pub, priv, e := ed25519.GenerateKey(rand.Reader)
		if e != nil {
			t.Fatal(e)
		}
		id := fmt.Sprintf("peer-%02d", i)
		peers[id] = pub
		privs = append(privs, priv)
	}
	return &Ledger{peers: peers, receipts: map[string]map[string]Attestation{}}, privs
}
func signed(t *testing.T, l *Ledger, priv ed25519.PrivateKey, id, artifact, verdict string) Attestation {
	t.Helper()
	eh := hash("evidence")
	a := Attestation{ArtifactHash: artifact, Verdict: verdict, EvidenceHashes: []string{eh}, EvidenceRoot: merkle([]string{eh}), IssuedAtEpochMs: time.Now().UnixMilli(), PeerID: id}
	a.SignatureBase64 = base64.StdEncoding.EncodeToString(ed25519.Sign(priv, canonical(a)))
	return a
}
func TestFixedPeerQuorumSevenOfTen(t *testing.T) {
	l, keys := testLedger(t, 10)
	artifact := hash("artifact-A")
	for i := 0; i < 10; i++ {
		v := "FAIL"
		if i < 7 {
			v = "PASS"
		}
		if e := l.ingest(signed(t, l, keys[i], fmt.Sprintf("peer-%02d", i), artifact, v)); e != nil {
			t.Fatal(e)
		}
	}
	c := l.consensus(artifact)
	if !c.Accepted || c.RequiredQuorum != 7 || c.AgreeingPeers != 7 || c.FinalVerdict != "PASS" {
		t.Fatalf("unexpected consensus: %+v", c)
	}
}
func TestQuorumDoesNotShrinkToReceivedVotes(t *testing.T) {
	l, keys := testLedger(t, 10)
	artifact := hash("artifact-B")
	for i := 0; i < 6; i++ {
		if e := l.ingest(signed(t, l, keys[i], fmt.Sprintf("peer-%02d", i), artifact, "PASS")); e != nil {
			t.Fatal(e)
		}
	}
	c := l.consensus(artifact)
	if c.Accepted || c.RequiredQuorum != 7 {
		t.Fatalf("quorum incorrectly accepted: %+v", c)
	}
}
func TestRejectTamperedAndDuplicateReceipts(t *testing.T) {
	l, keys := testLedger(t, 3)
	artifact := hash("artifact-C")
	a := signed(t, l, keys[0], "peer-00", artifact, "PASS")
	a.SignatureBase64 = "AA=="
	if l.ingest(a) == nil {
		t.Fatal("tampered signature accepted")
	}
	a = signed(t, l, keys[0], "peer-00", artifact, "PASS")
	if e := l.ingest(a); e != nil {
		t.Fatal(e)
	}
	if e := l.ingest(a); e != nil {
		t.Fatalf("identical retry should be idempotent: %v", e)
	}
	b := signed(t, l, keys[0], "peer-00", artifact, "FAIL")
	if l.ingest(b) == nil {
		t.Fatal("conflicting duplicate accepted")
	}
}
func TestPersistentLedgerReload(t *testing.T) {
	l, keys := testLedger(t, 1)
	l.path = filepath.Join(t.TempDir(), "ledger.json")
	artifact := hash("persist")
	if e := l.ingest(signed(t, l, keys[0], "peer-00", artifact, "PASS")); e != nil {
		t.Fatal(e)
	}
	copy := &Ledger{peers: l.peers, receipts: map[string]map[string]Attestation{}, path: l.path}
	if e := loadLedger(copy); e != nil {
		t.Fatal(e)
	}
	if copy.consensus(artifact).TotalValid != 1 {
		t.Fatal("receipt not restored")
	}
}
