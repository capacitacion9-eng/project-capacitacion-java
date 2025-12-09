# Requerimientos Funcionales - Sistema Ticketero Digital

**Proyecto:** Sistema de Gestión de Tickets con Notificaciones en Tiempo Real  
**Cliente:** Institución Financiera  
**Versión:** 1.0  
**Fecha:** Diciembre 2025  
**Analista:** Analista de Negocio Senior

---

## 1. Introducción

### 1.1 Propósito

Este documento especifica los requerimientos funcionales del Sistema Ticketero Digital, diseñado para modernizar la experiencia de atención en sucursales mediante:

- Digitalización completa del proceso de tickets
- Notificaciones automáticas en tiempo real vía Telegram
- Movilidad del cliente durante la espera
- Asignación inteligente de clientes a ejecutivos
- Panel de monitoreo para supervisión operacional

### 1.2 Alcance

Este documento cubre:

- ✅ 8 Requerimientos Funcionales (RF-001 a RF-008)
- ✅ 13 Reglas de Negocio (RN-001 a RN-013)
- ✅ Criterios de aceptación en formato Gherkin
- ✅ Modelo de datos funcional
- ✅ Matriz de trazabilidad

Este documento NO cubre:

- ❌ Arquitectura técnica (ver documento ARQUITECTURA.md)
- ❌ Tecnologías de implementación
- ❌ Diseño de interfaces de usuario

### 1.3 Definiciones

| Término | Definición |
|---------|------------|
| Ticket | Turno digital asignado a un cliente para ser atendido |
| Cola | Fila virtual de tickets esperando atención |
| Asesor | Ejecutivo bancario que atiende clientes |
| Módulo | Estación de trabajo de un asesor (numerados 1-5) |
| Chat ID | Identificador único de usuario en Telegram |
| UUID | Identificador único universal para tickets |

---

## 2. Reglas de Negocio

Las siguientes reglas de negocio aplican transversalmente a todos los requerimientos funcionales:

**RN-001: Unicidad de Ticket Activo**  
Un cliente solo puede tener 1 ticket activo a la vez. Los estados activos son: EN_ESPERA, PROXIMO, ATENDIENDO. Si un cliente intenta crear un nuevo ticket teniendo uno activo, el sistema debe rechazar la solicitud con error HTTP 409 Conflict.

**RN-002: Prioridad de Colas**  
Las colas tienen prioridades numéricas para asignación automática:
- GERENCIA: prioridad 4 (máxima)
- EMPRESAS: prioridad 3
- PERSONAL_BANKER: prioridad 2
- CAJA: prioridad 1 (mínima)

Cuando un asesor se libera, el sistema asigna primero tickets de colas con mayor prioridad.

**RN-003: Orden FIFO Dentro de Cola**  
Dentro de una misma cola, los tickets se procesan en orden FIFO (First In, First Out). El ticket más antiguo (createdAt menor) se asigna primero.

**RN-004: Balanceo de Carga Entre Asesores**  
Al asignar un ticket, el sistema selecciona el asesor AVAILABLE con menor valor de assignedTicketsCount, distribuyendo equitativamente la carga de trabajo.

**RN-005: Formato de Número de Ticket**  
El número de ticket sigue el formato: [Prefijo][Número secuencial 01-99]
- Prefijo: 1 letra según el tipo de cola
- Número: 2 dígitos, del 01 al 99, reseteado diariamente

Ejemplos: C01, P15, E03, G02

**RN-006: Prefijos por Tipo de Cola**  
- CAJA → C
- PERSONAL_BANKER → P
- EMPRESAS → E
- GERENCIA → G

**RN-007: Reintentos Automáticos de Mensajes**  
Si el envío de un mensaje a Telegram falla, el sistema reintenta automáticamente hasta 3 veces antes de marcarlo como FALLIDO.

**RN-008: Backoff Exponencial en Reintentos**  
Los reintentos de mensajes usan backoff exponencial:
- Intento 1: inmediato
- Intento 2: después de 30 segundos
- Intento 3: después de 60 segundos
- Intento 4: después de 120 segundos

**RN-009: Estados de Ticket**  
Un ticket puede estar en uno de estos estados:
- EN_ESPERA: esperando asignación a asesor
- PROXIMO: próximo a ser atendido (posición ≤ 3)
- ATENDIENDO: siendo atendido por un asesor
- COMPLETADO: atención finalizada exitosamente
- CANCELADO: cancelado por cliente o sistema
- NO_ATENDIDO: cliente no se presentó cuando fue llamado

**RN-010: Cálculo de Tiempo Estimado**  
El tiempo estimado de espera se calcula como:

```
tiempoEstimado = posiciónEnCola × tiempoPromedioCola
```

Donde tiempoPromedioCola varía por tipo:
- CAJA: 5 minutos
- PERSONAL_BANKER: 15 minutos
- EMPRESAS: 20 minutos
- GERENCIA: 30 minutos

**RN-011: Auditoría Obligatoria**  
Todos los eventos críticos del sistema deben registrarse en auditoría con: timestamp, tipo de evento, actor involucrado, entityId afectado, y cambios de estado.

**RN-012: Umbral de Pre-aviso**  
El sistema envía el Mensaje 2 (pre-aviso) cuando la posición del ticket es ≤ 3, indicando que el cliente debe acercarse a la sucursal.

**RN-013: Estados de Asesor**  
Un asesor puede estar en uno de estos estados:
- AVAILABLE: disponible para recibir asignaciones
- BUSY: atendiendo un cliente (no recibe nuevas asignaciones)
- OFFLINE: no disponible (almuerzo, capacitación, etc.)

---

## 3. Enumeraciones

### 3.1 QueueType

Tipos de cola disponibles en el sistema:

| Valor | Display Name | Tiempo Promedio | Prioridad | Prefijo |
|-------|--------------|-----------------|-----------|---------|
| CAJA | Caja | 5 min | 1 | C |
| PERSONAL_BANKER | Personal Banker | 15 min | 2 | P |
| EMPRESAS | Empresas | 20 min | 3 | E |
| GERENCIA | Gerencia | 30 min | 4 | G |

### 3.2 TicketStatus

Estados posibles de un ticket:

| Valor | Descripción | Es Activo? |
|-------|-------------|------------|
| EN_ESPERA | Esperando asignación | Sí |
| PROXIMO | Próximo a ser atendido | Sí |
| ATENDIENDO | Siendo atendido | Sí |
| COMPLETADO | Atención finalizada | No |
| CANCELADO | Cancelado | No |
| NO_ATENDIDO | Cliente no se presentó | No |

### 3.3 AdvisorStatus

Estados posibles de un asesor:

| Valor | Descripción | Recibe Asignaciones? |
|-------|-------------|----------------------|
| AVAILABLE | Disponible | Sí |
| BUSY | Atendiendo cliente | No |
| OFFLINE | No disponible | No |

### 3.4 MessageTemplate

Plantillas de mensajes para Telegram:

| Valor | Descripción | Momento de Envío |
|-------|-------------|------------------|
| totem_ticket_creado | Confirmación de creación | Inmediato al crear ticket |
| totem_proximo_turno | Pre-aviso | Cuando posición ≤ 3 |
| totem_es_tu_turno | Turno activo | Al asignar a asesor |

---

## 4. Requerimientos Funcionales

### RF-001: Crear Ticket Digital

**Descripción:** El sistema debe permitir al cliente crear un ticket digital para ser atendido en sucursal, ingresando su identificación nacional (RUT/ID), número de teléfono y seleccionando el tipo de atención requerida. El sistema generará un número único de ticket, calculará la posición actual en cola y el tiempo estimado de espera basado en datos reales de la operación.

**Prioridad:** Alta

**Actor Principal:** Cliente

**Precondiciones:**

- Terminal de autoservicio disponible y funcional
- Sistema de gestión de colas operativo
- Conexión a base de datos activa

**Modelo de Datos (Campos del Ticket):**

- codigoReferencia: UUID único (ej: "a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6")
- numero: String formato específico por cola (ej: "C01", "P15", "E03", "G02")
- nationalId: String, identificación nacional del cliente
- telefono: String, número de teléfono para Telegram
- branchOffice: String, nombre de la sucursal
- queueType: Enum (CAJA, PERSONAL_BANKER, EMPRESAS, GERENCIA)
- status: Enum (EN_ESPERA, PROXIMO, ATENDIENDO, COMPLETADO, CANCELADO, NO_ATENDIDO)
- positionInQueue: Integer, posición actual en cola (calculada en tiempo real)
- estimatedWaitMinutes: Integer, minutos estimados de espera
- createdAt: Timestamp, fecha/hora de creación
- assignedAdvisor: Relación a entidad Advisor (null inicialmente)
- assignedModuleNumber: Integer 1-5 (null inicialmente)

**Reglas de Negocio Aplicables:**

- RN-001: Un cliente solo puede tener 1 ticket activo a la vez
- RN-005: Número de ticket formato: [Prefijo][Número secuencial 01-99]
- RN-006: Prefijos por cola: C=Caja, P=Personal Banker, E=Empresas, G=Gerencia
- RN-010: Cálculo de tiempo estimado: posiciónEnCola × tiempoPromedioCola

**Criterios de Aceptación (Gherkin):**

**Escenario 1: Creación exitosa de ticket para cola de Caja**

```gherkin
Given el cliente con nationalId "12345678-9" no tiene tickets activos
And el terminal está en pantalla de selección de servicio
When el cliente ingresa:
  | Campo        | Valor           |
  | nationalId   | 12345678-9      |
  | telefono     | +56912345678    |
  | branchOffice | Sucursal Centro |
  | queueType    | CAJA            |
Then el sistema genera un ticket con:
  | Campo                 | Valor Esperado                    |
  | codigoReferencia      | UUID válido                       |
  | numero                | "C[01-99]"                        |
  | status                | EN_ESPERA                         |
  | positionInQueue       | Número > 0                        |
  | estimatedWaitMinutes  | positionInQueue × 5               |
  | assignedAdvisor       | null                              |
  | assignedModuleNumber  | null                              |
And el sistema almacena el ticket en base de datos
And el sistema programa 3 mensajes de Telegram
And el sistema retorna HTTP 201 con JSON:
  {
    "identificador": "a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6",
    "numero": "C01",
    "positionInQueue": 5,
    "estimatedWaitMinutes": 25,
    "queueType": "CAJA"
  }
```

**Escenario 2: Error - Cliente ya tiene ticket activo**

```gherkin
Given el cliente con nationalId "12345678-9" tiene un ticket activo:
  | numero | status     | queueType       |
  | P05    | EN_ESPERA  | PERSONAL_BANKER |
When el cliente intenta crear un nuevo ticket con queueType CAJA
Then el sistema rechaza la creación
And el sistema retorna HTTP 409 Conflict con JSON:
  {
    "error": "TICKET_ACTIVO_EXISTENTE",
    "mensaje": "Ya tienes un ticket activo: P05",
    "ticketActivo": {
      "numero": "P05",
      "positionInQueue": 3,
      "estimatedWaitMinutes": 45
    }
  }
And el sistema NO crea un nuevo ticket
```

**Escenario 3: Validación - RUT/ID inválido**

```gherkin
Given el terminal está en pantalla de ingreso de datos
When el cliente ingresa nationalId vacío
Then el sistema retorna HTTP 400 Bad Request con JSON:
  {
    "error": "VALIDACION_FALLIDA",
    "campos": {
      "nationalId": "El RUT/ID es obligatorio"
    }
  }
And el sistema NO crea el ticket
```

**Escenario 4: Validación - Teléfono en formato inválido**

```gherkin
Given el terminal está en pantalla de ingreso de datos
When el cliente ingresa telefono "123"
Then el sistema retorna HTTP 400 Bad Request
And el mensaje de error especifica formato requerido "+56XXXXXXXXX"
```

**Escenario 5: Cálculo de posición - Primera persona en cola**

```gherkin
Given la cola de tipo PERSONAL_BANKER está vacía
When el cliente crea un ticket para PERSONAL_BANKER
Then el sistema calcula positionInQueue = 1
And estimatedWaitMinutes = 15
And el número de ticket es "P01"
```

**Escenario 6: Cálculo de posición - Cola con tickets existentes**

```gherkin
Given la cola de tipo EMPRESAS tiene 4 tickets EN_ESPERA
When el cliente crea un nuevo ticket para EMPRESAS
Then el sistema calcula positionInQueue = 5
And estimatedWaitMinutes = 100
And el cálculo es: 5 × 20min = 100min
```

**Escenario 7: Creación sin teléfono (cliente no quiere notificaciones)**

```gherkin
Given el cliente no proporciona número de teléfono
When el cliente crea un ticket
Then el sistema crea el ticket exitosamente
And el sistema NO programa mensajes de Telegram
```

**Postcondiciones:**

- Ticket almacenado en base de datos con estado EN_ESPERA
- 3 mensajes programados (si hay teléfono)
- Evento de auditoría registrado: "TICKET_CREADO"

**Endpoints HTTP:**

- `POST /api/tickets` - Crear nuevo ticket

---

### RF-002: Enviar Notificaciones Automáticas vía Telegram

**Descripción:** El sistema debe enviar automáticamente tres tipos de mensajes vía Telegram al cliente durante el ciclo de vida de su ticket: (1) confirmación inmediata al crear el ticket, (2) pre-aviso cuando quedan 3 personas adelante, y (3) notificación de turno activo al ser asignado a un ejecutivo. Los mensajes deben incluir información relevante y actualizada del estado del ticket.

**Prioridad:** Alta

**Actor Principal:** Sistema (automatizado)

**Precondiciones:**

- Ticket creado con teléfono válido
- Telegram Bot configurado y activo
- Cliente tiene cuenta de Telegram vinculada al número proporcionado

**Modelo de Datos (Entidad Mensaje):**

- id: BIGSERIAL (primary key)
- ticket_id: BIGINT (foreign key a ticket)
- plantilla: String (totem_ticket_creado, totem_proximo_turno, totem_es_tu_turno)
- estadoEnvio: Enum (PENDIENTE, ENVIADO, FALLIDO)
- fechaProgramada: Timestamp
- fechaEnvio: Timestamp (nullable)
- telegramMessageId: String (nullable, retornado por Telegram API)
- intentos: Integer (contador de reintentos, default 0)

**Plantillas de Mensajes:**

**1. totem_ticket_creado:**
```
✅ <b>Ticket Creado</b>

Tu número de turno: <b>{numero}</b>
Posición en cola: <b>#{posicion}</b>
Tiempo estimado: <b>{tiempo} minutos</b>

Te notificaremos cuando estés próximo.
```

**2. totem_proximo_turno:**
```
⏰ <b>¡Pronto será tu turno!</b>

Turno: <b>{numero}</b>
Faltan aproximadamente 3 turnos.

Por favor, acércate a la sucursal.
```

**3. totem_es_tu_turno:**
```
🔔 <b>¡ES TU TURNO {numero}!</b>

Dirígete al módulo: <b>{modulo}</b>
Asesor: <b>{nombreAsesor}</b>
```

**Reglas de Negocio Aplicables:**

- RN-007: 3 reintentos automáticos antes de marcar como FALLIDO
- RN-008: Backoff exponencial (30s, 60s, 120s)
- RN-011: Auditoría de envíos
- RN-012: Mensaje 2 se envía cuando posición ≤ 3

**Criterios de Aceptación (Gherkin):**

**Escenario 1: Envío exitoso del Mensaje 1 (Confirmación)**

```gherkin
Given un ticket fue creado con:
  | codigoReferencia | numero | telefono     | positionInQueue | estimatedWaitMinutes |
  | uuid-123         | C05    | +56912345678 | 5               | 25                   |
When el sistema programa el Mensaje 1 (totem_ticket_creado)
Then el sistema crea un registro de mensaje con:
  | plantilla            | estadoEnvio | intentos |
  | totem_ticket_creado  | PENDIENTE   | 0        |
When el scheduler ejecuta el envío
Then el sistema invoca Telegram Bot API con el mensaje formateado
And Telegram API retorna success con messageId "TG-789"
Then el sistema actualiza el mensaje:
  | estadoEnvio | telegramMessageId | fechaEnvio        | intentos |
  | ENVIADO     | TG-789            | 2025-12-15 10:30  | 1        |
And el sistema registra auditoría: "MENSAJE_ENVIADO"
```

**Escenario 2: Envío exitoso del Mensaje 2 (Pre-aviso)**

```gherkin
Given un ticket con numero "P08" tiene positionInQueue = 4
When el sistema recalcula posiciones y positionInQueue cambia a 3
Then el sistema detecta umbral de pre-aviso (posición ≤ 3)
And el sistema programa Mensaje 2 (totem_proximo_turno)
When el scheduler ejecuta el envío
Then el mensaje contiene:
  """
  ⏰ ¡Pronto será tu turno!
  Turno: P08
  Faltan aproximadamente 3 turnos.
  Por favor, acércate a la sucursal.
  """
And el sistema marca el mensaje como ENVIADO
```

**Escenario 3: Envío exitoso del Mensaje 3 (Turno Activo)**

```gherkin
Given un ticket "E03" es asignado a:
  | assignedAdvisor | assignedModuleNumber |
  | Juan Pérez      | 2                    |
When el sistema programa Mensaje 3 (totem_es_tu_turno)
Then el mensaje contiene:
  """
  🔔 ¡ES TU TURNO E03!
  Dirígete al módulo: 2
  Asesor: Juan Pérez
  """
And el sistema envía el mensaje inmediatamente
And el estadoEnvio es ENVIADO
```

**Escenario 4: Fallo de red en primer intento, éxito en segundo**

```gherkin
Given un mensaje PENDIENTE con intentos = 0
When el scheduler ejecuta el envío
And Telegram API retorna error de red (timeout)
Then el sistema actualiza:
  | estadoEnvio | intentos |
  | PENDIENTE   | 1        |
And el sistema programa reintento en 30 segundos (RN-008)
When transcurren 30 segundos
And el scheduler reintenta el envío
And Telegram API retorna success
Then el sistema actualiza:
  | estadoEnvio | intentos |
  | ENVIADO     | 2        |
```

**Escenario 5: 3 reintentos fallidos → estado FALLIDO**

```gherkin
Given un mensaje PENDIENTE con intentos = 0
When el intento 1 falla (error de red)
Then intentos = 1, espera 30s
When el intento 2 falla (error de red)
Then intentos = 2, espera 60s
When el intento 3 falla (error de red)
Then intentos = 3, espera 120s
When el intento 4 falla (error de red)
Then el sistema actualiza:
  | estadoEnvio | intentos |
  | FALLIDO     | 4        |
And el sistema registra auditoría: "MENSAJE_FALLIDO"
And el sistema NO programa más reintentos
```

**Escenario 6: Backoff exponencial entre reintentos**

```gherkin
Given un mensaje con estadoEnvio = PENDIENTE
When el intento 1 falla a las 10:00:00
Then el sistema programa reintento 2 para las 10:00:30 (30s después)
When el intento 2 falla a las 10:00:30
Then el sistema programa reintento 3 para las 10:01:30 (60s después)
When el intento 3 falla a las 10:01:30
Then el sistema programa reintento 4 para las 10:03:30 (120s después)
```

**Escenario 7: Cliente sin teléfono, no se programan mensajes**

```gherkin
Given un ticket fue creado sin teléfono:
  | codigoReferencia | numero | telefono | positionInQueue |
  | uuid-456         | G01    | null     | 1               |
When el sistema intenta programar mensajes
Then el sistema NO crea registros de mensaje
And el sistema continúa el flujo normalmente
```

**Postcondiciones:**

- Mensaje insertado en BD con estado según resultado (ENVIADO/FALLIDO)
- telegram_message_id almacenado si éxito
- Intentos incrementado en cada reintento
- Auditoría registrada para cada envío

**Endpoints HTTP:**

- Ninguno (proceso interno automatizado por scheduler)

---

### RF-003: Calcular Posición y Tiempo Estimado en Cola

**Descripción:** El sistema debe calcular en tiempo real la posición exacta del cliente en cola y estimar el tiempo de espera basándose en: cantidad de tickets EN_ESPERA con createdAt anterior, tiempo promedio de atención por tipo de cola, y cantidad de ejecutivos disponibles. El cálculo debe actualizarse automáticamente cuando cambia el estado de otros tickets.

**Prioridad:** Alta

**Actor Principal:** Sistema (automatizado)

**Precondiciones:**

- Ticket existe en base de datos
- Cola tiene tipo definido (CAJA, PERSONAL_BANKER, EMPRESAS, GERENCIA)
- Tiempos promedio configurados por tipo de cola

**Algoritmos de Cálculo:**

**Posición en Cola:**
```
posición = COUNT(tickets EN_ESPERA con createdAt < este_ticket.createdAt) + 1
```

**Tiempo Estimado:**
```
tiempoEstimado = posición × tiempoPromedioCola
```

**Tiempos Promedio por Cola:**
- CAJA: 5 minutos
- PERSONAL_BANKER: 15 minutos
- EMPRESAS: 20 minutos
- GERENCIA: 30 minutos

**Reglas de Negocio Aplicables:**

- RN-003: Orden FIFO dentro de cola (createdAt determina orden)
- RN-010: Fórmula de cálculo de tiempo estimado
- RN-012: Cambio a estado PROXIMO cuando posición ≤ 3

**Criterios de Aceptación (Gherkin):**

**Escenario 1: Cálculo de posición - Primera persona en cola**

```gherkin
Given la cola PERSONAL_BANKER está vacía
When se crea un ticket T1 con queueType = PERSONAL_BANKER
Then el sistema calcula:
  | positionInQueue | estimatedWaitMinutes |
  | 1               | 15                   |
And el cálculo es: 1 × 15min = 15min
```

**Escenario 2: Cálculo de posición - Cola con tickets existentes**

```gherkin
Given la cola EMPRESAS tiene tickets:
  | numero | createdAt           | status     |
  | E01    | 2025-12-15 10:00:00 | EN_ESPERA  |
  | E02    | 2025-12-15 10:05:00 | EN_ESPERA  |
  | E03    | 2025-12-15 10:10:00 | EN_ESPERA  |
  | E04    | 2025-12-15 10:15:00 | EN_ESPERA  |
When se crea un ticket E05 a las 10:20:00
Then el sistema calcula positionInQueue = 5
And estimatedWaitMinutes = 100
And el cálculo es: 5 × 20min = 100min
```

**Escenario 3: Recálculo automático al completar ticket anterior**

```gherkin
Given un ticket T5 tiene:
  | numero | positionInQueue | estimatedWaitMinutes | queueType |
  | C05    | 5               | 25                   | CAJA      |
When el ticket C01 (primero en cola) cambia a estado COMPLETADO
Then el sistema recalcula automáticamente T5:
  | positionInQueue | estimatedWaitMinutes |
  | 4               | 20                   |
And el nuevo cálculo es: 4 × 5min = 20min
```

**Escenario 4: Cambio a estado PROXIMO cuando posición ≤ 3**

```gherkin
Given un ticket P08 tiene:
  | positionInQueue | status     |
  | 4               | EN_ESPERA  |
When tickets anteriores son completados
And el sistema recalcula positionInQueue = 3
Then el sistema actualiza automáticamente:
  | status  | positionInQueue |
  | PROXIMO | 3               |
And el sistema programa Mensaje 2 (pre-aviso)
```

**Escenario 5: Consulta de posición vía API**

```gherkin
Given un ticket con numero "G02" tiene positionInQueue = 2
When el cliente consulta GET /api/tickets/G02/position
Then el sistema retorna HTTP 200 con JSON:
  {
    "numero": "G02",
    "positionInQueue": 2,
    "estimatedWaitMinutes": 60,
    "status": "PROXIMO",
    "queueType": "GERENCIA"
  }
```

**Escenario 6: Diferentes tiempos por tipo de cola**

```gherkin
Given 4 tickets en diferentes colas, todos en posición 3:
  | numero | queueType       | positionInQueue |
  | C03    | CAJA            | 3               |
  | P03    | PERSONAL_BANKER | 3               |
  | E03    | EMPRESAS        | 3               |
  | G03    | GERENCIA        | 3               |
Then los tiempos estimados son:
  | numero | estimatedWaitMinutes | cálculo      |
  | C03    | 15                   | 3 × 5min     |
  | P03    | 45                   | 3 × 15min    |
  | E03    | 60                   | 3 × 20min    |
  | G03    | 90                   | 3 × 30min    |
```

**Escenario 7: Ticket no encontrado**

```gherkin
Given no existe un ticket con numero "X99"
When el cliente consulta GET /api/tickets/X99/position
Then el sistema retorna HTTP 404 Not Found con JSON:
  {
    "error": "TICKET_NO_ENCONTRADO",
    "mensaje": "No existe un ticket con número X99"
  }
```

**Postcondiciones:**

- Posición calculada correctamente según orden FIFO
- Tiempo estimado basado en fórmula RN-010
- Estado actualizado a PROXIMO si posición ≤ 3
- Mensaje 2 programado si aplica cambio a PROXIMO

**Endpoints HTTP:**

- `GET /api/tickets/{numero}/position` - Consultar posición actual del ticket

---

### RF-004: Asignar Ticket a Ejecutivo Automáticamente

**Descripción:** El sistema debe asignar automáticamente el siguiente ticket en cola cuando un ejecutivo se libere (cambia de BUSY a AVAILABLE), considerando: prioridad de colas (GERENCIA > EMPRESAS > PERSONAL_BANKER > CAJA), balanceo de carga entre ejecutivos disponibles, y orden FIFO dentro de cada cola. La asignación debe ser instantánea y notificar tanto al cliente como al ejecutivo.

**Prioridad:** Alta

**Actor Principal:** Sistema (automatizado)

**Precondiciones:**

- Al menos un asesor en estado AVAILABLE
- Al menos un ticket en estado EN_ESPERA o PROXIMO
- Sistema de notificaciones operativo

**Modelo de Datos (Entidad Advisor):**

- id: BIGSERIAL (primary key)
- name: String, nombre completo del asesor
- email: String, correo electrónico
- status: Enum (AVAILABLE, BUSY, OFFLINE)
- moduleNumber: Integer (1-5), número de módulo asignado
- assignedTicketsCount: Integer, contador de tickets asignados en el día
- queueTypes: Array de QueueType, colas que puede atender

**Algoritmo de Asignación:**

```
1. Detectar evento: asesor cambia a AVAILABLE
2. Buscar tickets pendientes ordenados por:
   a. Prioridad de cola (descendente): GERENCIA(4) > EMPRESAS(3) > PERSONAL_BANKER(2) > CAJA(1)
   b. Fecha de creación (ascendente): FIFO
3. Seleccionar asesor AVAILABLE con menor assignedTicketsCount
4. Asignar ticket al asesor:
   - ticket.assignedAdvisor = asesor
   - ticket.assignedModuleNumber = asesor.moduleNumber
   - ticket.status = ATENDIENDO
   - asesor.status = BUSY
   - asesor.assignedTicketsCount += 1
5. Programar Mensaje 3 (totem_es_tu_turno)
6. Notificar al asesor en su terminal
7. Registrar auditoría: TICKET_ASIGNADO
```

**Reglas de Negocio Aplicables:**

- RN-002: Prioridad de colas (GERENCIA > EMPRESAS > PERSONAL_BANKER > CAJA)
- RN-003: Orden FIFO dentro de cola
- RN-004: Balanceo de carga (menor assignedTicketsCount)
- RN-011: Auditoría obligatoria
- RN-013: Estados de asesor

**Criterios de Aceptación (Gherkin):**

**Escenario 1: Asignación exitosa con un solo asesor disponible**

```gherkin
Given existe un asesor:
  | id | name       | status    | moduleNumber | assignedTicketsCount |
  | 1  | Juan Pérez | AVAILABLE | 2            | 5                    |
And existe un ticket:
  | numero | queueType | status     | createdAt           |
  | C05    | CAJA      | EN_ESPERA  | 2025-12-15 10:30:00 |
When el asesor Juan Pérez se libera (evento: status = AVAILABLE)
Then el sistema asigna el ticket C05 al asesor:
  | assignedAdvisor | assignedModuleNumber | status      |
  | Juan Pérez      | 2                    | ATENDIENDO  |
And el asesor actualiza:
  | status | assignedTicketsCount |
  | BUSY   | 6                    |
And el sistema programa Mensaje 3 con:
  """
  🔔 ¡ES TU TURNO C05!
  Dirígete al módulo: 2
  Asesor: Juan Pérez
  """
And el sistema registra auditoría: "TICKET_ASIGNADO"
```

**Escenario 2: Balanceo de carga - Seleccionar asesor con menor carga**

```gherkin
Given existen 3 asesores AVAILABLE:
  | id | name         | status    | moduleNumber | assignedTicketsCount |
  | 1  | Juan Pérez   | AVAILABLE | 1            | 8                    |
  | 2  | María López  | AVAILABLE | 2            | 5                    |
  | 3  | Carlos Ruiz  | AVAILABLE | 3            | 5                    |
And existe un ticket P10 EN_ESPERA
When el sistema ejecuta asignación automática
Then el sistema selecciona a María López (menor assignedTicketsCount = 5, primero por ID)
And el ticket P10 se asigna a María López en módulo 2
```

**Escenario 3: Prioridad de colas - GERENCIA antes que CAJA**

```gherkin
Given existe un asesor AVAILABLE
And existen tickets pendientes:
  | numero | queueType | createdAt           | prioridad |
  | C01    | CAJA      | 2025-12-15 09:00:00 | 1         |
  | G01    | GERENCIA  | 2025-12-15 09:30:00 | 4         |
When el sistema ejecuta asignación automática
Then el sistema asigna G01 primero (prioridad 4 > 1)
And C01 permanece EN_ESPERA
```

**Escenario 4: FIFO dentro de misma cola**

```gherkin
Given existe un asesor AVAILABLE
And existen tickets PERSONAL_BANKER:
  | numero | createdAt           | status     |
  | P05    | 2025-12-15 10:00:00 | EN_ESPERA  |
  | P06    | 2025-12-15 10:15:00 | EN_ESPERA  |
  | P07    | 2025-12-15 10:30:00 | EN_ESPERA  |
When el sistema ejecuta asignación automática
Then el sistema asigna P05 (createdAt más antiguo)
And P06 y P07 permanecen EN_ESPERA
```

**Escenario 5: No hay asesores disponibles**

```gherkin
Given todos los asesores están BUSY u OFFLINE:
  | id | name       | status  |
  | 1  | Juan Pérez | BUSY    |
  | 2  | María López| OFFLINE |
And existen 10 tickets EN_ESPERA
When el sistema intenta asignación automática
Then el sistema NO asigna ningún ticket
And todos los tickets permanecen EN_ESPERA
And el sistema espera evento de asesor AVAILABLE
```

**Escenario 6: No hay tickets pendientes**

```gherkin
Given existe un asesor AVAILABLE
And NO existen tickets EN_ESPERA ni PROXIMO
When el asesor se libera
Then el sistema NO realiza asignación
And el asesor permanece AVAILABLE
```

**Escenario 7: Asignación múltiple - Varios asesores se liberan simultáneamente**

```gherkin
Given existen 3 asesores que se liberan simultáneamente:
  | id | name         | status    | assignedTicketsCount |
  | 1  | Juan Pérez   | AVAILABLE | 10                   |
  | 2  | María López  | AVAILABLE | 8                    |
  | 3  | Carlos Ruiz  | AVAILABLE | 8                    |
And existen 3 tickets EN_ESPERA:
  | numero | queueType | createdAt           |
  | E01    | EMPRESAS  | 2025-12-15 10:00:00 |
  | E02    | EMPRESAS  | 2025-12-15 10:15:00 |
  | E03    | EMPRESAS  | 2025-12-15 10:30:00 |
When el sistema ejecuta asignación automática
Then las asignaciones son:
  | ticket | asesor       | razón                              |
  | E01    | María López  | Menor carga (8), ticket más antiguo|
  | E02    | Carlos Ruiz  | Menor carga (8), siguiente ticket  |
  | E03    | Juan Pérez   | Único disponible restante          |
```

**Postcondiciones:**

- Ticket actualizado con assignedAdvisor, assignedModuleNumber, status = ATENDIENDO
- Asesor actualizado con status = BUSY, assignedTicketsCount incrementado
- Mensaje 3 programado para envío inmediato
- Notificación enviada al terminal del asesor
- Evento de auditoría registrado: TICKET_ASIGNADO

**Endpoints HTTP:**

- Ninguno (proceso interno automatizado por eventos)

---

### RF-005: Gestionar Múltiples Colas

**Descripción:** El sistema debe gestionar cuatro tipos de cola independientes (CAJA, PERSONAL_BANKER, EMPRESAS, GERENCIA), cada una con características específicas de tiempo promedio de atención y prioridad. El sistema debe permitir consultar el estado de cada cola, estadísticas en tiempo real, y cantidad de tickets pendientes por tipo.

**Prioridad:** Alta

**Actor Principal:** Sistema, Supervisor

**Precondiciones:**

- Sistema de gestión de colas operativo
- Tipos de cola configurados en el sistema
- Base de datos con tickets activos

**Características de las Colas:**

| Tipo de Cola | Display Name | Tiempo Promedio | Prioridad | Prefijo | Descripción |
|--------------|--------------|-----------------|-----------|---------|-------------|
| CAJA | Caja | 5 min | 1 | C | Transacciones básicas (depósitos, retiros, pagos) |
| PERSONAL_BANKER | Personal Banker | 15 min | 2 | P | Productos financieros (cuentas, tarjetas, créditos) |
| EMPRESAS | Empresas | 20 min | 3 | E | Clientes corporativos y empresariales |
| GERENCIA | Gerencia | 30 min | 4 | G | Casos especiales que requieren aprobación gerencial |

**Reglas de Negocio Aplicables:**

- RN-002: Prioridad de colas para asignación
- RN-006: Prefijos por tipo de cola
- RN-010: Tiempos promedio para cálculo de espera

**Criterios de Aceptación (Gherkin):**

**Escenario 1: Consultar estado de cola específica**

```gherkin
Given la cola PERSONAL_BANKER tiene tickets:
  | numero | status     | createdAt           |
  | P01    | ATENDIENDO | 2025-12-15 09:00:00 |
  | P02    | EN_ESPERA  | 2025-12-15 09:15:00 |
  | P03    | EN_ESPERA  | 2025-12-15 09:30:00 |
  | P04    | PROXIMO    | 2025-12-15 09:45:00 |
When el supervisor consulta GET /api/admin/queues/PERSONAL_BANKER
Then el sistema retorna HTTP 200 con JSON:
  {
    "queueType": "PERSONAL_BANKER",
    "displayName": "Personal Banker",
    "averageWaitMinutes": 15,
    "priority": 2,
    "prefix": "P",
    "ticketsWaiting": 3,
    "ticketsAttending": 1,
    "ticketsCompleted": 0,
    "oldestTicketWaitMinutes": 120
  }
```

**Escenario 2: Consultar estadísticas de cola**

```gherkin
Given la cola CAJA tiene:
  | status      | cantidad |
  | EN_ESPERA   | 8        |
  | PROXIMO     | 2        |
  | ATENDIENDO  | 3        |
  | COMPLETADO  | 45       |
When el supervisor consulta GET /api/admin/queues/CAJA/stats
Then el sistema retorna HTTP 200 con JSON:
  {
    "queueType": "CAJA",
    "totalTicketsToday": 58,
    "ticketsWaiting": 10,
    "ticketsAttending": 3,
    "ticketsCompleted": 45,
    "averageWaitTimeActual": 6.5,
    "averageServiceTime": 4.8,
    "peakHour": "10:00-11:00",
    "currentLoad": "MEDIUM"
  }
```

**Escenario 3: Múltiples colas operando simultáneamente**

```gherkin
Given el sistema tiene tickets activos en todas las colas:
  | queueType       | EN_ESPERA | ATENDIENDO | COMPLETADO |
  | CAJA            | 5         | 2          | 30         |
  | PERSONAL_BANKER | 3         | 1          | 15         |
  | EMPRESAS        | 2         | 1          | 8          |
  | GERENCIA        | 1         | 0          | 3          |
When el sistema opera normalmente
Then cada cola mantiene su independencia
And los tickets no se mezclan entre colas
And las prioridades se respetan en asignación
```

**Escenario 4: Cola vacía**

```gherkin
Given la cola GERENCIA no tiene tickets activos
When el supervisor consulta GET /api/admin/queues/GERENCIA
Then el sistema retorna HTTP 200 con JSON:
  {
    "queueType": "GERENCIA",
    "displayName": "Gerencia",
    "averageWaitMinutes": 30,
    "priority": 4,
    "prefix": "G",
    "ticketsWaiting": 0,
    "ticketsAttending": 0,
    "ticketsCompleted": 0,
    "oldestTicketWaitMinutes": 0
  }
```

**Escenario 5: Resumen de todas las colas**

```gherkin
Given el sistema tiene tickets en múltiples colas
When el supervisor consulta GET /api/admin/queues
Then el sistema retorna HTTP 200 con JSON array:
  [
    {
      "queueType": "CAJA",
      "ticketsWaiting": 5,
      "ticketsAttending": 2,
      "averageWaitMinutes": 25
    },
    {
      "queueType": "PERSONAL_BANKER",
      "ticketsWaiting": 3,
      "ticketsAttending": 1,
      "averageWaitMinutes": 45
    },
    {
      "queueType": "EMPRESAS",
      "ticketsWaiting": 2,
      "ticketsAttending": 1,
      "averageWaitMinutes": 40
    },
    {
      "queueType": "GERENCIA",
      "ticketsWaiting": 1,
      "ticketsAttending": 0,
      "averageWaitMinutes": 30
    }
  ]
```

**Postcondiciones:**

- Cada cola mantiene su independencia operacional
- Estadísticas calculadas en tiempo real
- Prioridades respetadas en asignación automática
- Tiempos promedio aplicados correctamente

**Endpoints HTTP:**

- `GET /api/admin/queues` - Listar todas las colas con resumen
- `GET /api/admin/queues/{type}` - Consultar estado de cola específica
- `GET /api/admin/queues/{type}/stats` - Estadísticas detalladas de cola

---

### RF-006: Consultar Estado del Ticket

**Descripción:** El sistema debe permitir al cliente consultar en cualquier momento el estado actual de su ticket, mostrando: estado actual, posición en cola, tiempo estimado actualizado, ejecutivo asignado (si aplica), y módulo de atención. La consulta puede realizarse por UUID (código de referencia) o por número de ticket.

**Prioridad:** Alta

**Actor Principal:** Cliente

**Precondiciones:**

- Ticket existe en el sistema
- Sistema de consultas operativo

**Reglas de Negocio Aplicables:**

- RN-009: Estados de ticket
- RN-010: Cálculo de tiempo estimado actualizado

**Criterios de Aceptación (Gherkin):**

**Escenario 1: Consulta exitosa de ticket EN_ESPERA por UUID**

```gherkin
Given existe un ticket con:
  | codigoReferencia | numero | status     | positionInQueue | estimatedWaitMinutes | queueType |
  | uuid-123         | C05    | EN_ESPERA  | 5               | 25                   | CAJA      |
When el cliente consulta GET /api/tickets/uuid-123
Then el sistema retorna HTTP 200 con JSON:
  {
    "codigoReferencia": "uuid-123",
    "numero": "C05",
    "status": "EN_ESPERA",
    "positionInQueue": 5,
    "estimatedWaitMinutes": 25,
    "queueType": "CAJA",
    "assignedAdvisor": null,
    "assignedModuleNumber": null,
    "createdAt": "2025-12-15T10:30:00Z"
  }
```

**Escenario 2: Consulta de ticket ATENDIENDO con asesor asignado**

```gherkin
Given existe un ticket con:
  | numero | status      | assignedAdvisor | assignedModuleNumber | queueType       |
  | P08    | ATENDIENDO  | María López     | 3                    | PERSONAL_BANKER |
When el cliente consulta GET /api/tickets/P08/position
Then el sistema retorna HTTP 200 con JSON:
  {
    "numero": "P08",
    "status": "ATENDIENDO",
    "positionInQueue": 0,
    "estimatedWaitMinutes": 0,
    "queueType": "PERSONAL_BANKER",
    "assignedAdvisor": "María López",
    "assignedModuleNumber": 3,
    "message": "Tu turno está siendo atendido en el módulo 3"
  }
```

**Escenario 3: Consulta de ticket PROXIMO (próximo a ser atendido)**

```gherkin
Given existe un ticket con:
  | numero | status  | positionInQueue | estimatedWaitMinutes | queueType |
  | E02    | PROXIMO | 2               | 40                   | EMPRESAS  |
When el cliente consulta GET /api/tickets/E02/position
Then el sistema retorna HTTP 200 con JSON:
  {
    "numero": "E02",
    "status": "PROXIMO",
    "positionInQueue": 2,
    "estimatedWaitMinutes": 40,
    "queueType": "EMPRESAS",
    "assignedAdvisor": null,
    "assignedModuleNumber": null,
    "message": "Pronto será tu turno. Por favor, acércate a la sucursal."
  }
```

**Escenario 4: Consulta de ticket COMPLETADO**

```gherkin
Given existe un ticket con:
  | numero | status      | assignedAdvisor | completedAt         |
  | G01    | COMPLETADO  | Juan Pérez      | 2025-12-15 11:45:00 |
When el cliente consulta GET /api/tickets/G01/position
Then el sistema retorna HTTP 200 con JSON:
  {
    "numero": "G01",
    "status": "COMPLETADO",
    "positionInQueue": 0,
    "estimatedWaitMinutes": 0,
    "assignedAdvisor": "Juan Pérez",
    "completedAt": "2025-12-15T11:45:00Z",
    "message": "Tu atención ha sido completada."
  }
```

**Escenario 5: Ticket no encontrado**

```gherkin
Given no existe un ticket con numero "X99"
When el cliente consulta GET /api/tickets/X99/position
Then el sistema retorna HTTP 404 Not Found con JSON:
  {
    "error": "TICKET_NO_ENCONTRADO",
    "mensaje": "No existe un ticket con número X99"
  }
```

**Escenario 6: Consulta con posición actualizada en tiempo real**

```gherkin
Given un ticket C10 tiene positionInQueue = 8 a las 10:00:00
When 3 tickets anteriores son completados
And el cliente consulta GET /api/tickets/C10/position a las 10:15:00
Then el sistema recalcula automáticamente
And retorna positionInQueue = 5
And estimatedWaitMinutes = 25 (5 × 5min)
```

**Postcondiciones:**

- Información actualizada en tiempo real
- Posición recalculada si hubo cambios
- Mensaje contextual según estado del ticket

**Endpoints HTTP:**

- `GET /api/tickets/{codigoReferencia}` - Consultar por UUID
- `GET /api/tickets/{numero}/position` - Consultar por número de ticket

---

### RF-007: Panel de Monitoreo para Supervisor

**Descripción:** El sistema debe proveer un dashboard en tiempo real para supervisores que muestre: resumen de tickets por estado, cantidad de clientes en espera por cola, estado de ejecutivos, tiempos promedio de atención, y alertas de situaciones críticas. El dashboard debe actualizarse automáticamente cada 5 segundos sin intervención del usuario.

**Prioridad:** Alta

**Actor Principal:** Supervisor

**Precondiciones:**

- Usuario autenticado con rol de supervisor
- Sistema operativo con datos en tiempo real
- Conexión a base de datos activa

**Componentes del Dashboard:**

1. **Resumen General:** Total de tickets por estado (EN_ESPERA, ATENDIENDO, COMPLETADO)
2. **Estado de Colas:** Tickets en espera por cada tipo de cola
3. **Estado de Asesores:** Cantidad de asesores por estado (AVAILABLE, BUSY, OFFLINE)
4. **Métricas de Rendimiento:** Tiempos promedio de atención y espera
5. **Alertas:** Notificaciones de situaciones críticas (colas saturadas, asesores insuficientes)

**Reglas de Negocio Aplicables:**

- RN-009: Estados de ticket
- RN-013: Estados de asesor

**Criterios de Aceptación (Gherkin):**

**Escenario 1: Consultar dashboard completo**

```gherkin
Given el sistema tiene tickets y asesores activos
When el supervisor consulta GET /api/admin/dashboard
Then el sistema retorna HTTP 200 con JSON:
  {
    "timestamp": "2025-12-15T10:30:00Z",
    "summary": {
      "totalTicketsToday": 87,
      "ticketsWaiting": 15,
      "ticketsAttending": 5,
      "ticketsCompleted": 67
    },
    "queueStatus": [
      {
        "queueType": "CAJA",
        "waiting": 8,
        "attending": 2,
        "averageWaitMinutes": 30
      },
      {
        "queueType": "PERSONAL_BANKER",
        "waiting": 4,
        "attending": 2,
        "averageWaitMinutes": 45
      },
      {
        "queueType": "EMPRESAS",
        "waiting": 2,
        "attending": 1,
        "averageWaitMinutes": 40
      },
      {
        "queueType": "GERENCIA",
        "waiting": 1,
        "attending": 0,
        "averageWaitMinutes": 30
      }
    ],
    "advisorStatus": {
      "available": 2,
      "busy": 5,
      "offline": 1
    },
    "performance": {
      "averageServiceTimeMinutes": 12.5,
      "averageWaitTimeMinutes": 38.2,
      "ticketsPerHour": 10.8
    },
    "alerts": [
      {
        "type": "COLA_SATURADA",
        "message": "Cola CAJA tiene 8 tickets en espera",
        "severity": "MEDIUM"
      }
    ]
  }
```

**Escenario 2: Consultar estado de asesores**

```gherkin
Given existen 8 asesores en el sistema:
  | id | name         | status    | moduleNumber | assignedTicketsCount |
  | 1  | Juan Pérez   | BUSY      | 1            | 12                   |
  | 2  | María López  | BUSY      | 2            | 10                   |
  | 3  | Carlos Ruiz  | AVAILABLE | 3            | 8                    |
  | 4  | Ana García   | AVAILABLE | 4            | 9                    |
  | 5  | Luis Torres  | BUSY      | 5            | 11                   |
  | 6  | Sofia Díaz   | OFFLINE   | -            | 7                    |
  | 7  | Pedro Vega   | BUSY      | 1            | 13                   |
  | 8  | Laura Rojas  | BUSY      | 2            | 9                    |
When el supervisor consulta GET /api/admin/advisors
Then el sistema retorna HTTP 200 con JSON array de 8 asesores
And el resumen muestra:
  | status    | count |
  | AVAILABLE | 2     |
  | BUSY      | 5     |
  | OFFLINE   | 1     |
```

**Escenario 3: Consultar estadísticas de asesores**

```gherkin
Given el sistema tiene datos de rendimiento de asesores
When el supervisor consulta GET /api/admin/advisors/stats
Then el sistema retorna HTTP 200 con JSON:
  {
    "totalAdvisors": 8,
    "available": 2,
    "busy": 5,
    "offline": 1,
    "topPerformers": [
      {
        "name": "Pedro Vega",
        "ticketsCompleted": 13,
        "averageServiceTime": 11.2
      },
      {
        "name": "Juan Pérez",
        "ticketsCompleted": 12,
        "averageServiceTime": 12.5
      }
    ],
    "utilizationRate": 62.5
  }
```

**Escenario 4: Cambiar estado de asesor manualmente**

```gherkin
Given un asesor con id=3 tiene status=AVAILABLE
When el supervisor ejecuta PUT /api/admin/advisors/3/status con body:
  {
    "status": "OFFLINE",
    "reason": "Almuerzo"
  }
Then el sistema actualiza el asesor:
  | status  | assignedTicketsCount |
  | OFFLINE | 8                    |
And el sistema retorna HTTP 200 con JSON:
  {
    "id": 3,
    "name": "Carlos Ruiz",
    "status": "OFFLINE",
    "message": "Estado actualizado exitosamente"
  }
And el sistema registra auditoría: "ASESOR_ESTADO_CAMBIADO"
```

**Escenario 5: Alerta de cola saturada**

```gherkin
Given la cola CAJA tiene 16 tickets EN_ESPERA
When el sistema evalúa alertas (umbral: 15 tickets)
Then el sistema genera alerta:
  {
    "type": "COLA_SATURADA",
    "queueType": "CAJA",
    "ticketsWaiting": 16,
    "severity": "HIGH",
    "message": "Cola CAJA crítica: 16 tickets en espera",
    "recommendation": "Asignar más asesores a CAJA"
  }
And la alerta aparece en el dashboard
```

**Escenario 6: Resumen ejecutivo del día**

```gherkin
Given es fin del día operativo (18:00)
When el supervisor consulta GET /api/admin/summary
Then el sistema retorna HTTP 200 con JSON:
  {
    "date": "2025-12-15",
    "totalTickets": 156,
    "ticketsCompleted": 148,
    "ticketsCancelled": 5,
    "ticketsNoAttended": 3,
    "averageWaitTime": 35.2,
    "averageServiceTime": 13.8,
    "peakHour": "10:00-11:00",
    "queueDistribution": {
      "CAJA": 78,
      "PERSONAL_BANKER": 45,
      "EMPRESAS": 23,
      "GERENCIA": 10
    },
    "advisorPerformance": {
      "totalAdvisors": 8,
      "averageTicketsPerAdvisor": 18.5,
      "topPerformer": "Pedro Vega"
    }
  }
```

**Postcondiciones:**

- Dashboard actualizado cada 5 segundos automáticamente
- Alertas generadas cuando se detectan situaciones críticas
- Métricas calculadas en tiempo real
- Cambios de estado de asesores registrados en auditoría

**Endpoints HTTP:**

- `GET /api/admin/dashboard` - Dashboard completo en tiempo real
- `GET /api/admin/advisors` - Lista de asesores con estado actual
- `GET /api/admin/advisors/stats` - Estadísticas de rendimiento de asesores
- `PUT /api/admin/advisors/{id}/status` - Cambiar estado de asesor manualmente
- `GET /api/admin/summary` - Resumen ejecutivo del día

---

### RF-008: Registrar Auditoría de Eventos

**Descripción:** El sistema debe registrar automáticamente todos los eventos críticos del sistema en una tabla de auditoría, incluyendo: creación de tickets, asignaciones a asesores, cambios de estado, envío de mensajes, y acciones administrativas. Cada registro debe contener timestamp, tipo de evento, actor involucrado, entidad afectada, y detalles de cambios de estado para trazabilidad completa.

**Prioridad:** Alta

**Actor Principal:** Sistema (automatizado)

**Precondiciones:**

- Sistema de auditoría operativo
- Base de datos con tabla de auditoría configurada
- Eventos del sistema capturados correctamente

**Modelo de Datos (Entidad AuditLog):**

- id: BIGSERIAL (primary key)
- timestamp: Timestamp, fecha/hora del evento
- eventType: String, tipo de evento (TICKET_CREADO, TICKET_ASIGNADO, etc.)
- actor: String, quien ejecutó la acción (cliente, sistema, supervisor)
- entityType: String, tipo de entidad afectada (TICKET, ADVISOR, MESSAGE)
- entityId: String, identificador de la entidad afectada
- oldState: JSON, estado anterior (nullable)
- newState: JSON, estado nuevo
- metadata: JSON, información adicional del contexto
- ipAddress: String, dirección IP de origen (nullable)

**Tipos de Eventos Auditables:**

| Tipo de Evento | Descripción | Actor | Entidad |
|----------------|-------------|-------|---------|
| TICKET_CREADO | Ticket creado por cliente | Cliente | TICKET |
| TICKET_ASIGNADO | Ticket asignado a asesor | Sistema | TICKET |
| TICKET_COMPLETADO | Atención finalizada | Asesor | TICKET |
| TICKET_CANCELADO | Ticket cancelado | Cliente/Sistema | TICKET |
| MENSAJE_ENVIADO | Mensaje Telegram enviado | Sistema | MESSAGE |
| MENSAJE_FALLIDO | Mensaje Telegram falló | Sistema | MESSAGE |
| ASESOR_ESTADO_CAMBIADO | Estado de asesor modificado | Supervisor | ADVISOR |
| ASESOR_ASIGNADO | Asesor asignado a ticket | Sistema | ADVISOR |

**Reglas de Negocio Aplicables:**

- RN-011: Auditoría obligatoria para todos los eventos críticos

**Criterios de Aceptación (Gherkin):**

**Escenario 1: Auditoría de creación de ticket**

```gherkin
Given un cliente crea un ticket exitosamente
When el sistema completa la creación del ticket C05
Then el sistema registra en auditoría:
  | timestamp           | eventType      | actor          | entityType | entityId |
  | 2025-12-15 10:30:00 | TICKET_CREADO  | 12345678-9     | TICKET     | uuid-123 |
And el registro incluye newState:
  {
    "numero": "C05",
    "status": "EN_ESPERA",
    "queueType": "CAJA",
    "positionInQueue": 5
  }
And oldState es null
```

**Escenario 2: Auditoría de asignación de ticket**

```gherkin
Given un ticket P08 es asignado al asesor María López
When el sistema completa la asignación
Then el sistema registra en auditoría:
  | timestamp           | eventType         | actor   | entityType | entityId |
  | 2025-12-15 11:00:00 | TICKET_ASIGNADO   | Sistema | TICKET     | uuid-456 |
And el registro incluye oldState:
  {
    "status": "EN_ESPERA",
    "assignedAdvisor": null,
    "assignedModuleNumber": null
  }
And newState:
  {
    "status": "ATENDIENDO",
    "assignedAdvisor": "María López",
    "assignedModuleNumber": 3
  }
```

**Escenario 3: Auditoría de envío de mensaje exitoso**

```gherkin
Given un mensaje Telegram es enviado exitosamente
When el sistema confirma el envío con messageId "TG-789"
Then el sistema registra en auditoría:
  | timestamp           | eventType       | actor   | entityType | entityId |
  | 2025-12-15 10:30:05 | MENSAJE_ENVIADO | Sistema | MESSAGE    | msg-123  |
And el registro incluye metadata:
  {
    "plantilla": "totem_ticket_creado",
    "telegramMessageId": "TG-789",
    "ticketNumero": "C05",
    "intentos": 1
  }
```

**Escenario 4: Auditoría de mensaje fallido**

```gherkin
Given un mensaje Telegram falla después de 4 intentos
When el sistema marca el mensaje como FALLIDO
Then el sistema registra en auditoría:
  | timestamp           | eventType       | actor   | entityType | entityId |
  | 2025-12-15 10:35:00 | MENSAJE_FALLIDO | Sistema | MESSAGE    | msg-456  |
And el registro incluye metadata:
  {
    "plantilla": "totem_proximo_turno",
    "intentos": 4,
    "ultimoError": "Network timeout",
    "ticketNumero": "P08"
  }
```

**Escenario 5: Auditoría de cambio de estado de asesor**

```gherkin
Given un supervisor cambia el estado de un asesor a OFFLINE
When el sistema ejecuta PUT /api/admin/advisors/3/status
Then el sistema registra en auditoría:
  | timestamp           | eventType              | actor              | entityType | entityId |
  | 2025-12-15 12:00:00 | ASESOR_ESTADO_CAMBIADO | supervisor@bank.cl | ADVISOR    | 3        |
And el registro incluye oldState:
  {
    "status": "AVAILABLE",
    "assignedTicketsCount": 8
  }
And newState:
  {
    "status": "OFFLINE",
    "assignedTicketsCount": 8
  }
And metadata:
  {
    "reason": "Almuerzo",
    "ipAddress": "192.168.1.100"
  }
```

**Escenario 6: Consultar historial de auditoría de un ticket**

```gherkin
Given un ticket C05 tiene múltiples eventos registrados
When el supervisor consulta el historial de auditoría
Then el sistema retorna todos los eventos ordenados por timestamp:
  | timestamp           | eventType         | actor      |
  | 2025-12-15 10:30:00 | TICKET_CREADO     | 12345678-9 |
  | 2025-12-15 10:30:05 | MENSAJE_ENVIADO   | Sistema    |
  | 2025-12-15 10:45:00 | TICKET_ASIGNADO   | Sistema    |
  | 2025-12-15 11:00:00 | TICKET_COMPLETADO | Juan Pérez |
```

**Escenario 7: Auditoría de ticket completado**

```gherkin
Given un asesor finaliza la atención de un ticket
When el ticket G01 cambia a estado COMPLETADO
Then el sistema registra en auditoría:
  | timestamp           | eventType          | actor      | entityType | entityId |
  | 2025-12-15 11:45:00 | TICKET_COMPLETADO  | Juan Pérez | TICKET     | uuid-789 |
And el registro incluye oldState:
  {
    "status": "ATENDIENDO",
    "assignedAdvisor": "Juan Pérez"
  }
And newState:
  {
    "status": "COMPLETADO",
    "completedAt": "2025-12-15T11:45:00Z"
  }
And metadata:
  {
    "serviceTimeMinutes": 28,
    "moduleNumber": 2
  }
```

**Postcondiciones:**

- Evento registrado en tabla de auditoría
- Timestamp preciso del evento
- Estados anterior y nuevo capturados (si aplica)
- Metadata contextual almacenada
- Trazabilidad completa del ciclo de vida

**Endpoints HTTP:**

- Ninguno (proceso interno automatizado)
- Consultas de auditoría disponibles en endpoints administrativos

---

## 5. Matriz de Trazabilidad

### 5.1 Matriz RF → Beneficio → Endpoints

| RF | Nombre | Beneficio de Negocio | Endpoints HTTP |
|----|--------|---------------------|----------------|
| RF-001 | Crear Ticket Digital | Digitalización del proceso, eliminación de tickets físicos | POST /api/tickets |
| RF-002 | Enviar Notificaciones Telegram | Movilidad del cliente, reducción de abandonos | Ninguno (automatizado) |
| RF-003 | Calcular Posición y Tiempo | Transparencia, gestión de expectativas | GET /api/tickets/{numero}/position |
| RF-004 | Asignar Ticket a Ejecutivo | Optimización de recursos, balanceo de carga | Ninguno (automatizado) |
| RF-005 | Gestionar Múltiples Colas | Priorización inteligente, eficiencia operacional | GET /api/admin/queues, GET /api/admin/queues/{type}, GET /api/admin/queues/{type}/stats |
| RF-006 | Consultar Estado del Ticket | Autoservicio, reducción de consultas presenciales | GET /api/tickets/{uuid}, GET /api/tickets/{numero}/position |
| RF-007 | Panel de Monitoreo | Supervisión en tiempo real, toma de decisiones | GET /api/admin/dashboard, GET /api/admin/advisors, GET /api/admin/advisors/stats, PUT /api/admin/advisors/{id}/status, GET /api/admin/summary |
| RF-008 | Registrar Auditoría | Trazabilidad completa, cumplimiento normativo | Ninguno (automatizado) |

### 5.2 Matriz de Dependencias entre RFs

| RF | Depende de | Descripción de Dependencia |
|----|------------|---------------------------|
| RF-001 | - | Independiente (punto de entrada) |
| RF-002 | RF-001 | Requiere ticket creado para enviar notificaciones |
| RF-003 | RF-001 | Requiere tickets existentes para calcular posición |
| RF-004 | RF-001, RF-003 | Requiere tickets EN_ESPERA y cálculo de prioridad |
| RF-005 | RF-001 | Requiere tickets en diferentes colas |
| RF-006 | RF-001 | Requiere ticket existente para consultar |
| RF-007 | RF-001, RF-004, RF-005 | Requiere datos de tickets, asesores y colas |
| RF-008 | RF-001, RF-002, RF-004 | Registra eventos de todos los RFs |

### 5.3 Matriz RF → Reglas de Negocio

| RF | Reglas de Negocio Aplicables |
|----|------------------------------|
| RF-001 | RN-001, RN-005, RN-006, RN-010 |
| RF-002 | RN-007, RN-008, RN-011, RN-012 |
| RF-003 | RN-003, RN-010, RN-012 |
| RF-004 | RN-002, RN-003, RN-004, RN-011, RN-013 |
| RF-005 | RN-002, RN-006, RN-010 |
| RF-006 | RN-009, RN-010 |
| RF-007 | RN-009, RN-013 |
| RF-008 | RN-011 |

---

## 6. Modelo de Datos Consolidado

### 6.1 Entidades Principales

**Ticket**
- codigoReferencia: UUID (PK)
- numero: String
- nationalId: String
- telefono: String (nullable)
- branchOffice: String
- queueType: Enum
- status: Enum
- positionInQueue: Integer
- estimatedWaitMinutes: Integer
- createdAt: Timestamp
- assignedAdvisor: FK → Advisor (nullable)
- assignedModuleNumber: Integer (nullable)
- completedAt: Timestamp (nullable)

**Advisor**
- id: BIGSERIAL (PK)
- name: String
- email: String
- status: Enum
- moduleNumber: Integer (1-5)
- assignedTicketsCount: Integer
- queueTypes: Array[QueueType]

**Message**
- id: BIGSERIAL (PK)
- ticket_id: FK → Ticket
- plantilla: String
- estadoEnvio: Enum
- fechaProgramada: Timestamp
- fechaEnvio: Timestamp (nullable)
- telegramMessageId: String (nullable)
- intentos: Integer

**AuditLog**
- id: BIGSERIAL (PK)
- timestamp: Timestamp
- eventType: String
- actor: String
- entityType: String
- entityId: String
- oldState: JSON (nullable)
- newState: JSON
- metadata: JSON
- ipAddress: String (nullable)

---

## 7. Casos de Uso Principales

### CU-001: Cliente Obtiene Ticket y Recibe Notificaciones

**Flujo Principal:**
1. Cliente ingresa RUT y selecciona tipo de atención (RF-001)
2. Sistema genera ticket con número único (RF-001)
3. Sistema calcula posición y tiempo estimado (RF-003)
4. Sistema envía Mensaje 1 de confirmación (RF-002)
5. Cliente sale de sucursal
6. Sistema monitorea progreso de cola (RF-003)
7. Cuando posición ≤ 3, sistema envía Mensaje 2 (RF-002)
8. Cliente regresa a sucursal
9. Sistema asigna ticket a asesor disponible (RF-004)
10. Sistema envía Mensaje 3 con módulo (RF-002)
11. Cliente es atendido
12. Sistema registra auditoría de todos los eventos (RF-008)

### CU-002: Supervisor Monitorea Operación en Tiempo Real

**Flujo Principal:**
1. Supervisor accede al dashboard (RF-007)
2. Sistema muestra resumen de tickets por estado (RF-007)
3. Sistema muestra estado de todas las colas (RF-005)
4. Sistema muestra estado de asesores (RF-007)
5. Sistema actualiza dashboard cada 5 segundos (RF-007)
6. Supervisor detecta cola saturada (RF-007)
7. Supervisor cambia estado de asesor a AVAILABLE (RF-007)
8. Sistema registra cambio en auditoría (RF-008)

### CU-003: Cliente Consulta Estado de su Ticket

**Flujo Principal:**
1. Cliente accede a consulta con número de ticket (RF-006)
2. Sistema busca ticket en base de datos (RF-006)
3. Sistema recalcula posición actual (RF-003)
4. Sistema retorna estado actualizado (RF-006)
5. Cliente visualiza posición y tiempo estimado (RF-006)

---

## 8. Matriz de Endpoints HTTP

| Método | Endpoint | RF | Descripción | Autenticación |
|--------|----------|----|-----------|--------------| 
| POST | /api/tickets | RF-001 | Crear nuevo ticket | No |
| GET | /api/tickets/{uuid} | RF-006 | Consultar ticket por UUID | No |
| GET | /api/tickets/{numero}/position | RF-003, RF-006 | Consultar posición por número | No |
| GET | /api/admin/queues | RF-005 | Listar todas las colas | Sí |
| GET | /api/admin/queues/{type} | RF-005 | Consultar cola específica | Sí |
| GET | /api/admin/queues/{type}/stats | RF-005 | Estadísticas de cola | Sí |
| GET | /api/admin/dashboard | RF-007 | Dashboard completo | Sí |
| GET | /api/admin/advisors | RF-007 | Lista de asesores | Sí |
| GET | /api/admin/advisors/stats | RF-007 | Estadísticas de asesores | Sí |
| PUT | /api/admin/advisors/{id}/status | RF-007 | Cambiar estado de asesor | Sí |
| GET | /api/admin/summary | RF-007 | Resumen ejecutivo del día | Sí |
| GET | /api/health | - | Health check del sistema | No |

**Total de Endpoints:** 12

---

## 9. Validaciones y Reglas de Formato

### 9.1 Validaciones de Entrada

**nationalId (RUT/ID):**
- Obligatorio
- Formato: String de 8-12 caracteres
- Ejemplo: "12345678-9"

**telefono:**
- Opcional
- Formato: +56XXXXXXXXX (Chile)
- Longitud: 12 caracteres
- Ejemplo: "+56912345678"

**branchOffice:**
- Obligatorio
- String de 3-50 caracteres
- Ejemplo: "Sucursal Centro"

**queueType:**
- Obligatorio
- Valores permitidos: CAJA, PERSONAL_BANKER, EMPRESAS, GERENCIA

### 9.2 Formatos de Respuesta

**Códigos HTTP Utilizados:**
- 200 OK: Consulta exitosa
- 201 Created: Recurso creado exitosamente
- 400 Bad Request: Validación fallida
- 404 Not Found: Recurso no encontrado
- 409 Conflict: Conflicto de negocio (ej: ticket duplicado)
- 500 Internal Server Error: Error del servidor

---

## 10. Checklist de Validación

### 10.1 Completitud

- [x] 8 Requerimientos Funcionales documentados (RF-001 a RF-008)
- [x] 13 Reglas de Negocio numeradas (RN-001 a RN-013)
- [x] 4 Enumeraciones definidas (QueueType, TicketStatus, AdvisorStatus, MessageTemplate)
- [x] Mínimo 48 escenarios Gherkin totales
- [x] 12 Endpoints HTTP mapeados
- [x] 4 Entidades principales definidas (Ticket, Advisor, Message, AuditLog)
- [x] 3 Casos de Uso principales documentados
- [x] Matriz de trazabilidad completa

### 10.2 Criterios Cuantitativos

| Criterio | Objetivo | Actual | Estado |
|----------|----------|--------|--------|
| Requerimientos Funcionales | 8 | 8 | ✅ |
| Reglas de Negocio | 13 | 13 | ✅ |
| Escenarios Gherkin | ≥44 | 48 | ✅ |
| Endpoints HTTP | 11-12 | 12 | ✅ |
| Enumeraciones | 4 | 4 | ✅ |
| Entidades | 3-4 | 4 | ✅ |

**Desglose de Escenarios por RF:**
- RF-001: 7 escenarios
- RF-002: 7 escenarios
- RF-003: 7 escenarios
- RF-004: 7 escenarios
- RF-005: 5 escenarios
- RF-006: 6 escenarios
- RF-007: 6 escenarios
- RF-008: 7 escenarios
- **Total: 52 escenarios** ✅

### 10.3 Criterios Cualitativos

- [x] Formato Gherkin correcto (Given/When/Then/And)
- [x] Ejemplos JSON válidos en respuestas HTTP
- [x] Sin ambigüedades en descripciones
- [x] Sin mencionar tecnologías de implementación
- [x] Numeración consistente (RF-XXX, RN-XXX)
- [x] Tablas bien formateadas
- [x] Jerarquía clara con markdown

### 10.4 Trazabilidad

- [x] Cada RF tiene reglas de negocio aplicables identificadas
- [x] Cada RF tiene endpoints HTTP mapeados
- [x] Cada RF tiene beneficio de negocio documentado
- [x] Dependencias entre RFs documentadas
- [x] Casos de uso vinculan múltiples RFs

---

## 11. Glosario

| Término | Definición Completa |
|---------|---------------------|
| Backoff Exponencial | Estrategia de reintentos donde el tiempo de espera se duplica en cada intento |
| Balanceo de Carga | Distribución equitativa de trabajo entre múltiples recursos |
| Dashboard | Panel de control visual con métricas en tiempo real |
| FIFO | First In, First Out - Primero en entrar, primero en salir |
| Gherkin | Lenguaje de especificación de comportamiento (Given/When/Then) |
| NPS | Net Promoter Score - Métrica de satisfacción del cliente |
| Scheduler | Componente que ejecuta tareas programadas automáticamente |
| Telegram Bot API | Interfaz de programación para enviar mensajes vía Telegram |
| Throughput | Cantidad de tickets procesados por unidad de tiempo |
| UUID | Identificador Único Universal de 128 bits |

---

## 12. Resumen Ejecutivo

### Métricas del Documento

- **Requerimientos Funcionales:** 8
- **Reglas de Negocio:** 13
- **Escenarios de Prueba:** 52
- **Endpoints HTTP:** 12
- **Entidades de Datos:** 4
- **Enumeraciones:** 4
- **Casos de Uso:** 3

### Beneficios Esperados

1. **Mejora de NPS:** De 45 a 65 puntos (+44%)
2. **Reducción de Abandonos:** De 15% a 5% (-67%)
3. **Incremento de Productividad:** +20% tickets atendidos por ejecutivo
4. **Trazabilidad:** 100% de eventos auditados

### Próximos Pasos

1. Revisión y aprobación por stakeholders
2. Diseño de arquitectura técnica (PROMPT 2)
3. Diseño de base de datos
4. Desarrollo de prototipos
5. Plan de pruebas basado en escenarios Gherkin

---

**Documento preparado por:** Analista de Negocio Senior  
**Fecha de elaboración:** Diciembre 2025  
**Versión:** 1.0  
**Estado:** Completo - Listo para Revisión

---

**FIN DEL DOCUMENTO**

