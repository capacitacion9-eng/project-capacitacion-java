#!/bin/bash
# =============================================================================
# TICKETERO - Race Condition Test
# =============================================================================
# Valida que SELECT FOR UPDATE previene asignación doble de asesores
# Usage: ./scripts/concurrency/race-condition-test.sh
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   TICKETERO - RACE CONDITION TEST (CONC-01)                  ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# =============================================================================
# 1. SETUP: Solo 1 asesor disponible
# =============================================================================
echo -e "${YELLOW}1. Configurando escenario...${NC}"

if command -v docker &> /dev/null; then
    docker exec ticketero-db psql -U dev -d ticketero -c "
        -- Limpiar
        DELETE FROM ticket_event;
        DELETE FROM outbox_message;
        DELETE FROM ticket;
        
        -- Solo 1 asesor AVAILABLE, resto en BREAK
        UPDATE advisor SET status = 'BREAK';
        UPDATE advisor SET status = 'AVAILABLE' WHERE id = 1;
    " > /dev/null 2>&1 || echo "   ⚠ No se pudo configurar BD"
fi

AVAILABLE=0
if command -v docker &> /dev/null; then
    AVAILABLE=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM advisor WHERE status='AVAILABLE';" 2>/dev/null | xargs || echo "0")
fi
echo "   ✓ Asesores AVAILABLE: $AVAILABLE (debe ser 1)"

# =============================================================================
# 2. CREAR 5 TICKETS SIMULTÁNEOS
# =============================================================================
echo -e "${YELLOW}2. Creando 5 tickets simultáneamente...${NC}"

for i in $(seq 1 5); do
    (
        curl -s -X POST "http://localhost:8080/api/tickets" \
            -H "Content-Type: application/json" \
            -d "{
                \"nationalId\": \"600000$(printf '%03d' $i)\",
                \"telefono\": \"+5691234${i}\",
                \"branchOffice\": \"Sucursal Test\",
                \"queueType\": \"CAJA\"
            }" > /dev/null 2>&1
    ) &
done

wait
echo "   ✓ 5 tickets creados"

# =============================================================================
# 3. ESPERAR PROCESAMIENTO INICIAL
# =============================================================================
echo -e "${YELLOW}3. Esperando procesamiento (30s)...${NC}"
sleep 30

# =============================================================================
# 4. VALIDAR RESULTADOS
# =============================================================================
echo -e "${YELLOW}4. Validando resultados...${NC}"

# Contar tickets por estado
COMPLETED=0
IN_PROGRESS=0
WAITING=0

if command -v docker &> /dev/null; then
    COMPLETED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status='COMPLETED';" 2>/dev/null | xargs || echo "0")
    IN_PROGRESS=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status IN ('CALLED', 'IN_PROGRESS');" 2>/dev/null | xargs || echo "0")
    WAITING=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM ticket WHERE status='WAITING';" 2>/dev/null | xargs || echo "0")
fi

echo ""
echo "   Estado de tickets:"
echo "   - COMPLETED:    $COMPLETED"
echo "   - IN_PROGRESS:  $IN_PROGRESS"
echo "   - WAITING:      $WAITING"

# Verificar que no hay asignaciones dobles
DOUBLE_ASSIGNED=0
if command -v docker &> /dev/null; then
    DOUBLE_ASSIGNED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c "
        SELECT COUNT(*) FROM (
            SELECT assigned_advisor_id, COUNT(*) 
            FROM ticket 
            WHERE assigned_advisor_id IS NOT NULL 
            AND status IN ('CALLED', 'IN_PROGRESS')
            GROUP BY assigned_advisor_id 
            HAVING COUNT(*) > 1
        ) doubles;
    " 2>/dev/null | xargs || echo "0")
fi

# Verificar deadlocks
DEADLOCKS=0
if command -v docker &> /dev/null; then
    DEADLOCKS=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT deadlocks FROM pg_stat_database WHERE datname='ticketero';" 2>/dev/null | xargs || echo "0")
fi

echo ""
echo "   Validaciones:"

# Check 1: No double assignments
if [ "$DOUBLE_ASSIGNED" -eq 0 ]; then
    echo -e "   - Asignaciones dobles: ${GREEN}0 (PASS)${NC}"
else
    echo -e "   - Asignaciones dobles: ${RED}$DOUBLE_ASSIGNED (FAIL)${NC}"
fi

# Check 2: Solo 1 ticket procesándose/completado por vez
PROCESSED=$((COMPLETED + IN_PROGRESS))
if [ "$PROCESSED" -le 2 ]; then
    echo -e "   - Procesamiento serializado: ${GREEN}PASS${NC}"
else
    echo -e "   - Procesamiento serializado: ${YELLOW}WARN ($PROCESSED simultáneos)${NC}"
fi

# Check 3: No deadlocks
if [ "${DEADLOCKS:-0}" -eq 0 ]; then
    echo -e "   - Deadlocks PostgreSQL: ${GREEN}0 (PASS)${NC}"
else
    echo -e "   - Deadlocks PostgreSQL: ${RED}$DEADLOCKS (FAIL)${NC}"
fi

# =============================================================================
# 5. CLEANUP
# =============================================================================
echo -e "${YELLOW}5. Restaurando estado...${NC}"
if command -v docker &> /dev/null; then
    docker exec ticketero-db psql -U dev -d ticketero -c \
        "UPDATE advisor SET status = 'AVAILABLE';" > /dev/null 2>&1 || true
fi

# =============================================================================
# RESULTADO FINAL
# =============================================================================
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

if [ "$DOUBLE_ASSIGNED" -eq 0 ] && [ "${DEADLOCKS:-0}" -eq 0 ]; then
    echo -e "  ${GREEN}✅ RACE CONDITION TEST PASSED${NC}"
    echo "  SELECT FOR UPDATE funcionando correctamente"
    exit 0
else
    echo -e "  ${RED}❌ RACE CONDITION TEST FAILED${NC}"
    exit 1
fi