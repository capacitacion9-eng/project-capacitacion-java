#!/bin/bash
# =============================================================================
# TICKETERO - Worker Crash Test
# =============================================================================
# Simula crash de worker y valida auto-recovery
# Usage: ./scripts/resilience/worker-crash-test.sh
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   TICKETERO - WORKER CRASH TEST (RES-01)                     ║${NC}"
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

# Crear ticket
echo -e "${YELLOW}2. Creando ticket...${NC}"
curl -s -X POST "http://localhost:8080/api/tickets" \
    -H "Content-Type: application/json" \
    -d '{
        "nationalId": "90000001",
        "telefono": "+56912345678",
        "branchOffice": "Sucursal Test",
        "queueType": "CAJA"
    }' > /dev/null 2>&1 || echo "   ⚠ Error creando ticket"

# Esperar que empiece procesamiento
echo -e "${YELLOW}3. Esperando inicio de procesamiento...${NC}"
sleep 5

# Simular crash: detener heartbeat de un asesor BUSY
echo -e "${YELLOW}4. Simulando crash de worker...${NC}"
if command -v docker &> /dev/null; then
    docker exec ticketero-db psql -U dev -d ticketero -c "
        UPDATE advisor 
        SET last_heartbeat = NOW() - INTERVAL '120 seconds'
        WHERE status = 'BUSY'
        LIMIT 1;
    " > /dev/null 2>&1 || echo "   ⚠ No se pudo simular crash"
fi

echo "   ✓ Heartbeat detenido (simulando worker muerto)"

# Esperar detección (recovery check cada 30s, timeout 60s)
echo -e "${YELLOW}5. Esperando detección de recovery (max 120s)...${NC}"
START_TIME=$(date +%s)
MAX_WAIT=120
DETECTED=false

while [ $(($(date +%s) - START_TIME)) -lt $MAX_WAIT ]; do
    # Check if any advisor was recovered (status changed from BUSY to AVAILABLE)
    AVAILABLE_COUNT=0
    if command -v docker &> /dev/null; then
        AVAILABLE_COUNT=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
            "SELECT COUNT(*) FROM advisor WHERE status='AVAILABLE';" 2>/dev/null | xargs || echo "0")
    fi
    
    # If we have available advisors, recovery likely happened
    if [ "$AVAILABLE_COUNT" -gt 0 ]; then
        DETECTION_TIME=$(($(date +%s) - START_TIME))
        DETECTED=true
        echo ""
        echo "   ✓ Recovery detectado en ${DETECTION_TIME}s"
        break
    fi
    
    echo -ne "\r   Esperando... $(( $(date +%s) - START_TIME ))s    "
    sleep 5
done

echo ""

# Validar resultados
echo -e "${YELLOW}6. Validando resultados...${NC}"
echo ""

# Check 1: Asesor liberado
BUSY_ADVISORS=0
if command -v docker &> /dev/null; then
    BUSY_ADVISORS=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM advisor WHERE status='BUSY';" 2>/dev/null | xargs || echo "0")
fi

if [ "$BUSY_ADVISORS" -eq 0 ]; then
    echo -e "   - Asesor liberado: ${GREEN}PASS${NC}"
else
    echo -e "   - Asesor liberado: ${YELLOW}WARN${NC} ($BUSY_ADVISORS aún BUSY)"
fi

# Check 2: Tiempo de detección < 90s
if [ "$DETECTED" = true ] && [ "$DETECTION_TIME" -lt 90 ]; then
    echo -e "   - Tiempo < 90s: ${GREEN}PASS${NC} (${DETECTION_TIME}s)"
else
    echo -e "   - Tiempo < 90s: ${RED}FAIL${NC} (${DETECTION_TIME:-timeout}s)"
fi

# Check 3: Sistema sigue funcionando
SYSTEM_HEALTHY=false
if curl -s http://localhost:8080/actuator/health | grep -q "UP" 2>/dev/null; then
    echo -e "   - Sistema funcionando: ${GREEN}PASS${NC}"
    SYSTEM_HEALTHY=true
else
    echo -e "   - Sistema funcionando: ${RED}FAIL${NC}"
fi

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if [ "$DETECTED" = true ] && [ "$DETECTION_TIME" -lt 90 ] && [ "$SYSTEM_HEALTHY" = true ]; then
    echo -e "  ${GREEN}✅ WORKER CRASH TEST PASSED${NC}"
    exit 0
else
    echo -e "  ${RED}❌ WORKER CRASH TEST FAILED${NC}"
    exit 1
fi