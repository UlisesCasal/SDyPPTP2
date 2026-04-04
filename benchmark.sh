#!/bin/bash
# ================================================================
# Benchmark HIT2 — Mide throughput variando workers (1, 2, 4, 8)
# Uso: ./benchmark.sh <total_tareas>
# Ejemplo: ./benchmark.sh 16
# ================================================================

TOTAL_TASKS=${1:-16}
SERVER_URL="http://localhost:8080/api/hit2/getRemoteTask"
RESULTS_FILE="benchmark_results.csv"

echo "workers,total_tasks,completed,failed,total_seconds,throughput_per_min" > $RESULTS_FILE

for WORKERS in 1 2 4 8; do
    echo ""
    echo "========================================="
    echo "Probando con $WORKERS workers..."
    echo "========================================="

    # Reiniciar el server con N workers (ajustar según cómo lo levantes)
    # Si usás Docker:
    docker rm -f orchestrator-server 2>/dev/null || true
    docker run -d \
        --name orchestrator-server \
        -p 8080:8080 \
        -e TASK_SERVICE_HOST=host.docker.internal \
        -e HIT2_WORKERS_MAX=$WORKERS \
        --add-host=host.docker.internal:host-gateway \
        -v /var/run/docker.sock:/var/run/docker.sock \
        ulisescasal/orchestrator-server:latest

    echo "Esperando que el servidor arranque..."
    sleep 10

    # Timestamp inicio
    START=$(date +%s)

    # Lanzar todas las tareas en paralelo con curl
    PIDS=""
    COMPLETED=0
    FAILED=0

    for i in $(seq 1 $TOTAL_TASKS); do
        (
            RESULT=$(curl -s -o /dev/null -w "%{http_code}" \
                -X POST $SERVER_URL \
                -H "Content-Type: application/json" \
                -d "{
                    \"calculo\":\"sumar\",
                    \"parametros\":{\"a\":$i,\"b\":$((i * 2))},
                    \"datosAdicionales\":{\"traceId\":\"bench-$i\"},
                    \"imagenDocker\":\"ulisescasal/task-service:1.0.0\",
                    \"lamportTimestamp\":$i
                }" --max-time 120)
            echo "$RESULT" > /tmp/bench_result_$i.txt
        ) &
        PIDS="$PIDS $!"
    done

    # Esperar que terminen todas
    for PID in $PIDS; do
        wait $PID
    done

    END=$(date +%s)
    ELAPSED=$((END - START))

    # Contar resultados
    for i in $(seq 1 $TOTAL_TASKS); do
        CODE=$(cat /tmp/bench_result_$i.txt 2>/dev/null)
        if [ "$CODE" = "200" ]; then
            COMPLETED=$((COMPLETED + 1))
        else
            FAILED=$((FAILED + 1))
        fi
        rm -f /tmp/bench_result_$i.txt
    done

    # Throughput = (completed / elapsed_seconds) * 60
    if [ $ELAPSED -gt 0 ]; then
        THROUGHPUT=$(echo "scale=2; ($COMPLETED / $ELAPSED) * 60" | bc)
    else
        THROUGHPUT="N/A"
    fi

    echo "Workers: $WORKERS | Completadas: $COMPLETED | Falladas: $FAILED | Tiempo: ${ELAPSED}s | Throughput: $THROUGHPUT tareas/min"
    echo "$WORKERS,$TOTAL_TASKS,$COMPLETED,$FAILED,$ELAPSED,$THROUGHPUT" >> $RESULTS_FILE

    # Cleanup
    docker rm -f orchestrator-server 2>/dev/null || true
done

echo ""
echo "Resultados guardados en $RESULTS_FILE"
cat $RESULTS_FILE
