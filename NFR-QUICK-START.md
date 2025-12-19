# NFR Testing - Quick Start Guide

## 🚀 Ejecución Rápida (5 minutos)

### 1. Verificar Prerrequisitos

```bash
# Verificar Docker
docker ps | grep ticketero

# Verificar API
curl http://localhost:8080/actuator/health
```

### 2. Ejecutar Tests Básicos

```bash
# Test de conectividad (30 segundos)
bash scripts/utils/test-api-connectivity.sh

# Load test básico (2 minutos)
bash scripts/performance/load-test.sh

# Validar consistencia
bash scripts/utils/validate-consistency.sh
```

### 3. Suite Completa

```bash
# Todos los tests (~30 minutos)
bash run-nfr-tests.sh all

# Solo performance (~10 minutos)
bash run-nfr-tests.sh performance

# Solo concurrencia (~5 minutos)
bash run-nfr-tests.sh concurrency

# Solo resiliencia (~5 minutos)
bash run-nfr-tests.sh resilience
```

## 📊 Tests Disponibles

| ID | Test | Duración | Comando |
|----|------|----------|---------|
| PERF-01 | Load Test | 2 min | `bash scripts/performance/load-test.sh` |
| PERF-02 | Spike Test | 1 min | `bash scripts/performance/spike-test.sh` |
| PERF-03 | Soak Test | 30 min | `bash scripts/performance/soak-test.sh` |
| CONC-01 | Race Condition | 1 min | `bash scripts/concurrency/race-condition-test.sh` |
| CONC-02 | Idempotency | 1 min | `bash scripts/concurrency/idempotency-test.sh` |
| CONC-03 | Outbox Concurrency | 2 min | `bash scripts/concurrency/outbox-concurrency-test.sh` |
| RES-01 | Worker Crash | 3 min | `bash scripts/resilience/worker-crash-test.sh` |
| RES-02 | Graceful Shutdown | 2 min | `bash scripts/resilience/graceful-shutdown-test.sh` |

## 🎯 Umbrales de Éxito

| Métrica | Umbral | Test |
|---------|--------|------|
| Throughput | ≥50 tickets/min | PERF-01 |
| Latencia p95 | <2000ms | PERF-01 |
| Race conditions | 0 | CONC-01 |
| Recovery time | <90s | RES-01 |
| Data loss | 0 | RES-02 |
| Memory leak | <20% increase | PERF-03 |

## 📁 Resultados

```bash
# Ver resultados
ls -la results/

# Generar reporte
bash scripts/utils/generate-report.sh

# Analizar métricas
cat results/load-test-metrics-*.csv
```

## 🔧 Troubleshooting

### Docker no está corriendo
```bash
docker-compose up -d
```

### API no responde
```bash
docker logs ticketero-api
docker restart ticketero-api
```

### Limpiar estado
```bash
docker exec ticketero-db psql -U dev -d ticketero -c "
  DELETE FROM ticket_event;
  DELETE FROM outbox_message;
  DELETE FROM ticket;
  UPDATE advisor SET status = 'AVAILABLE';
"
```

## 📖 Documentación Completa

Ver `docs/NFR-TESTING-GUIDE.md` para documentación exhaustiva.

## ✅ Checklist Pre-Test

- [ ] Docker containers running
- [ ] API health endpoint UP
- [ ] Database accessible
- [ ] Disk space available (>1GB)
- [ ] No tests running concurrently

## 🎉 Demo

```bash
# Ver estructura del framework
bash demo-nfr-framework.sh
```