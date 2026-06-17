@echo off
setlocal EnableDelayedExpansion
REM Run Main with the same classpath IntelliJ uses: compiled classes + all LWJGL jars.
REM Edit LWJGL_DIR if your jars live elsewhere (must match .vscode/settings.json).

set "ROOT=%~dp0.."
set "OUT=%ROOT%\out\production\Graphicsv3"
set "LWJGL_DIR=C:\winbin\lwjgl-opengl-new"
set "NATIVES=%ROOT%\natives"

set "CP=%OUT%"
for %%f in ("%LWJGL_DIR%\*.jar") do set "CP=!CP!;%%f"

if not exist "%OUT%\Ross\Instance\Main.class" (
  echo ERROR: No compiled classes in "%OUT%".
  echo Build in VS Code ^(Java: Force Java Compilation^) or IntelliJ first.
  exit /b 1
)

java -Djava.library.path="%NATIVES%" -cp "%CP%" Ross.Instance.Main %*
