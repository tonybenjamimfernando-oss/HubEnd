#!/usr/bin/env python3
"""Build real source before/after pairs from GHSA commit patches (best-effort, bounded)."""
import argparse, json, os, re, sys, time
from pathlib import Path
import requests

API = "https://api.github.com"
TOKEN = os.getenv("GITHUB_TOKEN")
HEADERS = {"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
if TOKEN: HEADERS["Authorization"] = f"Bearer {TOKEN}"
CWES = ["CWE-89", "CWE-327", "CWE-287", "CWE-295", "CWE-312", "CWE-532", "CWE-502", "CWE-798", "CWE-326", "CWE-319", "CWE-328", "CWE-330", "CWE-306", "CWE-259", "CWE-297", "CWE-522", "CWE-921", "CWE-117", "CWE-400"]
ALLOWED = (".kt", ".java", ".py", ".js", ".ts", ".go", ".c", ".cc", ".cpp", ".php", ".rb", ".rs")

def get(url, params=None):
    r = requests.get(url, headers=HEADERS, params=params, timeout=30)
    if r.status_code == 403 and r.headers.get("X-RateLimit-Remaining") == "0":
        raise RuntimeError("GitHub API rate limit reached; set GITHUB_TOKEN")
    r.raise_for_status(); return r.json()

def reconstruct(patch):
    before, after = [], []
    in_hunk = False
    for line in patch.splitlines():
        if line.startswith("@@"):
            in_hunk = True; continue
        if not in_hunk or line.startswith(("\\", "+++", "---")): continue
        if line.startswith(" "): before.append(line[1:]); after.append(line[1:])
        elif line.startswith("-"): before.append(line[1:])
        elif line.startswith("+"): after.append(line[1:])
    old, new = "\n".join(before), "\n".join(after)
    if not old.strip() or not new.strip() or old == new: return None
    return old, new

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="data/cve_pairs.jsonl"); ap.add_argument("--pages", type=int, default=3)
    ap.add_argument("--per-page", type=int, default=50); ap.add_argument("--delay", type=float, default=0.25)
    a = ap.parse_args()
    if not 1 <= a.pages <= 10 or not 1 <= a.per_page <= 100: ap.error("pages 1..10 and per-page 1..100")
    output = Path(a.out); output.parent.mkdir(parents=True, exist_ok=True)
    seen, total = set(), 0
    with output.open("w", encoding="utf-8") as f:
        for cwe in CWES:
            for page in range(1, a.pages + 1):
                try: advisories = get(f"{API}/advisories", {"cwe_id": cwe, "per_page": a.per_page, "page": page})
                except Exception as e: print(f"WARN {cwe}: {e}", file=sys.stderr); break
                if not advisories: break
                for adv in advisories:
                    for ref in adv.get("references", []):
                        url = ref.get("url", "")
                        m = re.search(r"github\.com/([^/]+/[^/]+)/commit/([0-9a-f]{7,40})", url)
                        if not m: continue
                        repo, sha = m.groups(); key = (repo, sha)
                        if key in seen: continue
                        seen.add(key)
                        try: commit = get(f"{API}/repos/{repo}/commits/{sha}")
                        except Exception: continue
                        cwe_ids = [x.get("cwe_id") for x in adv.get("cwes", [])]
                        chosen = next((x for x in cwe_ids if x in CWES), cwe)
                        for file in commit.get("files", []):
                            if not file.get("filename", "").lower().endswith(ALLOWED) or not file.get("patch"): continue
                            pair = reconstruct(file["patch"])
                            if not pair: continue
                            old, new = pair
                            if max(len(old), len(new)) > 12000: continue
                            row = {"cwe": chosen, "input": old, "output": new,
                                   "source": adv.get("ghsa_id"), "commit": sha,
                                   "repository": repo, "path": file.get("filename"),
                                   "provenance": "github-commit-unified-diff"}
                            f.write(json.dumps(row, ensure_ascii=False) + "\n"); total += 1
                time.sleep(a.delay)
    print(f"Wrote {total} source-level pairs to {output}", file=sys.stderr)
    if total == 0: raise SystemExit("No valid source-level pairs; do not train on advisory prose")
if __name__ == "__main__": main()
