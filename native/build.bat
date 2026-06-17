@echo off
REM Build the rmlui_java native library and place it in natives/
REM Run from the repo root or from native/

setlocal
pushd "%~dp0"
set BUILD_DIR=build-windows
set CMAKE_ARGS=-DCMAKE_BUILD_TYPE=Release

set CMAKE_ARGS=%CMAKE_ARGS% -DCMAKE_TOOLCHAIN_FILE="%VCPKG_ROOT%\scripts\buildsystems\vcpkg.cmake" -DVCPKG_TARGET_TRIPLET=x64-windows
set CMAKE_ARGS=%CMAKE_ARGS% -DFreetype_ROOT="%VCPKG_ROOT%\installed\x64-windows"
set CMAKE_ARGS=%CMAKE_ARGS% -DFREETYPE_INCLUDE_DIR_ft2build="%VCPKG_ROOT%\installed\x64-windows\include"
set CMAKE_ARGS=%CMAKE_ARGS% -DFREETYPE_INCLUDE_DIR_freetype2="%VCPKG_ROOT%\installed\x64-windows\include\freetype2"
set CMAKE_ARGS=%CMAKE_ARGS% -DFREETYPE_LIBRARY_RELEASE="%VCPKG_ROOT%\installed\x64-windows\lib\freetype.lib"
set CMAKE_ARGS=%CMAKE_ARGS% -DFREETYPE_LIBRARY_DEBUG="%VCPKG_ROOT%\installed\x64-windows\debug\lib\freetyped.lib"


echo [rmlui-java] Configuring...
if exist "%BUILD_DIR%\CMakeCache.txt" del /f /q "%BUILD_DIR%\CMakeCache.txt"
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
