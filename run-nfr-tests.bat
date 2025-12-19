@echo off
REM =============================================================================
REM TICKETERO - NFR Test Runner (Windows)
REM =============================================================================
REM Ejecuta todos los tests no funcionales
REM Usage: run-nfr-tests.bat [test_category]
REM Categories: performance, concurrency, resilience, all
REM =============================================================================

setlocal enabledelayedexpansion

set CATEGORY=%1
if "%CATEGORY%"=="" set CATEGORY=all

echo ╔══════════════════════════════════════════════════════════════╗
echo ║                TICKETERO - NFR TEST SUITE                     ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
echo   Categoría: %CATEGORY%
echo   Timestamp: %date% %time%
echo.

REM Create results directory
if not exist "results" mkdir results

set TOTAL_TESTS=0
set PASSED_TESTS=0
set FAILED_TESTS=0
set RESULTS_FILE=results\nfr-test-results-%date:~-4,4%%date:~-10,2%%date:~-7,2%-%time:~0,2%%time:~3,2%%time:~6,2%.txt

REM Initialize results file
echo test_name,result,duration_s,category,timestamp > "%RESULTS_FILE%"

REM Function to run a test (simulated with labels)
goto :main

:run_test
set test_name=%~1
set test_script=%~2
set category=%~3

echo ═══════════════════════════════════════════════════════════════
echo   EJECUTANDO: %test_name%
echo ═══════════════════════════════════════════════════════════════
echo.

set /a TOTAL_TESTS+=1

if exist "%test_script%" (
    set start_time=%time%
    
    REM Run the test using Git Bash if available, otherwise skip
    where bash >nul 2>nul
    if !errorlevel! equ 0 (
        bash "%test_script%"
        if !errorlevel! equ 0 (
            echo ✅ %test_name% PASSED
            echo %test_name%,PASS,0,%category%,%date% %time% >> "%RESULTS_FILE%"
            set /a PASSED_TESTS+=1
        ) else (
            echo ❌ %test_name% FAILED
            echo %test_name%,FAIL,0,%category%,%date% %time% >> "%RESULTS_FILE%"
            set /a FAILED_TESTS+=1
        )
    ) else (
        echo ⚠ %test_name% SKIPPED (bash not available)
        echo %test_name%,SKIP,0,%category%,%date% %time% >> "%RESULTS_FILE%"
    )
) else (
    echo ⚠ %test_name% SKIPPED (script not found: %test_script%)
    echo %test_name%,SKIP,0,%category%,%date% %time% >> "%RESULTS_FILE%"
)

echo.
timeout /t 2 /nobreak >nul
goto :eof

:main

REM Performance Tests
if "%CATEGORY%"=="all" goto :run_performance
if "%CATEGORY%"=="performance" goto :run_performance
goto :skip_performance

:run_performance
echo 🚀 PERFORMANCE TESTS
echo.
call :run_test "PERF-01 Load Test Sostenido" "scripts\performance\load-test.sh" "performance"

:skip_performance

REM Concurrency Tests
if "%CATEGORY%"=="all" goto :run_concurrency
if "%CATEGORY%"=="concurrency" goto :run_concurrency
goto :skip_concurrency

:run_concurrency
echo ⚡ CONCURRENCY TESTS
echo.
call :run_test "CONC-01 Race Condition Test" "scripts\concurrency\race-condition-test.sh" "concurrency"

:skip_concurrency

REM Resilience Tests
if "%CATEGORY%"=="all" goto :run_resilience
if "%CATEGORY%"=="resilience" goto :run_resilience
goto :skip_resilience

:run_resilience
echo 🛡️ RESILIENCE TESTS
echo.
call :run_test "RES-01 Worker Crash Test" "scripts\resilience\worker-crash-test.sh" "resilience"

:skip_resilience

REM Final consistency check
echo 🔍 FINAL CONSISTENCY CHECK
echo.
if exist "scripts\utils\validate-consistency.sh" (
    where bash >nul 2>nul
    if !errorlevel! equ 0 (
        bash "scripts\utils\validate-consistency.sh"
        if !errorlevel! equ 0 (
            echo CONSISTENCY_CHECK,PASS,0,validation,%date% %time% >> "%RESULTS_FILE%"
        ) else (
            echo CONSISTENCY_CHECK,FAIL,0,validation,%date% %time% >> "%RESULTS_FILE%"
            set /a FAILED_TESTS+=1
        )
        set /a TOTAL_TESTS+=1
    )
)

REM Summary
echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                    RESUMEN FINAL                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
echo   Total Tests:    %TOTAL_TESTS%
echo   Passed:         %PASSED_TESTS%
echo   Failed:         %FAILED_TESTS%
echo.

if %FAILED_TESTS% equ 0 (
    echo   🎉 ALL TESTS PASSED!
) else (
    echo   ⚠ Some tests failed
)

echo.
echo   📁 Resultados: %RESULTS_FILE%
echo   📁 Métricas: results\
echo.

REM Exit with appropriate code
if %FAILED_TESTS% equ 0 (
    exit /b 0
) else (
    exit /b 1
)