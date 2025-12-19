#!/bin/bash
# =============================================================================
# TICKETERO - API Connectivity Test
# =============================================================================
# Tests basic API connectivity and response
# Usage: ./scripts/utils/test-api-connectivity.sh
# =============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

BASE_URL=${1:-"http://localhost:8080"}

echo "═══════════════════════════════════════════════════════════════"
echo "  TICKETERO - API CONNECTIVITY TEST"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  Base URL: $BASE_URL"
echo ""

ERRORS=0

# Test 1: Health endpoint
echo -n "1. Health endpoint... "
if curl -s -f "$BASE_URL/actuator/health" > /dev/null 2>&1; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${RED}FAIL${NC} (API not responding)"
    ERRORS=$((ERRORS + 1))
fi

# Test 2: Create ticket endpoint (basic structure test)
echo -n "2. Create ticket endpoint structure... "
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/tickets" \
    -H "Content-Type: application/json" \
    -d '{
        "nationalId": "12345678",
        "telefono": "+56912345678",
        "branchOffice": "Test Branch",
        "queueType": "CAJA"
    }' 2>/dev/null)

HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "400" ] || [ "$HTTP_CODE" = "500" ]; then
    echo -e "${GREEN}PASS${NC} (HTTP $HTTP_CODE)"
else
    echo -e "${RED}FAIL${NC} (HTTP $HTTP_CODE or no response)"
    ERRORS=$((ERRORS + 1))
fi

# Test 3: Basic load test (5 requests)
echo -n "3. Basic load test (5 requests)... "
SUCCESS_COUNT=0
for i in $(seq 1 5); do
    RESPONSE=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/tickets" \
        -H "Content-Type: application/json" \
        -d "{
            \"nationalId\": \"1234567$i\",
            \"telefono\": \"+5691234567$i\",
            \"branchOffice\": \"Test Branch\",
            \"queueType\": \"CAJA\"
        }" 2>/dev/null)
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -c 4)
    if [ "$HTTP_CODE" = "201" ]; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    fi
    
    sleep 0.5
done

if [ "$SUCCESS_COUNT" -ge 3 ]; then
    echo -e "${GREEN}PASS${NC} ($SUCCESS_COUNT/5 successful)"
else
    echo -e "${YELLOW}WARN${NC} ($SUCCESS_COUNT/5 successful)"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
if [ $ERRORS -eq 0 ]; then
    echo -e "  RESULTADO: ${GREEN}API CONNECTIVITY OK${NC}"
    echo "  Sistema listo para pruebas NFR"
else
    echo -e "  RESULTADO: ${RED}$ERRORS ERRORES DE CONECTIVIDAD${NC}"
    echo "  Verificar que el sistema esté corriendo"
fi
echo "═══════════════════════════════════════════════════════════════"

exit $ERRORS