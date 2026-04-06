@echo off
REM Build the rmlui_java native library and place it in natives/
REM Run from the repo root or from native/

setlocal
pushd "%~dp0"
set BUILD_DIR=build-windows
set CMAKE_ARGS=-DCMAKE_BUILD_TYPE=Release

REM Prefer explicitly configured VCPKG_ROOT, fallback to user's default install path.
if defined VCPKG_ROOT (
    set CMAKE_ARGS=%CMAKE_ARGS% -DCMAKE_TOOLCHAIN_FILE="%VCPKG_ROOT%\scripts\buildsystems\vcpkg.cmake" -DVCPKG_TARGET_TRIPLET=x64-windows
) else (
    if exist "C:\Users\%USERNAME%\vcpkg\scripts\buildsystems\vcpkg.cmake" (
        set CMAKE_ARGS=%CMAKE_ARGS% -DCMAKE_TOOLCHAIN_FILE="C:\Users\%USERNAME%\vcpkg\scripts\buildsystems\vcpkg.cmake" -DVCPKG_TARGET_TRIPLET=x64-windows
    )
)

echo [rmlui-java] Configuring...
cmake -S . -B "%BUILD_DIR%" %CMAKE_ARGS%
if errorlevel 1 (
    echo [rmlui-java] Configure failed.
    popd
    exit /b 1
)

echo [rmlui-java] Building...
cmake --build "%BUILD_DIR%" --config Release --parallel
if errorlevel 1 (
    echo [rmlui-java] Build failed.
    popd
    exit /b 1
)

echo [rmlui-java] Done - rmlui_java.dll written to natives/
popd
endlocal
