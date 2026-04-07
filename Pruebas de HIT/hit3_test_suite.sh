#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SCRIPT_NAME="$(basename "$0")"

BASE_URL="http://localhost:8090"
IMAGE="ulisescasal/task-service:1.0.0"
COMPOSE_FILE="$ROOT_DIR/docker-compose.hit3.yml"
REQUEST_TIMEOUT=60

DO_UP="false"
DO_DOWN="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --image)
      IMAGE="$2"
      shift 2
      ;;
    --up)
      DO_UP="true"
      shift
      ;;
    --down)
      DO_DOWN="true"
      shift
      ;;
    --timeout)
      REQUEST_TIMEOUT="$2"
      shift 2
      ;;
    *)
      echo "Opción inválida: $1"
      echo "Uso: bash \"$SCRIPT_NAME\" [--base-url URL] [--image IMAGE] [--up] [--down] [--timeout SEGUNDOS]"
      exit 1
      ;;
  esac
done

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Falta dependencia: $1"
    exit 1
  }
}

need_cmd curl
need_cmd docker
need_cmd python3

json_field() {
  local json="$1"
  local field="$2"
  python3 -c "import json,sys; d=json.loads(sys.argv[1]); print(d.get(sys.argv[2], ''))" "$json" "$field"
}

now_ms() {
  python3 -c 'import time; print(int(time.time()*1000))'
}

request_task() {
  local trace_id="$1"
  curl -sS --max-time "$REQUEST_TIMEOUT" -X POST "$BASE_URL/api/hit3/getRemoteTask" \
    -H "Content-Type: application/json" \
    -d "{\"calculo\":\"sumar\",\"parametros\":{\"a\":10,\"b\":20},\"datosAdicionales\":{\"traceId\":\"$trace_id\"},\"imagenDocker\":\"$IMAGE\"}"
}

request_task_retry() {
  local trace_id="$1"
  local attempts=3
  local wait_seconds=2
  local output=""
  for n in $(seq 1 "$attempts"); do
    if output="$(request_task "$trace_id" 2>/dev/null)"; then
      echo "$output"
      return 0
    fi
    sleep "$wait_seconds"
  done
  return 1
}

get_status() {
  curl -sS --max-time 5 "$BASE_URL/internal/hit3/cluster/status"
}

print_title() {
  echo
  echo "=============================================================="
  echo "$1"
  echo "=============================================================="
}

if [[ "$DO_UP" == "true" ]]; then
  print_title "Levantando cluster HIT3"
  docker compose -f "$COMPOSE_FILE" up -d
fi

print_title "Caso 1 - Estado del cluster disponible"
STATUS_JSON="$(get_status)"
echo "$STATUS_JSON"
SELF_NODE_ID="$(json_field "$STATUS_JSON" "selfNodeId")"
LEADER_ID="$(json_field "$STATUS_JSON" "leaderId")"
if [[ -z "$SELF_NODE_ID" ]]; then
  echo "Falló: no se pudo leer selfNodeId"
  exit 1
fi
echo "OK: selfNodeId=$SELF_NODE_ID, leaderId=$LEADER_ID"

if [[ "$LEADER_ID" == "-1" || -z "$LEADER_ID" ]]; then
  print_title "Esperando elección inicial de líder"
  for _ in {1..30}; do
    sleep 1
    STATUS_JSON="$(get_status)"
    LEADER_ID="$(json_field "$STATUS_JSON" "leaderId")"
    if [[ "$LEADER_ID" != "-1" && -n "$LEADER_ID" ]]; then
      break
    fi
  done
fi

if [[ "$LEADER_ID" == "-1" || -z "$LEADER_ID" ]]; then
  echo "Falló: no hay líder elegido"
  exit 1
fi
echo "OK: líder actual=$LEADER_ID"

print_title "Caso 2 - Ejecución de tarea simple"
SIMPLE_RESPONSE="$(request_task_retry "hit3-simple-001")"
echo "$SIMPLE_RESPONSE"
SIMPLE_STATUS="$(json_field "$SIMPLE_RESPONSE" "status")"
if [[ "$SIMPLE_STATUS" != "OK" ]]; then
  echo "Falló: la tarea simple no devolvió status=OK"
  exit 1
fi
echo "OK: tarea simple ejecutada"

print_title "Caso 3 - Carga concurrente (10 tareas)"
TMP_RESULTS="$(mktemp)"
for i in {1..10}; do
  (
    request_task_retry "hit3-concurrent-$i" >"$TMP_RESULTS.$i" 2>/dev/null || true
  ) &
done
wait

OK_COUNT=0
for i in {1..10}; do
  if [[ -f "$TMP_RESULTS.$i" ]]; then
    RESP="$(cat "$TMP_RESULTS.$i")"
    ST="$(json_field "$RESP" "status" || true)"
    if [[ "$ST" == "OK" ]]; then
      OK_COUNT=$((OK_COUNT + 1))
    fi
  fi
done
echo "Respuestas OK en concurrente: $OK_COUNT/10"
if (( OK_COUNT < 7 )); then
  echo "Falló: demasiadas tareas concurrentes fallidas"
  exit 1
fi

print_title "Caso 4 - Failover: caída del líder y nueva elección"
OLD_LEADER="$LEADER_ID"
LEADER_CONTAINER="orchestrator-$OLD_LEADER"
echo "Matando líder actual: $LEADER_CONTAINER"
docker kill "$LEADER_CONTAINER" >/dev/null

START_MS="$(now_ms)"
NEW_LEADER="-1"
for _ in {1..60}; do
  sleep 0.5
  STATUS_JSON="$(get_status || true)"
  if [[ -n "$STATUS_JSON" ]]; then
    CANDIDATE="$(json_field "$STATUS_JSON" "leaderId" || true)"
    if [[ -n "$CANDIDATE" && "$CANDIDATE" != "-1" && "$CANDIDATE" != "$OLD_LEADER" ]]; then
      NEW_LEADER="$CANDIDATE"
      break
    fi
  fi
done
END_MS="$(now_ms)"

if [[ "$NEW_LEADER" == "-1" ]]; then
  echo "Falló: no se eligió nuevo líder"
  exit 1
fi

RECOVERY_MS=$((END_MS - START_MS))
echo "OK: nuevo líder=$NEW_LEADER"
echo "Recovery time aproximado=${RECOVERY_MS}ms"

print_title "Caso 5 - Redistribución tras failover"
POST_FAILOVER_RESPONSE="$(request_task_retry "hit3-post-failover-001")"
echo "$POST_FAILOVER_RESPONSE"
POST_FAILOVER_STATUS="$(json_field "$POST_FAILOVER_RESPONSE" "status")"
if [[ "$POST_FAILOVER_STATUS" != "OK" ]]; then
  echo "Falló: no se pudo ejecutar tarea después del failover"
  exit 1
fi
echo "OK: tareas siguen funcionando con nuevo líder"

print_title "Caso 6 - Levantar nodo caído nuevamente"
docker start "$LEADER_CONTAINER" >/dev/null
sleep 3
STATUS_JSON="$(get_status)"
echo "$STATUS_JSON"
echo "OK: nodo original levantado"

print_title "Resumen HIT3"
echo "Líder inicial: $OLD_LEADER"
echo "Líder posterior: $NEW_LEADER"
echo "Recovery time: ${RECOVERY_MS}ms"
echo "Estado final: PASS"

rm -f "$TMP_RESULTS" "$TMP_RESULTS".*

if [[ "$DO_DOWN" == "true" ]]; then
  print_title "Apagando cluster HIT3"
  docker compose -f "$COMPOSE_FILE" down
fi
