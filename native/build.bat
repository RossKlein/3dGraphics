@echo off
REM Build the rmlui_java native library and place it in natives/
REM Run from the repo root or from native/

setlocal
set SCRIPT_DIR=%~dp0
set BUILD_DIR=%SCRIPT_DIR%build-windows

echo [rmlui-java] Configuring...
cmake -S "%SCRIPT_DIR%" -B "%BUILD_DIR%" ^
    -DCMAKE_BUILD_TYPE=Release

echo [rmlui-java] Building...
cmake --build "%BUILD_DIR%" --config Release --parallel

echo [rmlui-java] Done - rmlui_java.dll written to natives/
endlocal
