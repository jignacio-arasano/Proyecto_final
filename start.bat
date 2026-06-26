@echo off
cd /d "%~dp0"
echo ============================================================
echo  Sistema de gestion de rentabilidad - Iniciando...
echo ============================================================
echo.
echo  Base de datos: Supabase (PostgreSQL en la nube)
echo  Los datos persisten entre reinicios. Requiere conexion a internet.
echo.

echo [1/3] Iniciando predictor Python (puerto 5001)...
start "Predictor" cmd /k "cd /d "%~dp0predictor" && python app.py"

echo [2/3] Iniciando backend Spring Boot (puerto 8080) con Supabase...
start "Backend" cmd /k "cd /d "%~dp0backend" && mvnw.cmd spring-boot:run"

echo [3/3] Iniciando frontend React (puerto 5173)...
start "Frontend" cmd /k "cd /d "%~dp0frontend" && npm run dev"

echo.
echo ============================================================
echo  Abre la ventana "Backend" y espera ver:
echo  "Started BackendApplication in X seconds"
echo  Recien ahi abre: http://localhost:5173
echo  (La app entra directo, sin login)
echo ============================================================
pause
