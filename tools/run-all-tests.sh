#!/usr/bin/env bash
# =============================================================================
# Capa de testing completa de BC4 (equivalente POSIX de run-all-tests.ps1).
#
# Encadena los gates de cada nivel y copia los reportes a docs/reportes/:
#   1. unitarias + cobertura   2. integracion Java   3. integracion Postman
#   4. contrato Pact (omitible con --skip-contracts)
#
# No se detiene en el primer fallo: ejecuta todos los niveles y resume al final.
#
# Uso:
#   ./tools/run-all-tests.sh
#   ./tools/run-all-tests.sh --skip-contracts
#
# Exit: 0 todo verde | 1 al menos un nivel fallo
# =============================================================================
set -uo pipefail

SKIP_CONTRACTS=0
for arg in "$@"; do
    [ "$arg" = "--skip-contracts" ] && SKIP_CONTRACTS=1
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="$REPO_ROOT/ms-production-packaging"
REPORTES="$REPO_ROOT/docs/reportes"
RESUMEN=""
FALLOS=0

nivel() {
    local nombre="$1"; shift
    printf '\n%s\n#  %s\n%s\n' "$(printf '#%.0s' $(seq 1 70))" "$nombre" "$(printf '#%.0s' $(seq 1 70))"
    local inicio=$SECONDS
    ( cd "$REPO_ROOT" && "$@" )
    local codigo=$?
    local estado="OK"
    if [ $codigo -ne 0 ]; then estado="FALLO (exit $codigo)"; FALLOS=1; fi
    RESUMEN+=$(printf '  %-40s %-18s %4ss' "$nombre" "$estado" "$((SECONDS - inicio))")$'\n'
}

copiar() {
    [ -d "$1" ] || return 0
    rm -rf "$2" && mkdir -p "$(dirname "$2")" && cp -r "$1" "$2"
}

nivel "1. Unitarias + cobertura" ./tools/validate-tests.sh
copiar "$MODULE_DIR/target/site/jacoco" "$REPORTES/coverage-unitarias"

nivel "2. Integracion Java (MockMvc + H2)" \
    bash -c "cd '$MODULE_DIR' && ./mvnw -B -Pintegration test-compile failsafe:integration-test failsafe:verify"
if [ -d "$MODULE_DIR/target/failsafe-reports" ]; then
    rm -rf "$REPORTES/integracion-java" && mkdir -p "$REPORTES/integracion-java"
    cp "$MODULE_DIR"/target/failsafe-reports/*integration* "$REPORTES/integracion-java/" 2>/dev/null
fi

nivel "3. Integracion Postman (Newman)" ./tools/validate-integration.sh --mode local --twice

if [ "$SKIP_CONTRACTS" = "0" ]; then
    nivel "4. Contrato (Pact)" ./tools/validate-contracts.sh --break-check
fi

printf '\n=== Resumen ===\n%s' "$RESUMEN"
echo "  Reportes en docs/reportes/ (coverage-unitarias, integracion-java)"
if [ $FALLOS -ne 0 ]; then echo; echo "FALLO: al menos un nivel no paso."; exit 1; fi
echo; echo "OK: la capa de testing completa esta en verde."
