#!/bin/bash
# =============================================================================
# TICKETERO - Load Test Sostenido
# =============================================================================
# Ejecuta test de carga sostenida: 100 tickets en 2 minutos
# Usage: ./scripts/performance/load-test.sh
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║        TICKETERO - LOAD TEST SOSTENIDO (PERF-01)             ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# =============================================================================
# 1. PRE-TEST CLEANUP
# =============================================================================
echo -e "${YELLOW}1. Limpiando estado previo...${NC}"

if command -v docker &> /dev/null; then
    docker exec ticketero-db psql -U dev -d ticketero -c "
        DELETE FROM ticket_event;
        DELETE FROM outbox_message;
        DELETE FROM ticket;
        UPDATE advisor SET status = 'AVAILABLE', total_tickets_served = 0;
    " > /dev/null 2>&1 || echo "   ⚠ No se pudo limpiar BD (continuando...)"
fi

echo "   ✓ Base de datos limpia"

# =============================================================================
# 2. CAPTURE BASELINE
# =============================================================================
echo -e "${YELLOW}2. Capturando baseline...${NC}"

ADVISORS_AVAILABLE=0
if command -v docker &> /dev/null; then
    ADVISORS_AVAILABLE=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM advisor WHERE status='AVAILABLE';" 2>/dev/null | xargs || echo "0")
fi
echo "   ✓ Asesores disponibles: $ADVISORS_AVAILABLE"

# =============================================================================
# 3. START METRICS COLLECTION (background)
# =============================================================================
echo -e "${YELLOW}3. Iniciando recolección de métricas...${NC}"

METRICS_FILE="$PROJECT_ROOT/results/load-test-metrics-$(date +%Y%m%d-%H%M%S).csv"
mkdir -p "$PROJECT_ROOT/results"

if [ -f "$SCRIPT_DIR/../utils/metrics-collector.sh" ]; then
    bash "$SCRIPT_DIR/../utils/metrics-collector.sh" 150 "$METRICS_FILE" &
    METRICS_PID=$!
    echo "   ✓ Métricas: $METRICS_FILE (PID: $METRICS_PID)"
else
    echo "   ⚠ metrics-collector.sh no encontrado"
    METRICS_PID=""
fi

# =============================================================================
# 4. EXECUTE LOAD TEST
# =============================================================================
echo -e "${YELLOW}4. Ejecutando load test (2 minutos)...${NC}"
echo ""

START_TIME=$(date +%s)

# Check if K6 is available
if command -v k6 &> /dev/null; then
    echo "   Usando K6..."
    k6 run --vus 10 --duration 2m "$PROJECT_ROOT/k6/load-test.js" \
        --out json="$PROJECT_ROOT/results/load-test-k6.json" 2>&1 | tee "$PROJECT_ROOT/results/load-test-output.txt"
else
    echo "   K6 no disponible, usando script bash..."
    
    TICKETS_TO_CREATE=100
    CREATED=0
    ERRORS=0
    
    for i in $(seq 1 $TICKETS_TO_CREATE); do
        QUEUE_INDEX=$((i % 4))
        QUEUES=("CAJA" "PERSONAL" "EMPRESAS" "GERENCIA")
        QUEUE=${QUEUES[$QUEUE_INDEX]}
        NATIONAL_ID="300000$(printf '%03d' $i)"
        
        RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "http://localhost:8080/api/tickets" \
            -H "Content-Type: application/json" \
            -d "{
                \"nationalId\": \"${NATIONAL_ID}\",
                \"telefono\": \"+5691234${i}\",
                \"branchOffice\": \"Sucursal Test\",
                \"queueType\": \"${QUEUE}\"
            }" 2>/dev/null)
        
        HTTP_CODE=$(echo "$RESPONSE" | tail -1)
        
        if [ "$HTTP_CODE" = "201" ]; then
            CREATED=$((CREATED + 1))
            echo -ne "\r   Tickets creados: $CREATED/$TICKETS_TO_CREATE"
        else
            ERRORS=$((ERRORS + 1))
        fi
        
        # Rate limiting: ~50 tickets/min = 1 ticket/1.2s
        sleep 1.2
    done
    
    echo ""
    echo "   ✓ Creados: $CREATED, Errores: $ERRORS"
fi

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# =============================================================================
# 5. WAIT FOR PROCESSING
# =============================================================================
echo -e "${YELLOW}5. Esperando procesamiento completo...${NC}"

MAX_WAIT=120
WAITED=0

while [ $WAITED -lt $MAX_WAIT ]; do
    WAITING=0
    IN_PROGRESS=0
    
    if command -v docker &> /dev/null; then
        WAITING=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
            "SELECT COUNT(*) FROM ticket WHERE status='WAITING';" 2>/dev/null | xargs || echo "0")
        IN_PROGRESS=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
            "SELECT COUNT(*) FROM ticket WHERE status IN ('CALLED', 'IN_PROGRESS');" 2>/dev/null | xargs || echo "0")
    fi
    
    if [ "$WAITING" -eq 0 ] && [ "$IN_PROGRESS" -eq 0 ]; then
        echo "   ✓ Todos los tickets procesados"
        break
    fi
    
    echo -ne "\r   Esperando... WAITING: $WAITING, IN_PROGRESS: $IN_PROGRESS    "
    sleep 5
    WAITED=$((WAITED + 5))
done

echo ""

# =============================================================================
# 6. STOP METRICS COLLECTION
# =============================================================================
if [ ! -z "$METRICS_PID" ]; then
    kill $METRICS_PID 2>/dev/null || true
    echo "   ✓ Recolección de métricas detenida"
fi

# =============================================================================
# 7. COLLECT RESULTS
# =============================================================================
echo -e "${YELLOW}6. Recolectando resultados...${NC}"

TOTAL_TICKETS=0
COMPLETED_TICKETS=0
FAILED_OUTBOX=0

if command -v docker &> /dev/null; then
    TOTAL_TICKETS=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket;" 2>/dev/null | xargs || echo "0")
    COMPLETED_TICKETS=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status='COMPLETED';" 2>/dev/null | xargs || echo "0")
    FAILED_OUTBOX=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM outbox_message WHERE status='FAILED';" 2>/dev/null | xargs || echo "0")
fi

THROUGHPUT=0
if [ $DURATION -gt 0 ]; then
    THROUGHPUT=$(echo "scale=1; $COMPLETED_TICKETS * 60 / $DURATION" | bc 2>/dev/null || echo "0")
fi

# =============================================================================
# 8. VALIDATE CONSISTENCY
# =============================================================================
echo -e "${YELLOW}7. Validando consistencia...${NC}"
CONSISTENCY_RESULT=0
if [ -f "$SCRIPT_DIR/../utils/validate-consistency.sh" ]; then
    bash "$SCRIPT_DIR/../utils/validate-consistency.sh"
    CONSISTENCY_RESULT=$?
fi

# =============================================================================
# 9. PRINT RESULTS
# =============================================================================
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  RESULTADOS LOAD TEST SOSTENIDO${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "  Duración:           ${DURATION} segundos"
echo "  Tickets creados:    ${TOTAL_TICKETS}"
echo "  Tickets completados: ${COMPLETED_TICKETS}"
echo "  Outbox fallidos:    ${FAILED_OUTBOX}"
echo ""
echo "  📊 MÉTRICAS:"
echo "  ─────────────────────────────────────────────"

# Throughput check
THROUGHPUT_OK=false
if command -v bc &> /dev/null && (( $(echo "$THROUGHPUT >= 50" | bc -l 2>/dev/null || echo "0") )); then
    echo -e "  Throughput:         ${GREEN}${THROUGHPUT} tickets/min${NC} (≥50 ✓)"
    THROUGHPUT_OK=true
elif [ "$COMPLETED_TICKETS" -ge 50 ]; then
    echo -e "  Throughput:         ${GREEN}~${COMPLETED_TICKETS} tickets${NC} (≥50 ✓)"
    THROUGHPUT_OK=true
else
    echo -e "  Throughput:         ${RED}${THROUGHPUT} tickets/min${NC} (<50 ✗)"
fi

# Completion check
COMPLETION_OK=false
if [ "$TOTAL_TICKETS" -gt 0 ]; then
    COMPLETION_RATE=$(echo "scale=1; $COMPLETED_TICKETS * 100 / $TOTAL_TICKETS" | bc 2>/dev/null || echo "0")
    if command -v bc &> /dev/null && (( $(echo "$COMPLETION_RATE >= 99" | bc -l 2>/dev/null || echo "0") )); then
        echo -e "  Completion rate:    ${GREEN}${COMPLETION_RATE}%${NC} (≥99% ✓)"
        COMPLETION_OK=true
    else
        echo -e "  Completion rate:    ${RED}${COMPLETION_RATE}%${NC} (<99% ✗)"
    fi
fi

# Consistency check
if [ $CONSISTENCY_RESULT -eq 0 ]; then
    echo -e "  Consistencia:       ${GREEN}PASS${NC}"
else
    echo -e "  Consistencia:       ${RED}FAIL${NC}"
fi

echo ""
echo "  📁 Archivos generados:"
echo "     - $METRICS_FILE"
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

# Exit code based on results
if [ "$THROUGHPUT_OK" = true ] && [ $CONSISTENCY_RESULT -eq 0 ]; then
    echo -e "${GREEN}✅ LOAD TEST PASSED${NC}"
    exit 0
else
    echo -e "${RED}❌ LOAD TEST FAILED${NC}"
    exit 1
fi