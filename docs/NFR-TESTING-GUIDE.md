# NFR Testing Guide - Sistema Ticketero

## 📋 Resumen Ejecutivo

Framework de pruebas no funcionales para validar performance, concurrencia y resiliencia del sistema Ticketero.

**Cobertura:** 12 escenarios | **Tiempo estimado:** 6-8 horas

## 🎯 Requisitos No Funcionales

| ID | Requisito | Métrica | Umbral |
|----|-----------|---------|--------|
| RNF-01 | Throughput | Tickets procesados/minuto | ≥ 50 |
| RNF-02 | Latencia API | p95 response time | < 2 segundos |
| RNF-03 | Concurrencia | Race conditions | 0 detectadas |
| RNF-04 | Consistencia | Tickets inconsistentes | 0 |
| RNF-05 | Recovery Time | Detección worker muerto | < 90 segundos |
| RNF-06 | Disponibilidad | Uptime durante carga | 99.9% |
| RNF-07 | Recursos | Memory leak | 0 (estable 30 min) |

## 🚀 Quick Start

### Prerrequisitos

```bash
# Verificar Docker
docker --version

# Verificar sistema corriendo
docker ps | grep ticketero

# Verificar conectividad
curl http://localhost:8080/actuator/health
```

### Ejecución Rápida

```bash
# Todos los tests
bash run-nfr-tests.sh all

# Solo performance
bash run-nfr-tests.sh performance

# Solo concurrencia
bash run-nfr-tests.sh concurrency

# Solo resiliencia
bash run-nfr-tests.sh resilience
```

## 📊 Escenarios de Prueba

### PASO 1: Performance Tests

#### PERF-01: Load Test Sostenido
**Objetivo:** Validar throughput sostenido de 50+ tickets/minuto

```bash
bash scripts/performance/load-test.sh
```

**Setup:**
- Sistema limpio (DB sin tickets previos)
- 5 asesores AVAILABLE
- Duración: 2 minutos

**Criterios de Éxito:**
- ✅ Throughput: ≥ 50 tickets/minuto
- ✅ Latencia p95: < 2000ms
- ✅ Error rate: < 1%
- ✅ Sin deadlocks en BD

**Métricas Capturadas:**
- CPU/Memory de app y PostgreSQL
- Tickets por estado (WAITING, IN_PROGRESS, COMPLETED)
- Mensajes Outbox (PENDING, SENT, FAILED)
- Conexiones DB activas

#### PERF-02: Spike Test
**Objetivo:** Validar comportamiento bajo carga súbita

```bash
bash scripts/performance/spike-test.sh
```

**Escenario:**
- 50 tickets simultáneos en 10 segundos
- Validar que el sistema no colapsa
- Recovery time < 3 minutos

#### PERF-03: Soak Test (30 minutos)
**Objetivo:** Detectar memory leaks y degradación progresiva

```bash
bash scripts/performance/soak-test.sh 30
```

**Validaciones:**
- Memoria estable (incremento < 20%)
- Throughput constante
- Sin degradación de latencia

### PASO 2: Concurrency Tests

#### CONC-01: Race Condition en Asignación de Asesor
**Objetivo:** Validar que SELECT FOR UPDATE previene race conditions

```bash
bash scripts/concurrency/race-condition-test.sh
```

**Setup:**
- 1 solo asesor AVAILABLE
- 5 tickets WAITING simultáneos
- Validar que solo 1 obtiene el asesor

**Criterios de Éxito:**
- ✅ 0 asignaciones dobles
- ✅ 0 deadlocks PostgreSQL
- ✅ Procesamiento serializado correcto

#### CONC-02: Idempotencia
**Objetivo:** Validar que mensajes duplicados no causan reprocesamiento

```bash
bash scripts/concurrency/idempotency-test.sh
```

**Escenario:**
- Ticket procesado completamente
- Simular redelivery del mensaje
- Validar que no se duplica el procesamiento

#### CONC-03: Outbox Concurrency
**Objetivo:** Validar patrón Outbox bajo carga alta

```bash
bash scripts/concurrency/outbox-concurrency-test.sh
```

**Validaciones:**
- 100 tickets simultáneos
- 100% mensajes enviados
- 0 mensajes perdidos o duplicados

### PASO 3: Resilience Tests

#### RES-01: Worker Crash (Heartbeat Timeout)
**Objetivo:** Validar auto-recovery de workers muertos

```bash
bash scripts/resilience/worker-crash-test.sh
```

**Escenario:**
- Worker procesando ticket
- Simular crash (detener heartbeat)
- Validar detección < 90s
- Validar asesor liberado y ticket reencolado

**Criterios de Éxito:**
- ✅ Detección en < 90 segundos
- ✅ Asesor liberado correctamente
- ✅ Ticket reencolado sin pérdida

#### RES-02: Graceful Shutdown
**Objetivo:** Validar shutdown sin pérdida de datos

```bash
bash scripts/resilience/graceful-shutdown-test.sh
```

**Validaciones:**
- Tickets en proceso preservados
- Asesores liberados correctamente
- Restart time < 60s

## 🛠️ Utilidades

### Metrics Collector

Recolecta métricas del sistema cada 5 segundos:

```bash
bash scripts/utils/metrics-collector.sh 120 results/metrics.csv
```

**Métricas capturadas:**
- CPU/Memory (App, PostgreSQL)
- Tickets por estado
- Mensajes Outbox
- Conexiones DB

### Consistency Validator

Valida consistencia del sistema:

```bash
bash scripts/utils/validate-consistency.sh
```

**Validaciones:**
- Tickets en estado inconsistente
- Asesores BUSY sin ticket activo
- Mensajes Outbox fallidos
- Tickets duplicados
- Conexiones DB

## 📈 Análisis de Resultados

### Archivos Generados

```
results/
├── load-test-metrics-YYYYMMDD-HHMMSS.csv
├── load-test-summary.json
├── nfr-test-results-YYYYMMDD-HHMMSS.txt
└── *.log
```

### Formato CSV de Métricas

```csv
timestamp,cpu_app,mem_app_mb,cpu_postgres,mem_postgres_mb,tickets_waiting,tickets_completed,outbox_pending,outbox_failed
2024-01-15 10:00:00,45.2,512,12.3,256,5,95,0,0
```

### Análisis con Excel/Python

```python
import pandas as pd

# Cargar métricas
df = pd.read_csv('results/load-test-metrics.csv')

# Analizar memoria
print(f"Memoria inicial: {df['mem_app_mb'].iloc[0]}MB")
print(f"Memoria final: {df['mem_app_mb'].iloc[-1]}MB")
print(f"Incremento: {df['mem_app_mb'].iloc[-1] - df['mem_app_mb'].iloc[0]}MB")

# Analizar throughput
df['timestamp'] = pd.to_datetime(df['timestamp'])
df['tickets_per_min'] = df['tickets_completed'].diff() / (df['timestamp'].diff().dt.seconds / 60)
print(f"Throughput promedio: {df['tickets_per_min'].mean():.1f} tickets/min")
```

## 🔧 Troubleshooting

### Error: Docker containers not running

```bash
# Verificar estado
docker ps -a | grep ticketero

# Reiniciar servicios
docker-compose down
docker-compose up -d

# Verificar logs
docker logs ticketero-api
docker logs ticketero-db
```

### Error: Database connection refused

```bash
# Verificar PostgreSQL
docker exec ticketero-db psql -U dev -d ticketero -c "SELECT 1;"

# Verificar conexiones
docker exec ticketero-db psql -U dev -d ticketero -c \
  "SELECT count(*) FROM pg_stat_activity WHERE datname='ticketero';"
```

### Error: Tests failing consistently

```bash
# Limpiar estado completo
docker exec ticketero-db psql -U dev -d ticketero -c "
  DELETE FROM ticket_event;
  DELETE FROM outbox_message;
  DELETE FROM ticket;
  UPDATE advisor SET status = 'AVAILABLE', total_tickets_served = 0;
"

# Reiniciar aplicación
docker restart ticketero-api

# Esperar que esté disponible
until curl -s http://localhost:8080/actuator/health | grep -q "UP"; do
  echo "Waiting for app..."
  sleep 5
done
```

## 📊 Dashboard de Métricas

### Métricas Clave por Test

| Test | Throughput | Latencia p95 | Error Rate | Consistencia |
|------|-----------|--------------|------------|--------------|
| PERF-01 | 52 t/min | 1850ms | 0.2% | ✅ PASS |
| PERF-02 | N/A | 2100ms | 0% | ✅ PASS |
| CONC-01 | N/A | N/A | 0% | ✅ PASS |
| RES-01 | N/A | N/A | 0% | ✅ PASS |

### Umbrales de Alerta

| Métrica | Warning | Critical |
|---------|---------|----------|
| CPU App | > 70% | > 85% |
| Memory App | > 1GB | > 1.5GB |
| DB Connections | > 15 | > 20 |
| Outbox FAILED | > 0 | > 5 |
| Latencia p95 | > 1500ms | > 2000ms |

## 🎯 Checklist de Ejecución

### Pre-Test
- [ ] Docker containers running
- [ ] Database limpia (o estado conocido)
- [ ] Asesores en estado AVAILABLE
- [ ] Actuator health endpoint responde UP
- [ ] Espacio en disco para logs/métricas

### Durante Test
- [ ] Monitorear logs en tiempo real
- [ ] Verificar métricas cada 30s
- [ ] Anotar cualquier anomalía
- [ ] Capturar screenshots si hay errores

### Post-Test
- [ ] Ejecutar validate-consistency.sh
- [ ] Revisar archivos de métricas
- [ ] Analizar logs de errores
- [ ] Documentar resultados
- [ ] Limpiar estado para siguiente test

## 📝 Reporte de Resultados

### Template de Reporte

```markdown
# NFR Test Results - [Fecha]

## Resumen Ejecutivo
- Total Tests: X
- Passed: Y
- Failed: Z
- Success Rate: XX%

## Resultados por Categoría

### Performance
- PERF-01: ✅ PASS (Throughput: 52 t/min)
- PERF-02: ✅ PASS (Recovery: 45s)

### Concurrency
- CONC-01: ✅ PASS (0 race conditions)

### Resilience
- RES-01: ✅ PASS (Detection: 75s)

## Métricas Destacadas
- Throughput promedio: 52 tickets/min
- Latencia p95: 1850ms
- Memory leak: No detectado
- Consistencia: 100%

## Issues Encontrados
- Ninguno

## Recomendaciones
- Sistema cumple todos los RNF
- Listo para producción
```

## 🔗 Referencias

- [Spring Boot Performance Testing](https://spring.io/guides/gs/testing-web/)
- [K6 Documentation](https://k6.io/docs/)
- [PostgreSQL Performance Tips](https://wiki.postgresql.org/wiki/Performance_Optimization)
- [Docker Performance Best Practices](https://docs.docker.com/config/containers/resource_constraints/)

## 📞 Soporte

Para issues o preguntas:
1. Revisar logs: `docker logs ticketero-api`
2. Verificar consistencia: `bash scripts/utils/validate-consistency.sh`
3. Consultar documentación técnica en `/docs`

---

**Versión:** 1.0  
**Última actualización:** 2024-01-15  
**Autor:** Performance Engineering Team