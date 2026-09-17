#!/usr/bin/env bash
# =============================================================================
# Gate de validacion de las PRUEBAS DE CONTRATO - Pact (version POSIX).
#
# Misma comprobacion que tools/validate-contracts.ps1, para Git Bash y para el CI:
#
#   1. CONSUMIDOR: las pruebas Pact de bc5-logistic-consumer generan el pacto
#   2. el pacto se publica en los recursos de prueba del proveedor
#   3. PROVEEDOR: ms-production-packaging reproduce cada interaccion contra si
#      mismo, levantado de verdad con el perfil 'it' (H2 en memoria)
#
# Uso:
#   ./tools/validate-contracts.sh                  # ciclo completo
#   ./tools/validate-contracts.sh --only consumer  # solo generar el pacto
#   ./tools/validate-contracts.sh --only provider  # solo verificarlo
#   ./tools/validate-contracts.sh --break-check    # exige que el pacto alterado FALLE
#
# --break-check es la comprobacion de falsabilidad: altera una expectativa del
# pacto (estado LISTO -> TRANSFERIDO) y exige que la verificacion se caiga. Si
# pasa igual, la verificacion no comprueba nada y el gate es decorativo.
#
# Exit codes:
#   0 = OK | 1 = interacciones no verificadas | 2 = el consumidor no genero el pacto
#   3 = entorno invalido | 4 = pacto ausente o vacio | 5 = gate falsificable
# =============================================================================
set -uo pipefail

ONLY="all"
BREAK_CHECK=0
MODULE="ms-production-packaging"
CONSUMER_MODULE="contract-testing/bc5-logistic-consumer"

while [ $# -gt 0 ]; do
    case "$1" in
        --only)        ONLY="$2"; shift 2 ;;
        --break-check) BREAK_CHECK=1; shift ;;
        --module)      MODULE="$2"; shift 2 ;;
        --consumer)    CONSUMER_MODULE="$2"; shift 2 ;;
        *) echo "Argumento desconocido: $1"; exit 3 ;;
    esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROVIDER_DIR="$REPO_ROOT/$MODULE"
CONSUMER_DIR="$REPO_ROOT/$CONSUMER_MODULE"
PACT_DIR="$PROVIDER_DIR/src/test/resources/pacts"
PACT_FILE="$PACT_DIR/ms-logistic-delivery-ms-production-packaging.json"

seccion() { echo; echo "=== $1 ============================================"; }

# El pacto y los reportes de Maven son JSON y XML: se leen con python, que es el
# unico prerrequisito extra de este gate ademas del JDK 21.
PY_BIN=""
for c in python3 python py; do
    if command -v "$c" >/dev/null 2>&1; then PY_BIN="$c"; break; fi
done
if [ -z "$PY_BIN" ]; then
    echo "ERROR: hace falta python (python3) para leer el pacto y los reportes."
    exit 3
fi

# Suma tests/fallos de los XML de surefire o failsafe: la salida de consola de
# Maven miente cuando el build muere antes de imprimir el resumen.
# Imprime "total fallos" en una linea.
resultado_tests() {
    local dir="$1"
    if [ ! -d "$dir" ]; then echo "0 0"; return; fi
    "$PY_BIN" - "$dir" <<'PY' 2>/dev/null || echo "0 0"
import glob, sys, xml.etree.ElementTree as ET
total = fallos = 0
for f in glob.glob(sys.argv[1] + "/TEST-*.xml"):
    r = ET.parse(f).getroot()
    total  += int(r.get("tests", 0))
    fallos += int(r.get("failures", 0)) + int(r.get("errors", 0))
print(total, fallos)
PY
}

verificar_proveedor() {
    ( cd "$PROVIDER_DIR" && ./mvnw -B -Pcontract test-compile failsafe:integration-test failsafe:verify )
}

# ---------------------------------------------------------------------------
# 1. Entorno
# ---------------------------------------------------------------------------
seccion "1/5  Entorno"

[ -d "$PROVIDER_DIR" ] || { echo "ERROR: no existe el modulo proveedor '$MODULE'"; exit 3; }
[ -d "$CONSUMER_DIR" ] || { echo "ERROR: no existe el modulo consumidor '$CONSUMER_MODULE'"; exit 3; }

JAVA_BIN="java"
[ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && JAVA_BIN="$JAVA_HOME/bin/java"
JAVA_VERSION="$("$JAVA_BIN" -version 2>&1 | head -1)"
case "$JAVA_VERSION" in
    *'"21'*) ;;
    *) echo "ERROR: el build exige JDK 21. Encontrado: $JAVA_VERSION"; exit 3 ;;
esac

echo "  Consumidor : $CONSUMER_MODULE  (ms-logistic-delivery / BC5)"
echo "  Proveedor  : $MODULE  (ms-production-packaging / BC4)"
echo "  Alcance    : $ONLY"
echo "  Java       : $JAVA_VERSION"

# ---------------------------------------------------------------------------
# 2. Consumidor
# ---------------------------------------------------------------------------
seccion "2/5  Consumidor (genera el pacto)"

CONSUMER_TOTAL=0
CONSUMER_FALLOS=0

if [ "$ONLY" = "provider" ]; then
    echo "  Omitido (--only provider): se verifica el pacto ya publicado."
else
    echo "  mvnw test - cada prueba ejecuta el cliente real contra el mock de Pact..."
    ( cd "$CONSUMER_DIR" && ./mvnw -B test )
    CONSUMER_EXIT=$?
    read -r CONSUMER_TOTAL CONSUMER_FALLOS <<< "$(resultado_tests "$CONSUMER_DIR/target/surefire-reports")"
    echo "  Tests: $CONSUMER_TOTAL ejecutados, $CONSUMER_FALLOS fallidos"

    if [ "$CONSUMER_EXIT" -ne 0 ] || [ "$CONSUMER_FALLOS" -gt 0 ]; then
        echo
        echo "FALLO: las pruebas del consumidor no pasaron; sin ellas no hay pacto."
        exit 2
    fi
fi

# ---------------------------------------------------------------------------
# 3. El pacto
# ---------------------------------------------------------------------------
seccion "3/5  Pacto publicado"

if [ ! -f "$PACT_FILE" ]; then
    echo "FALLO: no se genero el pacto en $PACT_FILE"
    echo "       Revisa la propiedad pact.rootDir del pom del consumidor."
    exit 4
fi

INTERACCIONES="$("$PY_BIN" - "$PACT_FILE" <<'PY' 2>/dev/null || echo 0
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
ints = d.get("interactions", [])
print(len(ints))
PY
)"

if [ "$INTERACCIONES" -eq 0 ]; then
    echo "FALLO: el pacto no contiene ninguna interaccion."
    exit 4
fi

"$PY_BIN" - "$PACT_FILE" <<'PY' 2>/dev/null
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
print("  %s -> %s   (%d interacciones)" % (d["consumer"]["name"], d["provider"]["name"], len(d["interactions"])))
print()
for i in d["interactions"]:
    req = i["request"]
    ruta = req["path"]
    q = req.get("query")
    if q:
        ruta += "?" + "&".join("%s=%s" % (k, ",".join(v)) for k, v in q.items())
    print("    [%s %s] %s -> %s" % (req["method"], ruta, i["description"], i["response"]["status"]))
    for s in i.get("providerStates", []):
        print("        given: %s" % s["name"])
PY

# ---------------------------------------------------------------------------
# 4. Proveedor
# ---------------------------------------------------------------------------
seccion "4/5  Proveedor (verifica el pacto)"

PROVIDER_TOTAL=0
PROVIDER_FALLOS=0

if [ "$ONLY" = "consumer" ]; then
    echo "  Omitido (--only consumer): el pacto queda publicado sin verificar."
else
    echo "  mvnw -Pcontract failsafe:integration-test - levanta BC4 y reproduce cada interaccion..."
    verificar_proveedor
    PROVIDER_EXIT=$?
    read -r PROVIDER_TOTAL PROVIDER_FALLOS <<< "$(resultado_tests "$PROVIDER_DIR/target/failsafe-reports")"
    echo "  Interacciones verificadas: $((PROVIDER_TOTAL - PROVIDER_FALLOS)) de $INTERACCIONES, $PROVIDER_FALLOS fallidas"

    if [ "$PROVIDER_TOTAL" -eq 0 ]; then
        echo
        echo "FALLO: el proveedor no ejecuto ninguna verificacion."
        echo "       Una verificacion que no corre es peor que una que falla: parece verde."
        exit 1
    fi

    if [ "$PROVIDER_EXIT" -ne 0 ] || [ "$PROVIDER_FALLOS" -gt 0 ]; then
        echo
        echo "FALLO: el proveedor no cumple el contrato que publico el consumidor."
        echo "       Reporte completo: $MODULE/target/failsafe-reports"
        echo "       El pacto manda: si el cambio del proveedor es deliberado, se negocia"
        echo "       con BC5 y se regenera el pacto; no se relaja la expectativa."
        exit 1
    fi

    # -------------------------------------------------------------------
    # 4b. Falsabilidad
    # -------------------------------------------------------------------
    if [ "$BREAK_CHECK" = "1" ]; then
        echo
        echo "  --- Comprobacion de falsabilidad (--break-check) ---"
        echo "  Se altera el pacto (estado LISTO -> TRANSFERIDO) y se exige que falle."

        cp "$PACT_FILE" "$PACT_FILE.bak"
        trap 'mv -f "$PACT_FILE.bak" "$PACT_FILE" 2>/dev/null || true' EXIT INT TERM

        TOCADAS="$("$PY_BIN" - "$PACT_FILE" <<'PY' 2>/dev/null || echo 0
import json, sys
ruta = sys.argv[1]
d = json.load(open(ruta, encoding="utf-8"))
tocadas = 0
for i in d["interactions"]:
    cuerpo = i.get("response", {}).get("body")
    if isinstance(cuerpo, dict) and "estado" in cuerpo:
        cuerpo["estado"] = "TRANSFERIDO"
        tocadas += 1
if tocadas:
    json.dump(d, open(ruta, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print(tocadas)
PY
)"

        if [ "$TOCADAS" -eq 0 ]; then
            echo "  AVISO: ninguna interaccion expone 'estado'; no se pudo falsear."
        else
            verificar_proveedor
            INTENTO_EXIT=$?
            read -r _ TRAS_FALLOS <<< "$(resultado_tests "$PROVIDER_DIR/target/failsafe-reports")"

            mv -f "$PACT_FILE.bak" "$PACT_FILE"
            trap - EXIT INT TERM
            echo "  Pacto original restaurado."

            if [ "$INTENTO_EXIT" -eq 0 ] && [ "$TRAS_FALLOS" -eq 0 ]; then
                echo
                echo "FALLO: el pacto alterado tambien paso la verificacion."
                echo "       La verificacion no comprueba el cuerpo de la respuesta: probablemente"
                echo "       todo el body usa matchers de tipo y no queda ningun valor exacto que"
                echo "       pueda romperse. Revisa los matchers del consumidor."
                exit 5
            fi
            echo "  OK: la verificacion fallo con el pacto alterado ($TRAS_FALLOS interacciones rotas)."

            echo "  Rehaciendo la verificacion con el pacto restaurado..."
            verificar_proveedor || { echo "FALLO: dejo de pasar tras restaurar el pacto."; exit 1; }
            read -r PROVIDER_TOTAL PROVIDER_FALLOS <<< "$(resultado_tests "$PROVIDER_DIR/target/failsafe-reports")"
        fi
    fi
fi

# ---------------------------------------------------------------------------
# 5. Resumen
# ---------------------------------------------------------------------------
seccion "5/5  Resumen"

[ "$ONLY" != "provider" ] && echo "  Consumidor : $CONSUMER_TOTAL pruebas Pact, $CONSUMER_FALLOS fallidas"
echo "  Pacto      : $INTERACCIONES interacciones"
[ "$ONLY" != "consumer" ] && echo "  Proveedor  : $PROVIDER_TOTAL interacciones verificadas, $PROVIDER_FALLOS fallidas"
[ "$BREAK_CHECK" = "1" ] && [ "$ONLY" != "consumer" ] && echo "  Falsabilidad: comprobada"

if [ "$ONLY" = "consumer" ]; then
    echo
    echo "OK: pacto generado. Falta verificarlo en el proveedor (--only provider)."
    exit 0
fi

echo
echo "OK: contrato verificado de punta a punta."
exit 0
