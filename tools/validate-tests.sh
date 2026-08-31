#!/usr/bin/env bash
# =============================================================================
# Gate de validacion de pruebas unitarias (equivalente POSIX de validate-tests.ps1).
#
# Se usa en el CI (Linux) y desde Git Bash. Ejecuta la misma comprobacion que el
# script de PowerShell:
#   1. verifica JDK 21   2. suite unitaria   3. cobertura + umbral   4. PIT (opcional)
#
# Uso:
#   ./tools/validate-tests.sh                       # modulo por defecto
#   ./tools/validate-tests.sh ms-production-packaging --mutation
#
# Exit: 0 OK | 1 tests/build | 2 cobertura bajo umbral | 3 entorno | 4 mutantes
# =============================================================================
set -uo pipefail

MODULE="${1:-ms-production-packaging}"
MUTATION="no"
for arg in "$@"; do
    if [ "$arg" = "--mutation" ]; then MUTATION="yes"; fi
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="$REPO_ROOT/$MODULE"

section() { printf '\n=== %s %s\n' "$1" "$(printf '=%.0s' $(seq 1 $((60 - ${#1}))))"; }

# -- 1. Entorno ---------------------------------------------------------------
section "1/4  Entorno"

if [ ! -d "$MODULE_DIR" ]; then
    echo "ERROR: no existe el modulo '$MODULE' en $REPO_ROOT" >&2
    exit 3
fi

# Respeta JAVA_HOME si ya es un JDK 21; si no, busca uno.
java_es_21() { [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | head -1 | grep -q '"21'; }

if [ -z "${JAVA_HOME:-}" ] || ! java_es_21 "$JAVA_HOME"; then
    for c in "${JAVA_HOME_21_X64:-}" \
             "/c/Program Files/Java/jdk-21" \
             "/usr/lib/jvm/temurin-21-jdk-amd64" \
             "/usr/lib/jvm/java-21-openjdk-amd64"; do
        if [ -n "$c" ] && java_es_21 "$c"; then export JAVA_HOME="$c"; break; fi
    done
fi

if [ -z "${JAVA_HOME:-}" ] || ! java_es_21 "$JAVA_HOME"; then
    echo "ERROR: no se encontro un JDK 21 (el build lo exige)." >&2
    echo "       Exporta JAVA_HOME apuntando a un JDK 21 y reintenta." >&2
    exit 3
fi

echo "  Modulo    : $MODULE"
echo "  JAVA_HOME : $JAVA_HOME"
echo "  Java      : $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"

cd "$MODULE_DIR" || exit 3

# -- 2. Suite + umbral --------------------------------------------------------
section "2/4  Tests unitarios + cobertura"

LOG="$(./mvnw -B verify 2>&1)"
MVN_EXIT=$?

echo "$LOG" | grep -E "Tests run:.*Failures" | tail -1 | sed 's/^/  /'

if [ $MVN_EXIT -ne 0 ]; then
    if echo "$LOG" | grep -qE "Rule violated|coveredRatio is"; then
        echo ""
        echo "FALLO: cobertura por debajo del umbral configurado en pom.xml" >&2
        echo "$LOG" | grep -E "Rule violated|coveredRatio is" | sed 's/^/  /' >&2
        echo "  -> Escribe los tests que faltan. Bajar el umbral no es una opcion." >&2
        exit 2
    fi
    echo ""
    echo "FALLO: la suite no paso. Ultimas lineas del log:" >&2
    echo "$LOG" | tail -25 | sed 's/^/  /' >&2
    exit 1
fi

# -- 3. Resumen de cobertura --------------------------------------------------
section "3/4  Cobertura (JaCoCo)"

CSV="target/site/jacoco/jacoco.csv"
if [ -f "$CSV" ]; then
    awk -F, 'NR>1 {lm+=$8; lc+=$9; bm+=$6; bc+=$7}
             END {printf "  Lineas : %.1f %% (%d / %d)\n  Ramas  : %.1f %% (%d / %d)\n",
                        100*lc/(lm+lc), lc, lm+lc, 100*bc/(bm+bc), bc, bm+bc}' "$CSV"
    echo "  Reporte: target/site/jacoco/index.html"
else
    echo "  AVISO: no se encontro $CSV"
fi

# -- 4. Mutation testing ------------------------------------------------------
section "4/4  Mutation testing (PIT)"

if [ "$MUTATION" != "yes" ]; then
    echo "  Omitido. Ejecuta con --mutation para validar la CALIDAD de los tests."
else
    echo "  Ejecutando PIT (tarda varios minutos)..."
    PIT_LOG="$(./mvnw -B -Djacoco.skip=true test-compile org.pitest:pitest-maven:mutationCoverage 2>&1)"
    PIT_EXIT=$?
    echo "$PIT_LOG" | grep -E "Generated [0-9]+ mutations|mutations with|Mutation Coverage" | sed 's/^/  /'
    if [ $PIT_EXIT -ne 0 ]; then
        echo ""
        echo "FALLO: demasiados mutantes sobrevivieron (umbral en pom.xml)." >&2
        echo "  Un mutante superviviente = codigo ejecutado por los tests pero NO comprobado." >&2
        echo "  Detalle: target/pit-reports/index.html" >&2
        exit 4
    fi
    echo "  Reporte: target/pit-reports/index.html"
fi

echo ""
echo "OK: validacion superada."
exit 0
