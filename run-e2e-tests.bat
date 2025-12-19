@echo off
REM ============================================================
REM E2E Tests Runner - Sistema Ticketero
REM ============================================================

echo.
echo ========================================
echo   🧪 E2E TESTS - SISTEMA TICKETERO
echo ========================================
echo.

REM Verificar Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ ERROR: Java no encontrado
    echo    Instalar Java 21+ y agregar al PATH
    pause
    exit /b 1
)

REM Verificar Maven
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ ERROR: Maven no encontrado
    echo    Instalar Maven 3.9+ y agregar al PATH
    pause
    exit /b 1
)

echo ✅ Prerrequisitos verificados
echo.

REM Menú de opciones
:menu
echo Selecciona una opción:
echo.
echo 1. Ejecutar TODOS los tests E2E
echo 2. Ejecutar tests de Creación de Tickets
echo 3. Ejecutar tests de Procesamiento
echo 4. Ejecutar tests de Notificaciones
echo 5. Ejecutar tests de Validaciones
echo 6. Ejecutar tests de Admin Dashboard
echo 7. Ejecutar con logs detallados (-X)
echo 8. Generar reporte HTML
echo 9. Salir
echo.

set /p choice="Ingresa tu opción (1-9): "

if "%choice%"=="1" goto all_tests
if "%choice%"=="2" goto creation_tests
if "%choice%"=="3" goto processing_tests
if "%choice%"=="4" goto notification_tests
if "%choice%"=="5" goto validation_tests
if "%choice%"=="6" goto admin_tests
if "%choice%"=="7" goto verbose_tests
if "%choice%"=="8" goto generate_report
if "%choice%"=="9" goto exit

echo ❌ Opción inválida
goto menu

:all_tests
echo.
echo 🚀 Ejecutando TODOS los tests E2E...
echo.
mvn test -Dtest="*IT"
goto show_results

:creation_tests
echo.
echo 🎫 Ejecutando tests de Creación de Tickets...
echo.
mvn test -Dtest=TicketCreationIT
goto show_results

:processing_tests
echo.
echo ⚙️ Ejecutando tests de Procesamiento...
echo.
mvn test -Dtest=TicketProcessingIT
goto show_results

:notification_tests
echo.
echo 📱 Ejecutando tests de Notificaciones...
echo.
mvn test -Dtest=NotificationIT
goto show_results

:validation_tests
echo.
echo ✅ Ejecutando tests de Validaciones...
echo.
mvn test -Dtest=ValidationIT
goto show_results

:admin_tests
echo.
echo 👨‍💼 Ejecutando tests de Admin Dashboard...
echo.
mvn test -Dtest=AdminDashboardIT
goto show_results

:verbose_tests
echo.
echo 🔍 Ejecutando tests con logs detallados...
echo.
mvn test -Dtest="*IT" -X
goto show_results

:generate_report
echo.
echo 📊 Generando reporte HTML...
echo.
mvn surefire-report:report
if %errorlevel% equ 0 (
    echo ✅ Reporte generado en: target\site\surefire-report.html
    echo.
    set /p open_report="¿Abrir reporte? (y/n): "
    if /i "%open_report%"=="y" (
        start target\site\surefire-report.html
    )
) else (
    echo ❌ Error generando reporte
)
goto menu

:show_results
echo.
if %errorlevel% equ 0 (
    echo ✅ TESTS COMPLETADOS EXITOSAMENTE
    echo.
    echo 📋 Resumen:
    echo    - Tests ejecutados sin errores
    echo    - Base de datos: H2 (fallback)
    echo    - Telegram API: Mockeado con WireMock
    echo.
) else (
    echo ❌ TESTS FALLARON
    echo.
    echo 🔍 Para más detalles:
    echo    1. Revisar logs arriba
    echo    2. Ejecutar con opción 7 (logs detallados)
    echo    3. Verificar target\surefire-reports\
    echo.
)

set /p continue="¿Ejecutar más tests? (y/n): "
if /i "%continue%"=="y" goto menu

:exit
echo.
echo 👋 ¡Gracias por usar el E2E Test Runner!
echo.
pause