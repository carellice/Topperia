@echo off
setlocal
set "TOPPERIA_SCRIPT=%~dp0scripts\release.ps1"
if not exist "%TOPPERIA_SCRIPT%" (
    echo ERRORE: scripts\release.ps1 non trovato.
    exit /b 1
)
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%TOPPERIA_SCRIPT%" %*
set "TOPPERIA_RESULT=%ERRORLEVEL%"
if not "%TOPPERIA_RESULT%"=="0" (
    echo.
    echo Rilascio interrotto. Leggi il messaggio sopra prima di riprovare.
)
if "%~1"=="" (
    echo.
    pause
)
exit /b %TOPPERIA_RESULT%
