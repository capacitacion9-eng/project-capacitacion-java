# Sistema Ticketero - API REST

Sistema de gestión de tickets con notificaciones en tiempo real vía Telegram.

## 🚀 Inicio Rápido

### Prerrequisitos
- Java 21+
- Docker y Docker Compose
- Maven 3.9+

### Configuración

1. **Clonar y configurar:**
```bash
git clone <repository>
cd ticketero
cp .env.example .env
```

2. **Configurar variables de entorno en .env:**
```bash
TELEGRAM_BOT_TOKEN=your_telegram_bot_token_here
DATABASE_URL=jdbc:postgresql://localhost:5432/ticketero
DATABASE_USERNAME=dev
DATABASE_PASSWORD=dev123
```

3. **Levantar base de datos:**
```bash
docker-compose up -d postgres
```

4. **Ejecutar aplicación:**
```bash
mvn spring-boot:run
```

## 📋 API Endpoints

### Tickets
- `POST /api/tickets` - Crear ticket
- `GET /api/tickets/{numero}/position` - Consultar posición
- `GET /api/tickets/reference/{uuid}` - Buscar por código de referencia

### Admin
- `GET /api/admin/dashboard` - Métricas del dashboard
- `POST /api/admin/tickets/{id}/complete` - Completar ticket

## 🔧 Arquitectura

- **Spring Boot 3.2.11** con Java 21
- **PostgreSQL** como base de datos
- **Flyway** para migraciones
- **Schedulers** para procesamiento automático
- **Telegram Bot API** para notificaciones

## 📊 Funcionalidades

- ✅ Creación de tickets por cola (CAJA, PERSONAL_BANKER, EMPRESAS, GERENCIA)
- ✅ Asignación automática a asesores disponibles
- ✅ Notificaciones vía Telegram en tiempo real
- ✅ Dashboard administrativo con métricas
- ✅ Gestión automática de posiciones en cola

## 🏗️ Estructura del Proyecto

```
src/main/java/com/example/ticketero/
├── controller/          # REST Controllers
├── service/            # Lógica de negocio
├── repository/         # Acceso a datos
├── model/
│   ├── entity/         # Entidades JPA
│   ├── dto/           # DTOs (Records)
│   └── enums/         # Enumeraciones
├── scheduler/         # Tareas programadas
├── config/           # Configuraciones
└── exception/        # Manejo de errores
```

## 🐳 Docker

```bash
# Solo base de datos
docker-compose up -d postgres

# Aplicación completa
docker-compose up -d
```

## 📝 Logs

Los logs se configuran en `application.yml`:
- INFO para la aplicación
- DEBUG para SQL queries
- ERROR para excepciones