#!/usr/bin/env bash
# =============================================================================
# Gate de validacion de las PRUEBAS DE INTEGRACION (version POSIX).
#
# Misma comprobacion que tools/validate-integration.ps1, para Git Bash y para el
# CI: levanta el ambiente, espera /actuator/health, ejecuta la coleccion de
# Postman con Newman y devuelve un exit code inequivoco.
#
# Uso:
#   ./tools/validate-integration.sh                 # modo docker (por defecto)
#   ./tools/validate-integration.sh --mode local    # jar + perfil 'it' (H2)
#   ./tools/validate-integration.sh --mode none     # la app ya esta levantada
#   ./tools/validate-integration.sh --keep-up       # no baja el ambiente al final
#   ./tools/validate-integration.sh --twice         # exige que pase DOS veces seguidas
#                                                   # contra la misma base de datos
#                                                   # (comprobacion de repetibilidad)
#
# En modo 'local' se respetan SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD si
# estan definidas: asi el CI arranca el mismo jar contra su PostgreSQL de servicio.
#
# Exit codes:
#   0 = OK | 1 = aserciones fallidas | 2 = el ambiente no levanto
#   3 = falta un prerrequisito | 4 = timeout esperando /actuator/health
# =============================================================================
set -uo pipefail

MODE="docker"
KEEP_UP=0
TWICE=0
BASE_URL="${BASE_URL:-http://localhost:8082}"
MODULE="ms-production-packaging"

while [ $# -gt 0 ]; do
    case "$1" in
        --mode)     MODE="$2"; shift 2 ;;
        --keep-up)  KEEP_UP=1; shift ;;
        --twice)    TWICE=1; shift ;;
        --base-url) BASE_URL="$2"; shift 2 ;;
        --module)   MODULE="$2"; shift 2 ;;
        *) echo "Argumento desconocido: $1"; exit 3 ;;
    esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="$REPO_ROOT/$MODULE"
COMPOSE_FILE="$REPO_ROOT/docker-compose.integration.yml"
COLECCION="$REPO_ROOT/postman/bc4-integracion.postman_collection.json"
ENTORNO="$REPO_ROOT/postman/local.postman_environment.json"
REPORTE_DIR="$MODULE_DIR/target/newman"
REPORTE_JSON="$REPORTE_DIR/report.json"
APP_PID=""

seccion() { echo; echo "=== $1 ============================================"; }

bajar_ambiente() {
    if [ "$KEEP_UP" = "1" ]; then
        echo "  --keep-up: el ambiente se queda levantado en $BASE_URL"
        return
    fi
    if [ "$MODE" = "docker" ]; then
        echo "  Bajando el ambiente Docker..."
        docker compose -f "$COMPOSE_FILE" down -v >/dev/null 2>&1
    elif [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        echo "  Parando la aplicacion (PID $APP_PID)..."
        kill "$APP_PID" 2>/dev/null
    fi
}

# ---------------------------------------------------------------------------
# 1. Prerrequisitos
# ---------------------------------------------------------------------------
seccion "1/5  Prerrequisitos"

[ -f "$COLECCION" ] || { echo "ERROR: no se encontro la coleccion en $COLECCION"; exit 3; }

NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmjs.org}"
if command -v newman >/dev/null 2>&1; then
    NEWMAN="newman"
elif command -v npx >/dev/null 2>&1; then
    # Si el npm local apunta a un registro privado con el token caducado, npx falla
    # con E401 por un motivo ajeno a las pruebas. Se fuerza el registro publico.
    NEWMAN="npx --yes --registry=$NPM_REGISTRY newman"
else
    echo "ERROR: no hay forma de ejecutar Newman."
    echo "       Instala Node.js (trae npx) o ejecuta: npm install -g newman"
    exit 3
fi

if [ "$MODE" = "docker" ] && ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: Docker no esta disponible. Usa el modo sin Docker: --mode local"
    exit 3
fi

echo "  Modo      : $MODE"
echo "  Base URL  : $BASE_URL"
echo "  Newman    : $NEWMAN"

# ---------------------------------------------------------------------------
# 2. Levantar el ambiente
# ---------------------------------------------------------------------------
seccion "2/5  Ambiente de pruebas"

mkdir -p "$REPORTE_DIR"

case "$MODE" in
    docker)
        echo "  docker compose up --build (la primera vez compila la imagen: tarda)..."
        if ! docker compose -f "$COMPOSE_FILE" up -d --build; then
            echo "FALLO: no se pudo levantar el ambiente Docker."
            exit 2
        fi
        ;;
    local)
        cd "$MODULE_DIR" || { echo "ERROR: no existe el modulo '$MODULE'"; exit 3; }
        echo "  Compilando el jar (sin tests)..."
        if ! ./mvnw -B -DskipTests package >"$REPORTE_DIR/build.log" 2>&1; then
            echo "FALLO: el empaquetado fallo. Ultimas lineas:"
            tail -20 "$REPORTE_DIR/build.log"
            exit 2
        fi
        JAR="$(ls target/*.jar 2>/dev/null | grep -v sources | head -1)"
        [ -n "$JAR" ] || { echo "FALLO: no se encontro el jar en target/"; exit 2; }
        echo "  Arrancando $(basename "$JAR") con el perfil 'it'..."
        java -jar "$JAR" --spring.profiles.active=it >"$REPORTE_DIR/app-it.log" 2>&1 &
        APP_PID=$!
        echo "  PID $APP_PID - log en $MODULE/target/newman/app-it.log"
        ;;
    none)
        echo "  Modo 'none': se asume que la aplicacion ya esta levantada."
        ;;
    *)
        echo "ERROR: modo desconocido '$MODE' (docker | local | none)"; exit 3 ;;
esac

# ---------------------------------------------------------------------------
# 3. Esperar a que la aplicacion responda
# ---------------------------------------------------------------------------
seccion "3/5  Esperando /actuator/health"

ARRIBA=0
for i in $(seq 1 60); do
    if curl -fsS --max-time 3 "$BASE_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
        ARRIBA=1
        echo "  UP tras $((i * 2)) s aprox."
        break
    fi
    if [ "$MODE" = "local" ] && [ -n "$APP_PID" ] && ! kill -0 "$APP_PID" 2>/dev/null; then
        echo "FALLO: la aplicacion termino durante el arranque. Log:"
        tail -25 "$REPORTE_DIR/app-it.log"
        exit 2
    fi
    sleep 2
done

if [ "$ARRIBA" != "1" ]; then
    echo "FALLO: $BASE_URL/actuator/health no respondio UP en 2 minutos."
    [ "$MODE" = "docker" ] && docker compose -f "$COMPOSE_FILE" logs --tail 40 app-it
    bajar_ambiente
    exit 4
fi

# ---------------------------------------------------------------------------
# 4. Ejecutar la coleccion
# ---------------------------------------------------------------------------
seccion "4/5  Coleccion Postman (Newman)"

VUELTAS=1
[ "$TWICE" = "1" ] && VUELTAS=2
NEWMAN_EXIT=0
FALLIDAS=0
FALLO_VUELTA=0

for v in $(seq 1 $VUELTAS); do
    if [ "$TWICE" = "1" ]; then
        echo
        echo "  --- Vuelta $v de $VUELTAS (misma base de datos) ---"
        REPORTE_VUELTA="$REPORTE_DIR/report-$v.json"
    else
        REPORTE_VUELTA="$REPORTE_JSON"
    fi

    $NEWMAN run "$COLECCION" \
        -e "$ENTORNO" \
        --env-var "baseUrl=$BASE_URL" \
        --reporters cli,json \
        --reporter-json-export "$REPORTE_VUELTA" \
        --color off
    [ $? -ne 0 ] && NEWMAN_EXIT=1

    if [ -f "$REPORTE_VUELTA" ] && command -v node >/dev/null 2>&1; then
        node -e "
const r = require('$REPORTE_VUELTA');
const s = r.run.stats;
const p = '$TWICE' === '1' ? 'vuelta $v - ' : '';
console.log('  ' + p + 'Requests   : ' + s.requests.total + ' ejecutados, ' + s.requests.failed + ' fallidos');
console.log('  ' + p + 'Aserciones : ' + s.assertions.total + ' ejecutadas, ' + s.assertions.failed + ' fallidas');
(r.run.failures || []).forEach(f => {
    console.log('    - [' + (f.source && f.source.name) + '] ' + f.error.test);
    console.log('      ' + f.error.message);
});
process.exit(s.assertions.failed > 0 ? 1 : 0);
"
        if [ $? -ne 0 ]; then
            FALLIDAS=1
            FALLO_VUELTA=$v
        fi
    fi

    # Si la primera vuelta ya fallo, la segunda no aporta informacion nueva.
    if [ "$FALLIDAS" != "0" ] && [ "$v" = "1" ]; then
        break
    fi
done

# ---------------------------------------------------------------------------
# 5. Resumen
# ---------------------------------------------------------------------------
seccion "5/5  Resumen"

if [ "$FALLO_VUELTA" = "2" ]; then
    echo "  La primera vuelta paso y la segunda no: la coleccion NO es repetible."
    echo "  Busca datos fijos que chocan con los creados en la vuelta anterior."
fi
[ "$TWICE" = "1" ] && echo "  Repetibilidad: 2 vueltas contra la misma base de datos"
echo "  Reportes  : $MODULE/target/newman/"

bajar_ambiente

if [ "$NEWMAN_EXIT" != "0" ] || [ "$FALLIDAS" != "0" ]; then
    echo
    echo "FALLO: la validacion de integracion no paso."
    exit 1
fi

echo
echo "OK: validacion de integracion superada."
exit 0
