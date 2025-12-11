@echo off
echo ========================================
echo  Sistema Ticketero - Inicio Desarrollo
echo ========================================

echo.
echo 1. Levantando PostgreSQL...
docker-compose up -d postgres

echo.
echo 2. Esperando que PostgreSQL esté listo...
timeout /t 10 /nobreak > nul

echo.
echo 3. Verificando conexión a base de datos...
docker-compose exec postgres pg_isready -U dev -d ticketero

echo.
echo 4. Iniciando aplicación Spring Boot...
echo    - API disponible en: http://localhost:8080
echo    - Health check: http://localhost:8080/actuator/health
echo    - Dashboard: http://localhost:8080/api/admin/dashboard
echo.

mvn spring-boot:run