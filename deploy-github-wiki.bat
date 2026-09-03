@echo off
chcp 65001 >nul
setlocal ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION

REM ============================================================================
REM   VAULT 2.0 · DESPLIEGUE AUTOMÁTICO DE WIKI A GITHUB  (Windows .bat)
REM   Repo destino: shalom25/Vault2.0   =>  Wiki: shalom25/Vault2.0.wiki.git
REM ============================================================================
REM   FORMA MÁS FÁCIL DE USAR (NO necesitas PAT):
REM   1) Instala GitHub CLI desde https://cli.github.com  y reinicia el PC
REM   2) Abre una consola y escribe:   gh auth login   (elige HTTPS, browser)
REM   3) Haz doble clic a este .BAT. Listo.
REM
REM   FORMA ALTERNATIVA con Personal Access Token:
REM   1) Crea tu PAT aquí: https://github.com/settings/tokens?type=beta
REM      - Scope "repo" (COMPLETO) o al menos "repo:public_repo" + "wiki"
REM      - Expiración: 90 días recomendado
REM   2) Cuando este .BAT te pregunte, pégalo y pulsa ENTER
REM ============================================================================

cd /d "%~dp0"
set "BUILD_DIR=%~dp0.github-wiki-build"
set "WIKI_REPO=https://github.com/shalom25/Vault2.0.wiki.git"
set "COMMIT_MSG=Wiki Vault 2.1.0 - %date:~-4%-%date:~3,2%-%date:~0,2% %time:~0,2%:%time:~3,2%"

echo.
echo ============================================================
echo   VAULT 2.0 · DESPLEGANDO WIKI EN GITHUB
echo   Destino: %WIKI_REPO%
echo ============================================================
echo.

REM --- Comprobamos que existan los archivos MD aplanados ---
if not exist "%BUILD_DIR%\Home.md" (
    echo [ERROR] No existe .github-wiki-build\Home.md. Ejecuta primero:
    echo         powershell -ExecutionPolicy Bypass -File prebuild-wiki.ps1
    pause
    exit /b 1
)
set /a N=0
for %%F in ("%BUILD_DIR%\*.md") do set /a N+=1
echo [OK] Encontrados %N% archivos .md en .github-wiki-build\
echo.

REM --- 1) Saber si está gh CLI autenticado (MÉTODO PREFERIDO) ---
where gh >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [INFO] Detectado GitHub CLI. Comprobando login...
    gh auth status >nul 2>nul
    if !ERRORLEVEL! EQU 0 (
        echo [OK] Ya estas autenticado via "gh auth login". Usando credenciales gh CLI.
        goto :DO_DEPLOY
    ) else (
        echo [AVISO] gh CLI instalado pero NO has hecho "gh auth login".
    )
)

REM --- 2) Alternativa: Pedir PAT interactivo ---
echo.
echo ----------------------------------------------------------
echo   NECESITAMOS AUTENTICACION
echo ----------------------------------------------------------
echo Opcion A (recomendado): cierra esta ventana, instala GH CLI:
echo            https://cli.github.com
echo            luego ejecuta: gh auth login
echo            y vuelve a abrir este BAT.
echo.
echo Opcion B: usa Personal Access Token (PAT).
echo   Crear PAT: https://github.com/settings/tokens?type=beta
echo   Scopes: marcar "repo" (todo) o al menos "repo:public_repo" + "wiki"
echo ----------------------------------------------------------
echo.
set "GITHUB_TOKEN="
set /p "GITHUB_TOKEN=Pega tu PAT aqui (o pulsa ENTER para cancelar): "
if "%GITHUB_TOKEN%"=="" (
    echo Cancelado por el usuario.
    pause
    exit /b 0
)
set "WIKI_REPO=https://shalom25:%GITHUB_TOKEN%@github.com/shalom25/Vault2.0.wiki.git"

:DO_DEPLOY
echo.
echo [1/4] Clonando la wiki actual de GitHub a una carpeta temporal...
if exist "%TEMP%\vault-wiki-deploy" rmdir /s /q "%TEMP%\vault-wiki-deploy"
git clone --depth 1 "%WIKI_REPO%" "%TEMP%\vault-wiki-deploy"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] git clone FALLO. Causas comunes:
    echo   - Token PAT invalido o sin scopes "repo/wikis"
    echo   - Aun NO has activado la Wiki del repo! Ve a:
    echo     https://github.com/shalom25/Vault2.0  -> Settings -> Features
    echo     y marca la casilla  [x] Wiki   !!!
    echo   - Mala conexion a internet.
    echo.
    pause
    exit /b 2
)

echo.
echo [2/4] Limpiando Wiki actual y copiando la nueva build...
cd /d "%TEMP%\vault-wiki-deploy"
REM Borramos todos los .md antiguos menos .git/
for %%F in (*.md *.MD *.png *.jpg *.gif *.svg *.jpeg) do if /i not "%%~nxF"==".gitkeep" del /q "%%F" 2>nul
REM Copiamos TODOS los MD + recursos
xcopy /E /I /Y /Q "%BUILD_DIR%\*" "%TEMP%\vault-wiki-deploy\" >nul

echo.
echo [3/4] git add + commit...
git add -A
git status --porcelain | findstr . >nul
if %ERRORLEVEL% NEQ 0 (
    echo [INFO] No hay cambios respecto a la Wiki actual (todo ya estaba igual).
    goto :PULL_NEXT
)
git -c core.autocrlf=false commit -m "%COMMIT_MSG%" --no-verify
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Fallo al hacer commit.
    pause
    exit /b 3
)

echo.
echo [4/4] git push a la wiki de GitHub...
git push origin master 2>nul
if %ERRORLEVEL% NEQ 0 (
    git push origin main 2>nul
)
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] git push FALLO. Revisa:
    echo   - Permisos escritura del PAT (scope repo completo).
    echo   - Nombre de rama (master o main). En el error anterior lo indica.
    pause
    exit /b 4
)

:PULL_NEXT
rmdir /s /q "%TEMP%\vault-wiki-deploy" 2>nul
echo.
echo ============================================================
echo   ✅ DESPLIEGUE TERMINADO CORRECTAMENTE
echo ============================================================
echo   Tu Wiki esta aqui:
echo   https://github.com/shalom25/Vault2.0/wiki
echo.
pause
endlocal
exit /b 0
