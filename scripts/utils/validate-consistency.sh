#!/bin/bash
# =============================================================================
# TICKETERO - Consistency Validator
# =============================================================================
# Valida consistencia del sistema después de pruebas de carga
# Usage: ./scripts/utils/validate-consistency.sh
# =============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "═══════════════════════════════════════════════════════════════"
echo "  TICKETERO - VALIDACIÓN DE CONSISTENCIA"
echo "═══════════════════════════════════════════════════════════════"
echo ""

ERRORS=0

# 1. Tickets en estado inconsistente
echo -n "1. Tickets en estado inconsistente... "
INCONSISTENT=0
if command -v docker &> /dev/null; then
    INCONSISTENT=$(docker exec ticketero-db psql -U dev -d ticketero -t -c "
        SELECT COUNT(*) FROM ticket t
        WHERE (t.status = 'IN_PROGRESS' AND t.started_at IS NULL)
           OR (t.status = 'COMPLETED' AND t.completed_at IS NULL)
           OR (t.status = 'CALLED' AND t.assigned_advisor_id IS NULL);
    " 2>/dev/null | xargs || echo "0")
fi

if [ "$INCONSISTENT" -eq 0 ]; then
    echo -e "${GREEN}PASS${NC} (0 encontrados)"
else
    echo -e "${RED}FAIL${NC} ($INCONSISTENT encontrados)"
    ERRORS=$((ERRORS + 1))
fi

# 2. Asesores en estado inconsistente
echo -n "2. Asesores BUSY sin ticket activo... "
BUSY_NO_TICKET=0
if command -v docker &> /dev/null; then
    BUSY_NO_TICKET=$(docker exec ticketero-db psql -U dev -d ticketero -t -c "
        SELECT COUNT(*) FROM advisor a
        WHERE a.status = 'BUSY'
        AND NOT EXISTS (
            SELECT 1 FROM ticket t 
            WHERE t.assigned_advisor_id = a.id 
            AND t.status IN ('CALLED', 'IN_PROGRESS')
        );
    " 2>/dev/null | xargs || echo "0")
fi

if [ "$BUSY_NO_TICKET" -eq 0 ]; then
    echo -e "${GREEN}PASS${NC} (0 encontrados)"
else
    echo -e "${YELLOW}WARN${NC} ($BUSY_NO_TICKET encontrados - recovery pendiente)"
fi

# 3. Mensajes Outbox fallidos
echo -n "3. Mensajes Outbox FAILED... "
OUTBOX_FAILED=0
if command -v docker &> /dev/null; then
    OUTBOX_FAILED=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT COUNT(*) FROM outbox_message WHERE status='FAILED';" 2>/dev/null | xargs || echo "0")
fi

if [ "$OUTBOX_FAILED" -eq 0 ]; then
    echo -e "${GREEN}PASS${NC} (0 fallidos)"
else
    echo -e "${RED}FAIL${NC} ($OUTBOX_FAILED mensajes fallidos)"
    ERRORS=$((ERRORS + 1))
fi

# 4. Tickets duplicados (mismo nationalId + cola en estado activo)
echo -n "4. Tickets potencialmente duplicados... "
DUPLICATES=0
if command -v docker &> /dev/null; then
    DUPLICATES=$(docker exec ticketero-db psql -U dev -d ticketero -t -c "
        SELECT COUNT(*) FROM (
            SELECT national_id, queue_type, COUNT(*) as cnt
            FROM ticket
            WHERE status IN ('WAITING', 'CALLED', 'IN_PROGRESS')
            GROUP BY national_id, queue_type
            HAVING COUNT(*) > 1
        ) dups;
    " 2>/dev/null | xargs || echo "0")
fi

if [ "$DUPLICATES" -eq 0 ]; then
    echo -e "${GREEN}PASS${NC} (0 duplicados)"
else
    echo -e "${YELLOW}WARN${NC} ($DUPLICATES posibles duplicados)"
fi

# 5. Conexiones DB abiertas
echo -n "5. Conexiones PostgreSQL... "
DB_CONN=0
if command -v docker &> /dev/null; then
    DB_CONN=$(docker exec ticketero-db psql -U dev -d ticketero -t -c \
        "SELECT count(*) FROM pg_stat_activity WHERE datname='ticketero';" 2>/dev/null | xargs || echo "0")
fi

if [ "$DB_CONN" -lt 20 ]; then
    echo -e "${GREEN}OK${NC} ($DB_CONN conexiones)"
else
    echo -e "${YELLOW}WARN${NC} ($DB_CONN conexiones - revisar pool)"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
if [ $ERRORS -eq 0 ]; then
    echo -e "  RESULTADO: ${GREEN}SISTEMA CONSISTENTE${NC}"
else
    echo -e "  RESULTADO: ${RED}$ERRORS ERRORES DE CONSISTENCIA${NC}"
fi
echo "═══════════════════════════════════════════════════════════════"

exit $ERRORS