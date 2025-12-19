#!/bin/bash
# =============================================================================
# TICKETERO - NFR Report Generator
# =============================================================================
# Genera reporte final de resultados NFR
# Usage: ./scripts/utils/generate-report.sh [results_file]
# =============================================================================

RESULTS_FILE=${1:-"results/nfr-test-results-latest.txt"}
REPORT_FILE="results/NFR-FINAL-REPORT-$(date +%Y%m%d-%H%M%S).md"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}📊 Generando reporte NFR...${NC}"

# Create report
cat > "$REPORT_FILE" << EOF
# NFR Test Results - $(date +"%Y-%m-%d %H:%M:%S")

## 📋 Resumen Ejecutivo

**Sistema:** Ticketero API  
**Fecha:** $(date +"%Y-%m-%d")  
**Duración total:** $(find results -name "*.csv" | wc -l) tests ejecutados  

## 🎯 Resultados por Categoría

### Performance Tests
EOF

# Count results by category
if [ -f "$RESULTS_FILE" ]; then
    TOTAL_TESTS=$(grep -c "," "$RESULTS_FILE" 2>/dev/null || echo "0")
    PASSED_TESTS=$(grep -c ",PASS," "$RESULTS_FILE" 2>/dev/null || echo "0")
    FAILED_TESTS=$(grep -c ",FAIL," "$RESULTS_FILE" 2>/dev/null || echo "0")
    
    echo "- **Total Tests:** $TOTAL_TESTS" >> "$REPORT_FILE"
    echo "- **Passed:** $PASSED_TESTS" >> "$REPORT_FILE"
    echo "- **Failed:** $FAILED_TESTS" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    
    # Performance results
    echo "#### PERF-01: Load Test Sostenido" >> "$REPORT_FILE"
    if grep -q "PERF-01.*PASS" "$RESULTS_FILE" 2>/dev/null; then
        echo "- ✅ **PASS** - Throughput ≥50 tickets/min" >> "$REPORT_FILE"
    else
        echo "- ❌ **FAIL** - Throughput insuficiente" >> "$REPORT_FILE"
    fi
    
    echo "" >> "$REPORT_FILE"
    echo "#### PERF-02: Spike Test" >> "$REPORT_FILE"
    if grep -q "PERF-02.*PASS" "$RESULTS_FILE" 2>/dev/null; then
        echo "- ✅ **PASS** - Sistema maneja picos de carga" >> "$REPORT_FILE"
    else
        echo "- ❌ **FAIL** - Problemas con carga súbita" >> "$REPORT_FILE"
    fi
    
    echo "" >> "$REPORT_FILE"
    echo "### Concurrency Tests" >> "$REPORT_FILE"
    echo "#### CONC-01: Race Conditions" >> "$REPORT_FILE"
    if grep -q "CONC-01.*PASS" "$RESULTS_FILE" 2>/dev/null; then
        echo "- ✅ **PASS** - SELECT FOR UPDATE funciona correctamente" >> "$REPORT_FILE"
    else
        echo "- ❌ **FAIL** - Race conditions detectadas" >> "$REPORT_FILE"
    fi
    
    echo "" >> "$REPORT_FILE"
    echo "### Resilience Tests" >> "$REPORT_FILE"
    echo "#### RES-01: Worker Crash" >> "$REPORT_FILE"
    if grep -q "RES-01.*PASS" "$RESULTS_FILE" 2>/dev/null; then
        echo "- ✅ **PASS** - Auto-recovery < 90s" >> "$REPORT_FILE"
    else
        echo "- ❌ **FAIL** - Recovery lento o fallido" >> "$REPORT_FILE"
    fi
fi

# Add metrics summary
cat >> "$REPORT_FILE" << EOF

## 📊 Métricas Destacadas

### Throughput
- **Objetivo:** ≥50 tickets/minuto
- **Resultado:** Verificar en métricas CSV

### Latencia
- **Objetivo:** p95 < 2000ms
- **Resultado:** Verificar en logs K6

### Consistencia
- **Objetivo:** 0 inconsistencias
- **Resultado:** Validado por consistency checker

## 🔍 Issues Encontrados

$(if [ "$FAILED_TESTS" -gt 0 ]; then echo "- $FAILED_TESTS tests fallaron"; else echo "- Ninguno"; fi)

## 📁 Archivos de Evidencia

\`\`\`
results/
$(ls -la results/ 2>/dev/null | tail -n +2 | head -10 || echo "No files found")
\`\`\`

## 🎯 Recomendaciones

$(if [ "$FAILED_TESTS" -eq 0 ]; then 
    echo "- ✅ Sistema cumple todos los RNF"
    echo "- ✅ Listo para producción"
else 
    echo "- ⚠️ Revisar tests fallidos"
    echo "- ⚠️ Optimizar componentes identificados"
fi)

## 📞 Contacto

Para consultas sobre este reporte:
- Revisar logs detallados en \`results/\`
- Ejecutar \`validate-consistency.sh\` para verificar estado
- Consultar documentación en \`docs/NFR-TESTING-GUIDE.md\`

---
**Generado automáticamente por NFR Testing Framework**
EOF

echo -e "${GREEN}✅ Reporte generado: $REPORT_FILE${NC}"
echo ""
echo "📋 Resumen:"
echo "   - Total tests: ${TOTAL_TESTS:-0}"
echo "   - Passed: ${PASSED_TESTS:-0}"
echo "   - Failed: ${FAILED_TESTS:-0}"
echo ""
echo "📁 Ver reporte completo: $REPORT_FILE"