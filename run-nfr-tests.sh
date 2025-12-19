#!/bin/bash
# =============================================================================
# TICKETERO - NFR Test Runner
# =============================================================================
# Ejecuta todos los tests no funcionales
# Usage: ./run-nfr-tests.sh [test_category]
# Categories: performance, concurrency, resilience, all
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BLUE='\033[0;34m'
NC='\033[0m'

CATEGORY=${1:-all}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                TICKETERO - NFR TEST SUITE                     ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "  Categoría: $CATEGORY"
echo "  Timestamp: $(date)"
echo ""

# Results tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
RESULTS_FILE="$SCRIPT_DIR/results/nfr-test-results-$(date +%Y%m%d-%H%M%S).txt"

mkdir -p "$SCRIPT_DIR/results"

# Function to run a test
run_test() {
    local test_name="$1"
    local test_script="$2"
    local category="$3"
    
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}  EJECUTANDO: $test_name${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if [ -f "$test_script" ]; then
        START_TIME=$(date +%s)
        
        if bash "$test_script"; then
            END_TIME=$(date +%s)
            DURATION=$((END_TIME - START_TIME))
            echo -e "${GREEN}✅ $test_name PASSED${NC} (${DURATION}s)"
            echo "$test_name,PASS,$DURATION,$category,$(date)" >> "$RESULTS_FILE"
            PASSED_TESTS=$((PASSED_TESTS + 1))
        else
            END_TIME=$(date +%s)
            DURATION=$((END_TIME - START_TIME))
            echo -e "${RED}❌ $test_name FAILED${NC} (${DURATION}s)"
            echo "$test_name,FAIL,$DURATION,$category,$(date)" >> "$RESULTS_FILE"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    else
        echo -e "${YELLOW}⚠ $test_name SKIPPED${NC} (script not found: $test_script)"
        echo "$test_name,SKIP,0,$category,$(date)" >> "$RESULTS_FILE"
    fi
    
    echo ""
    sleep 2
}

# Initialize results file
echo "test_name,result,duration_s,category,timestamp" > "$RESULTS_FILE"

# Performance Tests
if [ "$CATEGORY" = "all" ] || [ "$CATEGORY" = "performance" ]; then
    echo -e "${YELLOW}🚀 PERFORMANCE TESTS${NC}"
    echo ""
    
    run_test "PERF-01 Load Test Sostenido" "$SCRIPT_DIR/scripts/performance/load-test.sh" "performance"
    run_test "PERF-02 Spike Test" "$SCRIPT_DIR/scripts/performance/spike-test.sh" "performance"
    run_test "PERF-03 Soak Test" "$SCRIPT_DIR/scripts/performance/soak-test.sh" "performance"
fi

# Concurrency Tests
if [ "$CATEGORY" = "all" ] || [ "$CATEGORY" = "concurrency" ]; then
    echo -e "${YELLOW}⚡ CONCURRENCY TESTS${NC}"
    echo ""
    
    run_test "CONC-01 Race Condition Test" "$SCRIPT_DIR/scripts/concurrency/race-condition-test.sh" "concurrency"
    run_test "CONC-02 Idempotency Test" "$SCRIPT_DIR/scripts/concurrency/idempotency-test.sh" "concurrency"
    run_test "CONC-03 Outbox Concurrency Test" "$SCRIPT_DIR/scripts/concurrency/outbox-concurrency-test.sh" "concurrency"
fi

# Resilience Tests
if [ "$CATEGORY" = "all" ] || [ "$CATEGORY" = "resilience" ]; then
    echo -e "${YELLOW}🛡️ RESILIENCE TESTS${NC}"
    echo ""
    
    run_test "RES-01 Worker Crash Test" "$SCRIPT_DIR/scripts/resilience/worker-crash-test.sh" "resilience"
    run_test "RES-02 Graceful Shutdown Test" "$SCRIPT_DIR/scripts/resilience/graceful-shutdown-test.sh" "resilience"
fi

# Final consistency check
echo -e "${YELLOW}🔍 FINAL CONSISTENCY CHECK${NC}"
echo ""
if [ -f "$SCRIPT_DIR/scripts/utils/validate-consistency.sh" ]; then
    bash "$SCRIPT_DIR/scripts/utils/validate-consistency.sh"
    CONSISTENCY_RESULT=$?
    if [ $CONSISTENCY_RESULT -eq 0 ]; then
        echo "CONSISTENCY_CHECK,PASS,0,validation,$(date)" >> "$RESULTS_FILE"
    else
        echo "CONSISTENCY_CHECK,FAIL,0,validation,$(date)" >> "$RESULTS_FILE"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
fi

# Summary
echo ""
echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                    RESUMEN FINAL                              ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "  Total Tests:    $TOTAL_TESTS"
echo -e "  Passed:         ${GREEN}$PASSED_TESTS${NC}"
echo -e "  Failed:         ${RED}$FAILED_TESTS${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "  ${GREEN}🎉 ALL TESTS PASSED!${NC}"
    SUCCESS_RATE=100
else
    SUCCESS_RATE=$(echo "scale=1; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc 2>/dev/null || echo "0")
    echo -e "  ${YELLOW}⚠ Success Rate: ${SUCCESS_RATE}%${NC}"
fi

echo ""
echo "  📁 Resultados: $RESULTS_FILE"
echo "  📁 Métricas: $SCRIPT_DIR/results/"
echo ""

# Exit with appropriate code
if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi