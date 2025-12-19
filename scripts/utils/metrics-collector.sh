#!/bin/bash
# =============================================================================
# TICKETERO - Metrics Collector
# =============================================================================
# Recolecta métricas del sistema durante pruebas de performance
# Usage: ./scripts/utils/metrics-collector.sh [duration_seconds] [output_file]
# =============================================================================

DURATION=${1:-60}
OUTPUT_FILE=${2:-"metrics-$(date +%Y%m%d-%H%M%S).csv"}

echo "timestamp,cpu_app,mem_app_mb,cpu_postgres,mem_postgres_mb,tickets_waiting,tickets_completed,outbox_pending,outbox_failed" > "$OUTPUT_FILE"

echo "📊 Collecting metrics for ${DURATION} seconds..."
echo "📁 Output: ${OUTPUT_FILE}"

START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION))

while [ $(date +%s) -lt $END_TIME ]; do
    TIMESTAMP=$(date +%Y-%m-%d\ %H:%M:%S)
    
    # Container stats (simplified for Windows compatibility)
    APP_CPU=0
    APP_MEM=0
    PG_CPU=0
    PG_MEM=0
    
    # Try to get Docker stats if available
    if command -v docker &> /dev/null; then
        APP_STATS=$(docker stats ticketero-api --no-stream --format "{{.CPUPerc}},{{.MemUsage}}" 2>/dev/null | head -1)
        if [ ! -z "$APP_STATS" ]; then
            APP_CPU=$(echo "$APP_STATS" | cut -d',' -f1 | tr -d '%')
            APP_MEM=$(echo "$APP_STATS" | cut -d',' -f2 | cut -d'/' -f1 | tr -d 'MiB ')
        fi
        
        PG_STATS=$(docker stats ticketero-db --no-stream --format "{{.CPUPerc}},{{.MemUsage}}" 2>/dev/null | head -1)
        if [ ! -z "$PG_STATS" ]; then
            PG_CPU=$(echo "$PG_STATS" | cut -d',' -f1 | tr -d '%')
            PG_MEM=$(echo "$PG_STATS" | cut -d',' -f2 | cut -d'/' -f1 | tr -d 'MiB ')
        fi
    fi
    
    # Database metrics
    DB_CONNECTIONS=0
    TICKETS_WAITING=0
    TICKETS_COMPLETED=0
    OUTBOX_PENDING=0
    OUTBOX_FAILED=0
    
    if command -v docker &> /dev/null; then
        TICKETS_WAITING=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
            "SELECT COUNT(*) FROM ticket WHERE status='WAITING';" 2>/dev/null | xargs || echo "0")
        TICKETS_COMPLETED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
            "SELECT COUNT(*) FROM ticket WHERE status='COMPLETED';" 2>/dev/null | xargs || echo "0")
        OUTBOX_PENDING=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
            "SELECT COUNT(*) FROM outbox_message WHERE status='PENDING';" 2>/dev/null | xargs || echo "0")
        OUTBOX_FAILED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
            "SELECT COUNT(*) FROM outbox_message WHERE status='FAILED';" 2>/dev/null | xargs || echo "0")
    fi
    
    # Write to CSV
    echo "${TIMESTAMP},${APP_CPU:-0},${APP_MEM:-0},${PG_CPU:-0},${PG_MEM:-0},${TICKETS_WAITING:-0},${TICKETS_COMPLETED:-0},${OUTBOX_PENDING:-0},${OUTBOX_FAILED:-0}" >> "$OUTPUT_FILE"
    
    sleep 5
done

echo "✅ Metrics collection complete: ${OUTPUT_FILE}"