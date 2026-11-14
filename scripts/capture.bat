@echo off
setlocal
if "%~1"=="" goto :usage
set "CAPTURE_NAME=%~1"
rem Accept a filename stem only, never a path or shell metacharacters.
powershell -NoProfile -Command "if ($env:CAPTURE_NAME -notmatch '\A[A-Za-z0-9_-]+\z') { exit 1 }"
if errorlevel 1 goto :usage
pushd "%~dp0.."
if errorlevel 1 exit /b 1
if not exist "dumps" mkdir "dumps"
if not exist "shots" mkdir "shots"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0capture-redacted.ps1" -Name "%CAPTURE_NAME%"
if errorlevel 1 goto :failed
popd
exit /b 0

:failed
popd
exit /b 1

:usage
echo Usage: scripts\capture.bat name ^(letters, numbers, underscore, hyphen only^)
exit /b 1
