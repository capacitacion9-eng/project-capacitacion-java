#!/bin/bash
# =============================================================================
# TICKETERO - Graceful Shutdown Test
# =============================================================================
# Valida que el shutdown libera asesores y no pierde tickets
# Usage: ./scripts/resilience/graceful-shutdown-test.sh
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   TICKETERO - GRACEFUL SHUTDOWN TEST (RES-02)                ║${NC}"
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

# Crear varios tickets
echo -e "${YELLOW}2. Creando 5 tickets...${NC}"
for i in $(seq 1 5); do
    curl -s -X POST "http://localhost:8080/api/tickets" \
        -H "Content-Type: application/json" \
        -d "{
            \"nationalId\": \"92000$(printf '%03d' $i)\",
            \"telefono\": \"+56912345678\",
            \"branchOffice\": \"Sucursal Test\",
            \"queueType\": \"CAJA\"
        }" > /dev/null 2>&1 &
done
wait
echo "   ✓ 5 tickets creados"

# Esperar que algunos estén en procesamiento
sleep 3

# Capturar estado antes del restart
BEFORE_WAITING=0
BEFORE_IN_PROGRESS=0
BEFORE_BUSY=0

if command -v docker &> /dev/null; then
    BEFORE_WAITING=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status='WAITING';" 2>/dev/null | xargs || echo "0")
    BEFORE_IN_PROGRESS=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status IN ('CALLED', 'IN_PROGRESS');" 2>/dev/null | xargs || echo "0")
    BEFORE_BUSY=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM advisor WHERE status='BUSY';" 2>/dev/null | xargs || echo "0")
fi

echo "   Estado antes del restart:"
echo "   - WAITING: $BEFORE_WAITING"
echo "   - IN_PROGRESS: $BEFORE_IN_PROGRESS"
echo "   - Advisors BUSY: $BEFORE_BUSY"

# Ejecutar graceful shutdown
echo -e "${YELLOW}3. Ejecutando restart de aplicación...${NC}"
START_TIME=$(date +%s)

if command -v docker &> /dev/null; then
    docker restart ticketero-api > /dev/null 2>&1 || echo "   ⚠ Error en restart"
fi

# Esperar que la app vuelva a estar disponible
echo -e "${YELLOW}4. Esperando que la app esté disponible...${NC}"
MAX_WAIT=90
WAITED=0

while [ $WAITED -lt $MAX_WAIT ]; do
    if curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -q "UP"; then
        RESTART_TIME=$(($(date +%s) - START_TIME))
        echo "   ✓ App disponible en ${RESTART_TIME}s"
        break
    fi
    
    echo -ne "\r   Esperando... ${WAITED}s    "
    sleep 5
    WAITED=$((WAITED + 5))
done

echo ""

# Esperar procesamiento post-restart
sleep 30

# Validar estado después del restart
echo -e "${YELLOW}5. Validando estado post-restart...${NC}"
echo ""

AFTER_BUSY=0
AFTER_COMPLETED=0
TOTAL_TICKETS=0

if command -v docker &> /dev/null; then
    AFTER_BUSY=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM advisor WHERE status='BUSY';" 2>/dev/null | xargs || echo "0")
    AFTER_COMPLETED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status='COMPLETED';" 2>/dev/null | xargs || echo "0")
    TOTAL_TICKETS=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket;" 2>/dev/null | xargs || echo "0")
fi

echo "   Estado post-restart:"
echo "   - Advisors BUSY: $AFTER_BUSY"
echo "   - Tickets COMPLETED: $AFTER_COMPLETED"
echo "   - Total tickets: $TOTAL_TICKETS"
echo ""

PASS=true

# Check 1: Advisors liberados (o procesando normalmente)
if [ "$AFTER_BUSY" -le 1 ]; then
    echo -e "   - Advisors liberados: ${GREEN}PASS${NC}"
else
    echo -e "   - Advisors liberados: ${YELLOW}WARN${NC} ($AFTER_BUSY BUSY)"
fi

# Check 2: No se perdieron tickets
if [ "$TOTAL_TICKETS" -eq 5 ]; then
    echo -e "   - Tickets preservados: ${GREEN}PASS${NC}"
else
    echo -e "   - Tickets preservados: ${RED}FAIL${NC} ($TOTAL_TICKETS/5)"
    PASS=false
fi

# Check 3: App disponible rápido
if [ "${RESTART_TIME:-999}" -lt 60 ]; then
    echo -e "   - Restart < 60s: ${GREEN}PASS${NC}"
else
    echo -e "   - Restart < 60s: ${RED}FAIL${NC}"
    PASS=false
fi

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if [ "$PASS" = true ]; then
    echo -e "  ${GREEN}✅ GRACEFUL SHUTDOWN TEST PASSED${NC}"
    exit 0
else
    echo -e "  ${RED}❌ GRACEFUL SHUTDOWN TEST FAILED${NC}"
    exit 1
fi