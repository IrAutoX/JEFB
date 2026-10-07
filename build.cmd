@echo off
rem ============================================================
rem  JEFB self-hosting build script (Windows)
rem  Builds JEFB without Gradle: javac -> jar packaging.
rem  Usage: build.cmd [clean]
rem ============================================================
setlocal enabledelayedexpansion

rem -- Locate a JDK (17+) --------------------------------------
set "JDK=%JAVA_HOME%"
if "%JDK%"=="" set "JDK=C:\Program Files\Java\jdk-21"
if not exist "%JDK%\bin\javac.exe" (
    for /d %%D in ("C:\Program Files\Java\jdk-*") do set "JDK=%%~D"
)
if not exist "%JDK%\bin\javac.exe" (
    echo [ERROR] No JDK 17+ found. Set JAVA_HOME or install a JDK.
    exit /b 1
)
echo [JEFB] Using JDK: %JDK%
set "JAVAC=%JDK%\bin\javac.exe"
set "JAR=%JDK%\bin\jar.exe"

rem -- Classpath: bundled libraries ----------------------------
set "CP="
for %%J in (lib\*.jar) do (
    if "!CP!"=="" (set "CP=%%J") else (set "CP=!CP!;%%J")
)

rem -- Clean ----------------------------------------------------
if /i "%~1"=="clean" rmdir /s /q build 2>nul
if exist build\classes rmdir /s /q build\classes
if exist build\fat rmdir /s /q build\fat
mkdir build\classes 2>nul
mkdir build\fat 2>nul
if errorlevel 1 goto :fail

rem -- Compile --------------------------------------------------
echo [JEFB] Compiling sources...
"%JAVAC%" -encoding UTF-8 --release 17 -d build\classes -cp "%CP%" src\main\java\ir\IrAutoX\JEFB\*.java || goto :fail

rem -- Stage fat JAR contents ----------------------------------
echo [JEFB] Staging classes and resources...
xcopy build\classes\* build\fat\ /E /I /Y /Q >nul || goto :fail
if exist icons xcopy icons\* build\fat\icons\ /E /I /Y /Q >nul
if exist resources xcopy resources\* build\fat\resources\ /E /I /Y /Q >nul

rem -- Merge dependency JARs ------------------------------------
for %%J in (lib\*.jar) do (
    echo [JEFB] Merging %%J
    pushd build\fat
    "%JAR%" xf "..\..\%%J" || (popd & goto :fail)
    popd
)
if exist build\fat\META-INF\MANIFEST.MF del build\fat\META-INF\MANIFEST.MF 2>nul
del /s /q build\fat\META-INF\*.SF build\fat\META-INF\*.RSA build\fat\META-INF\*.DSA 2>nul

rem -- Package --------------------------------------------------
echo [JEFB] Packaging build\JEFB.jar ...
"%JAR%" --create --file build\JEFB.jar --main-class ir.IrAutoX.JEFB.JBuild --date "2026-01-01T00:00:00Z" -C build\fat . || goto :fail

echo [JEFB] BUILD SUCCESSFUL
for %%F in (build\JEFB.jar) do echo [JEFB] Output: %%~zF bytes - build\JEFB.jar
exit /b 0

:fail
echo [JEFB] BUILD FAILED
exit /b 1
