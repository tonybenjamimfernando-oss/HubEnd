# Validação 1.29.0 — estado efetivamente observado

## Executado nesta sessão
| Verificação | Resultado | Âmbito |
|---|---|---|
| `tools/quality/check_kotlin_static.py app/src` | PASS | 108 ficheiros Kotlin: todos os imports internos resolvem; sem declarações de topo duplicadas |
| `tools/quality/validate_cve_db.py` | PASS | 11 entradas válidas; 15 vetores de versão (port Python do algoritmo Kotlin) |
| Regex dos novos padrões de segredos | PASS | Amostras sintéticas, em Python (sintaxe compatível com java.util.regex) |

## Não executado / não aprovado
- Compilação Kotlin (não existe kotlinc/Gradle/Android SDK neste ambiente): `CveCatalog.kt`, o novo `DependencyAuditor.kt` e `SecretScanner.kt` foram escritos com cuidado mas **não compilados**.
- `CveCatalogTest` (9 testes JUnit) **não foi executado**; o algoritmo foi verificado só pelo port Python.
- Gradle/lint/APK, testes em dispositivo, NDK/llama.cpp/PRoot, treino SNSE, Docker GoPA (pendências de 1.28.0 mantidas).
- Dados CVE escritos de memória do autor (sem rede): confirmar cada entrada no NVD antes de depender delas.

## Limites
- Base local de 11 CVEs não é exaustiva; ausência de achados não prova ausência de vulnerabilidades.
- Bibliotecas nativas raramente expõem versão no caminho; sem `pom.properties` o resultado é INFO.
