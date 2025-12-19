#!/bin/bash
# =============================================================================
# TICKETERO - NFR Framework Demo
# =============================================================================
# Demonstrates the NFR testing framework structure and capabilities
# Usage: ./demo-nfr-framework.sh
# =============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║           TICKETERO - NFR TESTING FRAMEWORK DEMO              ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Show framework structure
echo -e "${CYAN}📁 ESTRUCTURA DEL FRAMEWORK:${NC}"
echo ""
echo "ticketero/"
echo "├── scripts/"
echo "│   ├── performance/          # Tests de carga y rendimiento"
echo "│   │   ├── load-test.sh      # PERF-01: Load test sostenido"
echo "│   │   ├── spike-test.sh     # PERF-02: Spike test"
echo "│   │   └── soak-test.sh      # PERF-03: Soak test (30 min)"
echo "│   ├── concurrency/          # Tests de concurrencia"
echo "│   │   ├── race-condition-test.sh    # CONC-01: Race conditions"
echo "│   │   ├── idempotency-test.sh       # CONC-02: Idempotencia"
echo "│   │   └── outbox-concurrency-test.sh # CONC-03: Outbox pattern"
echo "│   ├── resilience/           # Tests de resiliencia"
echo "│   │   ├── worker-crash-test.sh      # RES-01: Worker crash"
echo "│   │   ├── rabbitmq-failure-test.sh  # RES-02: RabbitMQ failure"
echo "│   │   └── graceful-shutdown-test.sh # RES-03: Graceful shutdown"
echo "│   └── utils/                # Utilidades"
echo "│       ├── metrics-collector.sh     # Recolector de métricas"
echo "│       └── validate-consistency.sh  # Validador de consistencia"
echo "├── k6/                       # Scripts K6 para load testing"
echo "│   └── load-test.js          # Script K6 con métricas custom"
echo "├── results/                  # Resultados y métricas"
echo "├── docs/"
echo "│   └── NFR-TESTING-GUIDE.md  # Documentación completa"
echo "├── run-nfr-tests.sh         # Runner principal (Linux/Mac)"
echo "└── run-nfr-tests.bat        # Runner principal (Windows)"
echo ""

# Show test categories
echo -e "${CYAN}🎯 CATEGORÍAS DE PRUEBAS:${NC}"
echo ""
echo -e "${YELLOW}1. PERFORMANCE TESTS${NC}"
echo "   • PERF-01: Load Test Sostenido (≥50 tickets/min)"
echo "   • PERF-02: Spike Test (50 tickets en 10s)"
echo "   • PERF-03: Soak Test (30 min, memory leak detection)"
echo ""
echo -e "${YELLOW}2. CONCURRENCY TESTS${NC}"
echo "   • CONC-01: Race Condition (SELECT FOR UPDATE validation)"
echo "   • CONC-02: Idempotency (message redelivery handling)"
echo "   • CONC-03: Outbox Concurrency (100 simultaneous messages)"
echo ""
echo -e "${YELLOW}3. RESILIENCE TESTS${NC}"
echo "   • RES-01: Worker Crash (heartbeat timeout <90s)"
echo "   • RES-02: RabbitMQ Failure (outbox pattern validation)"
echo "   • RES-03: Graceful Shutdown (zero data loss)"
echo ""

# Show NFR requirements
echo -e "${CYAN}📊 REQUISITOS NO FUNCIONALES:${NC}"
echo ""
echo "┌─────────────────────────────────────────────────────────────┐"
echo "│ ID     │ Requisito           │ Métrica              │ Umbral │"
echo "├─────────────────────────────────────────────────────────────┤"
echo "│ RNF-01 │ Throughput          │ Tickets/minuto       │ ≥ 50   │"
echo "│ RNF-02 │ Latencia API        │ p95 response time    │ < 2s   │"
echo "│ RNF-03 │ Concurrencia        │ Race conditions      │ 0      │"
echo "│ RNF-04 │ Consistencia        │ Tickets inconsist.   │ 0      │"
echo "│ RNF-05 │ Recovery Time       │ Worker death detect. │ < 90s  │"
echo "│ RNF-06 │ Disponibilidad      │ Uptime durante carga │ 99.9%  │"
echo "│ RNF-07 │ Recursos            │ Memory leak          │ 0      │"
echo "└─────────────────────────────────────────────────────────────┘"
echo ""

# Show usage examples
echo -e "${CYAN}🚀 EJEMPLOS DE USO:${NC}"
echo ""
echo -e "${GREEN}# Ejecutar todos los tests${NC}"
echo "bash run-nfr-tests.sh all"
echo ""
echo -e "${GREEN}# Solo tests de performance${NC}"
echo "bash run-nfr-tests.sh performance"
echo ""
echo -e "${GREEN}# Test específico${NC}"
echo "bash scripts/performance/load-test.sh"
echo ""
echo -e "${GREEN}# Validar consistencia${NC}"
echo "bash scripts/utils/validate-consistency.sh"
echo ""
echo -e "${GREEN}# Recolectar métricas (60 segundos)${NC}"
echo "bash scripts/utils/metrics-collector.sh 60 metrics.csv"
echo ""

# Show file verification
echo -e "${CYAN}✅ VERIFICACIÓN DE ARCHIVOS:${NC}"
echo ""

files=(
    "scripts/performance/load-test.sh"
    "scripts/concurrency/race-condition-test.sh"
    "scripts/resilience/worker-crash-test.sh"
    "scripts/utils/metrics-collector.sh"
    "scripts/utils/validate-consistency.sh"
    "k6/load-test.js"
    "docs/NFR-TESTING-GUIDE.md"
    "run-nfr-tests.sh"
    "run-nfr-tests.bat"
)

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo -e "   ✅ $file"
    else
        echo -e "   ❌ $file ${RED}(missing)${NC}"
    fi
done

echo ""

# Show next steps
echo -e "${CYAN}📋 PRÓXIMOS PASOS:${NC}"
echo ""
echo "1. Verificar que Docker esté corriendo:"
echo "   docker ps | grep ticketero"
echo ""
echo "2. Verificar conectividad de la API:"
echo "   bash scripts/utils/test-api-connectivity.sh"
echo ""
echo "3. Ejecutar test de conectividad básica:"
echo "   curl http://localhost:8080/actuator/health"
echo ""
echo "4. Ejecutar primer test NFR:"
echo "   bash scripts/performance/load-test.sh"
echo ""
echo "5. Revisar documentación completa:"
echo "   cat docs/NFR-TESTING-GUIDE.md"
echo ""

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  FRAMEWORK NFR LISTO - Documentación en docs/NFR-TESTING-GUIDE.md  ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"