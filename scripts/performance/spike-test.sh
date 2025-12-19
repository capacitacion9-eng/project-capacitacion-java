#!/bin/bash
# =============================================================================
# TICKETERO - Spike Test
# =============================================================================
# Ejecuta test de spike: 50 tickets simultáneos en 10 segundos
# Usage: ./scripts/performance/spike-test.sh
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║        TICKETERO - SPIKE TEST (PERF-02)                      ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Cleanup
echo -e "${YELLOW}1. Limpiando estado previo...${NC}"
if command -v docker &> /dev/null; then
    docker exec ticketero-db psql -U dev -d ticketero -c "
        DELETE FROM ticket_event;
        DELETE FROM outbox_message;
        DELETE FROM ticket;
        UPDATE advisor SET status = 'AVAILABLE', total_tickets_served = 0;
    " > /dev/null 2>&1 || echo "   ⚠ No se pudo limpiar BD"
fi

# Start metrics
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
METRICS_FILE="$PROJECT_ROOT/results/spike-test-metrics-$(date +%Y%m%d-%H%M%S).csv"
mkdir -p "$PROJECT_ROOT/results"

if [ -f "$SCRIPT_DIR/../utils/metrics-collector.sh" ]; then
    bash "$SCRIPT_DIR/../utils/metrics-collector.sh" 120 "$METRICS_FILE" &
    METRICS_PID=$!
fi

# Execute spike
echo -e "${YELLOW}2. Ejecutando spike (50 tickets en 10 segundos)...${NC}"
START_TIME=$(date +%s)

# Crear 50 tickets en paralelo
for i in $(seq 1 50); do
    (
        QUEUE_INDEX=$((i % 4))
        QUEUES=("CAJA" "PERSONAL" "EMPRESAS" "GERENCIA")
        QUEUE=${QUEUES[$QUEUE_INDEX]}
        
        curl -s -X POST "http://localhost:8080/api/tickets" \
            -H "Content-Type: application/json" \
            -d "{
                \"nationalId\": \"400000$(printf '%03d' $i)\",
                \"telefono\": \"+5691234${i}\",
                \"branchOffice\": \"Sucursal Test\",
                \"queueType\": \"${QUEUE}\"
            }" > /dev/null 2>&1
    ) &
done

wait
SPIKE_END=$(date +%s)
SPIKE_DURATION=$((SPIKE_END - START_TIME))
echo "   ✓ Spike completado en ${SPIKE_DURATION} segundos"

# Wait for processing
echo -e "${YELLOW}3. Esperando procesamiento...${NC}"
MAX_WAIT=180
WAITED=0

while [ $WAITED -lt $MAX_WAIT ]; do
    COMPLETED=0
    if command -v docker &> /dev/null; then
        COMPLETED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
            "SELECT COUNT(*) FROM ticket WHERE status='COMPLETED';" 2>/dev/null | xargs || echo "0")
    fi
    
    if [ "$COMPLETED" -ge 45 ]; then
        PROCESS_END=$(date +%s)
        TOTAL_PROCESS_TIME=$((PROCESS_END - START_TIME))
        echo "   ✓ 45+ procesados en ${TOTAL_PROCESS_TIME} segundos"
        break
    fi
    
    echo -ne "\r   Completados: $COMPLETED/50    "
    sleep 5
    WAITED=$((WAITED + 5))
done

# Stop metrics
if [ ! -z "$METRICS_PID" ]; then
    kill $METRICS_PID 2>/dev/null || true
fi

# Results
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  RESULTADOS SPIKE TEST${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "  Tickets creados:     50 en ${SPIKE_DURATION}s"
echo "  Tiempo procesamiento: ${TOTAL_PROCESS_TIME:-timeout}s"
echo ""

# Validate
if [ -f "$SCRIPT_DIR/../utils/validate-consistency.sh" ]; then
    bash "$SCRIPT_DIR/../utils/validate-consistency.sh"
    CONSISTENCY_RESULT=$?
fi

if [ "${TOTAL_PROCESS_TIME:-999}" -lt 180 ] && [ "${CONSISTENCY_RESULT:-1}" -eq 0 ]; then
    echo -e "${GREEN}✅ SPIKE TEST PASSED${NC}"
    exit 0
else
    echo -e "${RED}❌ SPIKE TEST FAILED${NC}"
    exit 1
fi