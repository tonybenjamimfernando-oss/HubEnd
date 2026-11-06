#!/usr/bin/env python3
"""Valida android/hubops/src/main/res/raw/cve_database.json (mesmas regras do CveCatalog.parse)
e executa vetores de teste de correspondência de versões (port Python do algoritmo)."""
import json, re, sys, pathlib

DB = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "android/hubops/src/main/res/raw/cve_database.json")
SEV = {"INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL"}

def parse(v):
    m = re.match(r"(\d+(?:\.\d+)*)([a-z](?![a-z0-9]))?", v.strip())
    if not m: return None
    parts = [int(x) for x in m.group(1).split(".")]
    if m.group(2): parts.append(ord(m.group(2)) - ord("a") + 1)
    return parts

def cmp(a, b):
    n = max(len(a), len(b))
    a, b = a + [0] * (n - len(a)), b + [0] * (n - len(b))
    return (a > b) - (a < b)

def contains(r, v):
    pv = parse(v)
    if pv is None: return False
    if r.get("introduced") and cmp(pv, parse(r["introduced"])) < 0: return False
    if r.get("fixed") and cmp(pv, parse(r["fixed"])) >= 0: return False
    return True

errors, seen = [], set()
data = json.loads(DB.read_text(encoding="utf-8"))
for i, e in enumerate(data):
    tag = f"#{i} {e.get('cve')}"
    if not re.fullmatch(r"CVE-\d{4}-\d{4,}", e.get("cve", "")): errors.append(f"{tag}: CVE inválido")
    if e.get("severity") not in SEV: errors.append(f"{tag}: severidade inválida")
    if not e.get("ranges") and not e.get("affected"): errors.append(f"{tag}: sem intervalos")
    for r in e.get("ranges", []):
        if not (r.get("introduced") or r.get("fixed")): errors.append(f"{tag}: intervalo vazio")
        for k in ("introduced", "fixed"):
            if r.get(k) and parse(r[k]) is None: errors.append(f"{tag}: {k} inválido")
        if r.get("introduced") and r.get("fixed") and cmp(parse(r["introduced"]), parse(r["fixed"])) >= 0:
            errors.append(f"{tag}: introduced >= fixed")
    key = (e["cve"], e["library"])
    if key in seen: errors.append(f"{tag}: duplicado")
    seen.add(key)

def affected(cve, v):
    return any(contains(r, v) for e in data if e["cve"] == cve for r in e.get("ranges", []))

vectors = [
    ("CVE-2021-44228", "2.3.0", True), ("CVE-2021-44228", "2.14.1", True),
    ("CVE-2021-44228", "2.15.0", False), ("CVE-2021-44228", "1.2.17", False),
    ("CVE-2014-0160", "1.0.1", True), ("CVE-2014-0160", "1.1.0", False), ("CVE-2014-0160", "1.0.1g", False),
    ("CVE-2015-6420", "3.2.1", True), ("CVE-2015-6420", "3.2.2", False),
    ("CVE-2015-6420", "4.0", True), ("CVE-2015-6420", "4.1", False),
    ("CVE-2022-25647", "2.8.5", True), ("CVE-2022-25647", "2.8.9", False),
    ("CVE-2020-8840", "2.9.10.2", True), ("CVE-2020-8840", "2.9.10.3", False),
]
for cve, v, exp in vectors:
    if affected(cve, v) != exp: errors.append(f"vetor falhou: {cve} {v} esperado={exp}")
print(f"{len(data)} entradas, {len(vectors)} vetores")
for e in errors: print("ERRO:", e)
sys.exit(1 if errors else 0)
