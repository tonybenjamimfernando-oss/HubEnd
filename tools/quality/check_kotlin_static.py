#!/usr/bin/env python3
"""Verificações estáticas leves do código Kotlin (sem compilador).

Detecta: imports internos sem declaração correspondente, declarações de topo
duplicadas no mesmo pacote e ficheiros de teste sem @Test. (Delimitadores não
são verificados: templates de string Kotlin geram falsos positivos.) NÃO substitui a compilação Gradle; serve como rede de segurança
rápida em ambientes sem Android SDK.
"""
import re, sys, pathlib

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "android/hubops/src")
INTERNAL = ("dev.hubops", "com.hubops")
DECL = re.compile(r'^\s*(?:(?:public|internal|private|data|sealed|enum|abstract|open|annotation|value|inline|fun|const|suspend|operator|tailrec)\s+)*'
                  r'(class|interface|object|typealias|fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?,\s]*\.)?(\w+)', re.M)

def strip(src: str) -> str:
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    src = re.sub(r'//[^\n]*', '', src)
    src = re.sub(r"'(?:\\.|[^'\\])'", "''", src)
    src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)
    return src

decls, pkgs, files = {}, {}, []
for f in ROOT.rglob("*.kt"):
    raw = f.read_text(encoding="utf-8")
    code = strip(raw)
    m = re.search(r'^\s*package\s+([\w.]+)', code, re.M)
    pkg = m.group(1) if m else ""
    files.append((f, raw, code, pkg))
    for kind, name in DECL.findall(code):
        # só topo: heurística por indentação zero
        pass
    for line in code.splitlines():
        mm = DECL.match(line)
        if mm and not line.startswith((" ", "\t")):
            decls.setdefault(pkg, {}).setdefault(mm.group(2), []).append(f)

problems = []
for f, raw, code, pkg in files:
    for imp in re.findall(r'^\s*import\s+([\w.]+)(?:\s+as\s+\w+)?\s*$', code, re.M):
        if not imp.startswith(INTERNAL) or imp.endswith((".BuildConfig", ".R")):
            continue  # BuildConfig/R são gerados pelo AGP
        parts = imp.split(".")
        ok = False
        for i in range(len(parts) - 1, 0, -1):
            p, n = ".".join(parts[:i]), parts[i]
            if n in decls.get(p, {}):
                ok = True
                break
        if not ok and imp.endswith(".*"):
            ok = True
        if not ok:
            problems.append(f"{f}: import interno sem declaração: {imp}")
    if "/test/" in str(f) and "@Test" not in raw:
        problems.append(f"{f}: ficheiro de teste sem @Test")

for pkg, names in decls.items():
    for n, fs in names.items():
        uniq = sorted(set(map(str, fs)))
        if len(uniq) > 1 and not n.startswith("main"):
            kinds_fun = all(re.search(rf'\bfun\s+(?:[\w.<>?]+\.)?{n}\b', strip(pathlib.Path(x).read_text())) for x in uniq)
            if not kinds_fun:  # overloads de funções são legítimos
                problems.append(f"declaração duplicada {pkg}.{n}: {', '.join(uniq)}")

print(f"{len(files)} ficheiros Kotlin analisados")
for p in problems:
    print("PROBLEMA:", p)
sys.exit(1 if problems else 0)
