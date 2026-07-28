@echo off
setlocal

cd /d "%~dp0"

set "PYTHON_EXE=C:\Python313\python.exe"
if exist "%PYTHON_EXE%" goto run_editor

where python >nul 2>nul
if %errorlevel%==0 (
    set "PYTHON_EXE=python"
    goto run_editor
)

echo Python was not found.
echo Tried: C:\Python313\python.exe
echo Also tried: python on PATH
echo.
pause
exit /b 1

:run_editor
echo Starting VeeDash PC Editor...
echo Folder: %CD%
echo Python: %PYTHON_EXE%
echo.
"%PYTHON_EXE%" "VeeDash-PC-Editor.py"
if errorlevel 1 (
    echo.
    echo VeeDash PC Editor stopped with an error.
    pause
)
