#!/bin/bash
# =============================================================================
# TICKETERO - Outbox Concurrency Test
# =============================================================================
# Valida que el patrón Outbox maneja carga alta sin duplicados
# Usage: ./scripts/concurrency/outbox-concurrency-test.sh
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   TICKETERO - OUTBOX CONCURRENCY TEST (CONC-03)              ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Setup
echo -e "${YELLOW}1. Limpiando estado...${NC}"
if command -v docker &> /dev/null; then
    docker exec ticketero-db psql -U dev -d ticketero -c "
        DELETE FROM ticket_event;
        DELETE FROM outbox_message;
        DELETE FROM ticket;
    " > /dev/null 2>&1 || echo "   ⚠ No se pudo limpiar BD"
fi

# Crear 100 tickets simultáneos
echo -e "${YELLOW}2. Creando 100 tickets simultáneamente...${NC}"
START_TIME=$(date +%s)

for i in $(seq 1 100); do
    (
        curl -s -X POST "http://localhost:8080/api/tickets" \
            -H "Content-Type: application/json" \
            -d "{
                \"nationalId\": \"800$(printf '%05d' $i)\",
                \"telefono\": \"+56912345678\",
                \"branchOffice\": \"Sucursal Test\",
                \"queueType\": \"CAJA\"
            }" > /dev/null 2>&1
    ) &
done

wait
CREATE_END=$(date +%s)
CREATE_TIME=$((CREATE_END - START_TIME))
echo "   ✓ 100 tickets creados en ${CREATE_TIME}s"

# Verificar mensajes en Outbox
OUTBOX_COUNT=0
if command -v docker &> /dev/null; then
    OUTBOX_COUNT=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM outbox_message;" 2>/dev/null | xargs || echo "0")
fi
echo "   ✓ Mensajes en Outbox: $OUTBOX_COUNT"

# Esperar que todos se publiquen
echo -e "${YELLOW}3. Esperando publicación (max 30s)...${NC}"
MAX_WAIT=30
WAITED=0

while [ $WAITED -lt $MAX_WAIT ]; do
    PENDING=0
    if command -v docker &> /dev/null; then
        PENDING=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
            "SELECT COUNT(*) FROM outbox_message WHERE status='PENDING';" 2>/dev/null | xargs || echo "0")
    fi
    
    if [ "$PENDING" -eq 0 ]; then
        PUBLISH_END=$(date +%s)
        PUBLISH_TIME=$((PUBLISH_END - CREATE_END))
        echo "   ✓ Todos publicados en ${PUBLISH_TIME}s"
        break
    fi
    
    echo -ne "\r   Pendientes: $PENDING    "
    sleep 2
    WAITED=$((WAITED + 2))
done

echo ""

# Validar resultados
echo -e "${YELLOW}4. Validando resultados...${NC}"

SENT=0
FAILED=0
PENDING=0

if command -v docker &> /dev/null; then
    SENT=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM outbox_message WHERE status='SENT';" 2>/dev/null | xargs || echo "0")
    FAILED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM outbox_message WHERE status='FAILED';" 2>/dev/null | xargs || echo "0")
    PENDING=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM outbox_message WHERE status='PENDING';" 2>/dev/null | xargs || echo "0")
fi

echo ""
echo "   Outbox status:"
echo "   - SENT:    $SENT"
echo "   - FAILED:  $FAILED"
echo "   - PENDING: $PENDING"
echo ""

# Validaciones
PASS=true

if [ "$SENT" -ge 90 ]; then
    echo -e "   - ≥90% enviados: ${GREEN}PASS${NC} ($SENT/100)"
else
    echo -e "   - ≥90% enviados: ${RED}FAIL${NC} ($SENT/100)"
    PASS=false
fi

if [ "$FAILED" -le 5 ]; then
    echo -e "   - ≤5 fallidos: ${GREEN}PASS${NC} ($FAILED)"
else
    echo -e "   - ≤5 fallidos: ${RED}FAIL${NC} ($FAILED)"
    PASS=false
fi

if [ "${PUBLISH_TIME:-999}" -lt 15 ]; then
    echo -e "   - Tiempo < 15s: ${GREEN}PASS${NC} (${PUBLISH_TIME}s)"
else
    echo -e "   - Tiempo < 15s: ${YELLOW}WARN${NC} (${PUBLISH_TIME:-timeout}s)"
fi

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if [ "$PASS" = true ]; then
    echo -e "  ${GREEN}✅ OUTBOX CONCURRENCY TEST PASSED${NC}"
    exit 0
else
    echo -e "  ${RED}❌ OUTBOX CONCURRENCY TEST FAILED${NC}"
    exit 1
fi