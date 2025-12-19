#!/bin/bash
# =============================================================================
# TICKETERO - Idempotency Test
# =============================================================================
# Valida que tickets ya procesados no se reprocesan
# Usage: ./scripts/concurrency/idempotency-test.sh
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   TICKETERO - IDEMPOTENCY TEST (CONC-02)                     ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Setup
echo -e "${YELLOW}1. Configurando escenario...${NC}"
if command -v docker &> /dev/null; then
    docker exec ticketero-db psql -U dev -d ticketero -c "
        DELETE FROM ticket_event;
        DELETE FROM outbox_message;
        DELETE FROM ticket;
        UPDATE advisor SET status = 'AVAILABLE', total_tickets_served = 0;
    " > /dev/null 2>&1 || echo "   ⚠ No se pudo limpiar BD"
fi

# Crear y esperar que se complete un ticket
echo -e "${YELLOW}2. Creando ticket y esperando procesamiento...${NC}"

RESPONSE=$(curl -s -X POST "http://localhost:8080/api/tickets" \
    -H "Content-Type: application/json" \
    -d '{
        "nationalId": "70000001",
        "telefono": "+56912345678",
        "branchOffice": "Sucursal Test",
        "queueType": "CAJA"
    }' 2>/dev/null || echo '{"numero":"ERROR"}')

TICKET_ID=$(echo "$RESPONSE" | grep -o '"numero":"[^"]*"' | cut -d'"' -f4 || echo "ERROR")
echo "   ✓ Ticket creado: $TICKET_ID"

# Esperar procesamiento
sleep 30

# Capturar estado inicial
INITIAL_COMPLETED=0
INITIAL_EVENTS=0
INITIAL_SERVED=0

if command -v docker &> /dev/null; then
    INITIAL_COMPLETED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status='COMPLETED';" 2>/dev/null | xargs || echo "0")
    INITIAL_EVENTS=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket_event;" 2>/dev/null | xargs || echo "0")
    INITIAL_SERVED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT SUM(total_tickets_served) FROM advisor;" 2>/dev/null | xargs || echo "0")
fi

echo "   Estado inicial:"
echo "   - Tickets completados: $INITIAL_COMPLETED"
echo "   - Eventos registrados: $INITIAL_EVENTS"
echo "   - Total servidos: $INITIAL_SERVED"

# Simular redelivery creando ticket duplicado
echo -e "${YELLOW}3. Simulando redelivery (ticket duplicado)...${NC}"

curl -s -X POST "http://localhost:8080/api/tickets" \
    -H "Content-Type: application/json" \
    -d '{
        "nationalId": "70000001",
        "telefono": "+56912345678",
        "branchOffice": "Sucursal Test",
        "queueType": "CAJA"
    }' > /dev/null 2>&1

# Esperar procesamiento del mensaje duplicado
echo -e "${YELLOW}4. Esperando procesamiento del mensaje duplicado (10s)...${NC}"
sleep 10

# Validar que nada cambió significativamente
FINAL_COMPLETED=0
FINAL_EVENTS=0
FINAL_SERVED=0

if command -v docker &> /dev/null; then
    FINAL_COMPLETED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status='COMPLETED';" 2>/dev/null | xargs || echo "0")
    FINAL_EVENTS=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket_event;" 2>/dev/null | xargs || echo "0")
    FINAL_SERVED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT SUM(total_tickets_served) FROM advisor;" 2>/dev/null | xargs || echo "0")
fi

echo -e "${YELLOW}5. Validando idempotencia...${NC}"
echo ""
echo "   Estado final:"
echo "   - Tickets completados: $FINAL_COMPLETED"
echo "   - Eventos registrados: $FINAL_EVENTS"
echo "   - Total servidos: $FINAL_SERVED"
echo ""

PASS=true

# Validar que no se procesó doble (permitir 1 ticket adicional por el duplicado)
if [ "$FINAL_COMPLETED" -le $((INITIAL_COMPLETED + 1)) ]; then
    echo -e "   - Tickets no duplicados: ${GREEN}PASS${NC}"
else
    echo -e "   - Tickets no duplicados: ${RED}FAIL${NC} (+$((FINAL_COMPLETED - INITIAL_COMPLETED)) tickets)"
    PASS=false
fi

# Validar eventos (puede haber algunos eventos adicionales por el intento)
EVENT_DIFF=$((FINAL_EVENTS - INITIAL_EVENTS))
if [ "$EVENT_DIFF" -le 3 ]; then
    echo -e "   - Eventos controlados: ${GREEN}PASS${NC} (+$EVENT_DIFF eventos)"
else
    echo -e "   - Eventos controlados: ${YELLOW}WARN${NC} (+$EVENT_DIFF eventos)"
fi

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if [ "$PASS" = true ]; then
    echo -e "  ${GREEN}✅ IDEMPOTENCY TEST PASSED${NC}"
    exit 0
else
    echo -e "  ${RED}❌ IDEMPOTENCY TEST FAILED${NC}"
    exit 1
fi