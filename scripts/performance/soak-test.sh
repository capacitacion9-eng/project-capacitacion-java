#!/bin/bash
# =============================================================================
# TICKETERO - Soak Test (30 minutos)
# =============================================================================
# Carga constante de 30 tickets/minuto durante 30 minutos
# Detecta memory leaks y degradación progresiva
# Usage: ./scripts/performance/soak-test.sh [duration_minutes]
# =============================================================================

DURATION_MIN=${1:-30}
TICKETS_PER_MIN=30

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║        TICKETERO - SOAK TEST (PERF-03)                       ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "  Duración: ${DURATION_MIN} minutos"
echo "  Carga: ${TICKETS_PER_MIN} tickets/minuto"
echo ""

# Start metrics with longer duration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
METRICS_FILE="$PROJECT_ROOT/results/soak-test-metrics-$(date +%Y%m%d-%H%M%S).csv"
mkdir -p "$PROJECT_ROOT/results"

DURATION_SEC=$((DURATION_MIN * 60 + 120))
if [ -f "$SCRIPT_DIR/../utils/metrics-collector.sh" ]; then
    bash "$SCRIPT_DIR/../utils/metrics-collector.sh" $DURATION_SEC "$METRICS_FILE" &
    METRICS_PID=$!
fi

# Capture initial memory
INITIAL_MEM=0
if command -v docker &> /dev/null; then
    INITIAL_MEM=$(docker stats ticketero-api --no-stream --format "{{.MemUsage}}" 2>/dev/null | cut -d'/' -f1 | tr -d 'MiB ' || echo "0")
fi

echo "  Memoria inicial: ${INITIAL_MEM}MB"
echo ""

# Execute soak test
START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION_MIN * 60))
TICKET_COUNTER=0
INTERVAL=$(echo "scale=2; 60 / $TICKETS_PER_MIN" | bc 2>/dev/null || echo "2")

while [ $(date +%s) -lt $END_TIME ]; do
    TICKET_COUNTER=$((TICKET_COUNTER + 1))
    QUEUE_INDEX=$((TICKET_COUNTER % 4))
    QUEUES=("CAJA" "PERSONAL" "EMPRESAS" "GERENCIA")
    QUEUE=${QUEUES[$QUEUE_INDEX]}
    
    curl -s -X POST "http://localhost:8080/api/tickets" \
        -H "Content-Type: application/json" \
        -d "{
            \"nationalId\": \"500$(printf '%06d' $TICKET_COUNTER)\",
            \"telefono\": \"+56912345678\",
            \"branchOffice\": \"Sucursal Test\",
            \"queueType\": \"${QUEUE}\"
        }" > /dev/null 2>&1 &
    
    ELAPSED=$(( ($(date +%s) - START_TIME) / 60 ))
    CURRENT_MEM=0
    if command -v docker &> /dev/null; then
        CURRENT_MEM=$(docker stats ticketero-api --no-stream --format "{{.MemUsage}}" 2>/dev/null | cut -d'/' -f1 | tr -d 'MiB ' || echo "0")
    fi
    
    echo -ne "\r  Minuto ${ELAPSED}/${DURATION_MIN} | Tickets: ${TICKET_COUNTER} | Memoria: ${CURRENT_MEM}MB    "
    
    sleep $INTERVAL
done

wait

# Stop metrics
if [ ! -z "$METRICS_PID" ]; then
    kill $METRICS_PID 2>/dev/null || true
fi

# Final memory
FINAL_MEM=0
if command -v docker &> /dev/null; then
    FINAL_MEM=$(docker stats ticketero-api --no-stream --format "{{.MemUsage}}" 2>/dev/null | cut -d'/' -f1 | tr -d 'MiB ' || echo "0")
fi

echo ""
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  RESULTADOS SOAK TEST${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "  Duración:          ${DURATION_MIN} minutos"
echo "  Tickets creados:   ${TICKET_COUNTER}"
echo "  Memoria inicial:   ${INITIAL_MEM}MB"
echo "  Memoria final:     ${FINAL_MEM}MB"

# Check for memory leak
MEM_LEAK=false
if [ "$INITIAL_MEM" -gt 0 ] && [ "$FINAL_MEM" -gt 0 ]; then
    MEM_DIFF=$(echo "$FINAL_MEM - $INITIAL_MEM" | bc 2>/dev/null || echo "0")
    MEM_INCREASE_PCT=$(echo "scale=1; $MEM_DIFF * 100 / $INITIAL_MEM" | bc 2>/dev/null || echo "0")
    
    if command -v bc &> /dev/null && (( $(echo "$MEM_INCREASE_PCT < 20" | bc -l 2>/dev/null || echo "1") )); then
        echo -e "  Memory leak:       ${GREEN}NO DETECTADO${NC} (+${MEM_INCREASE_PCT}%)"
    else
        echo -e "  Memory leak:       ${RED}POSIBLE${NC} (+${MEM_INCREASE_PCT}%)"
        MEM_LEAK=true
    fi
fi

echo ""
if [ -f "$SCRIPT_DIR/../utils/validate-consistency.sh" ]; then
    bash "$SCRIPT_DIR/../utils/validate-consistency.sh"
    CONSISTENCY_RESULT=$?
fi

if [ "$MEM_LEAK" = false ] && [ "${CONSISTENCY_RESULT:-1}" -eq 0 ]; then
    echo -e "${GREEN}✅ SOAK TEST PASSED${NC}"
    exit 0
else
    echo -e "${RED}❌ SOAK TEST FAILED${NC}"
    exit 1
fi