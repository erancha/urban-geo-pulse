call ..\..\services\receiver\set-env.cmd

@echo off
setlocal enabledelayedexpansion

echo Checking environment variables...

SET JAVA_HOME

@REM Check JAVA_HOME
if not defined JAVA_HOME (
    echo Error: JAVA_HOME environment variable is not set
    echo Please set JAVA_HOME to your Java installation directory
    pause
    exit /b 1
)

@REM Check M2_HOME (Maven)
if not defined M2_HOME (
    echo Warning: M2_HOME environment variable is not set
    echo Checking for mvn in PATH...
    where mvn >nul 2>nul
    if !errorlevel! neq 0 (
        echo Error: Maven is not found in PATH
        echo Please install Maven and set M2_HOME or add mvn to PATH
        pause
        exit /b 1
    )
)

@REM Change to the script directory
cd /d "%~dp0"

@REM Navigate to the workspace directory (relative path)
cd ..\..\workspace

echo Building Maven project...
echo Current directory: %CD%

@REM Try to use M2_HOME if set, otherwise use mvn from PATH
if defined M2_HOME (
    if exist "!M2_HOME!\bin\mvn.cmd" (
        echo Using Maven from M2_HOME: !M2_HOME!
        call "!M2_HOME!\bin\mvn.cmd" clean install
    ) else (
        echo Warning: mvn.cmd not found in M2_HOME, falling back to PATH
        call mvn clean install
    )
) else (
    call mvn clean install
)

if !errorlevel! neq 0 (
    echo Error: Maven build failed
    pause
    exit /b !errorlevel!
)

echo Build completed successfully
pause
exit /b 0
