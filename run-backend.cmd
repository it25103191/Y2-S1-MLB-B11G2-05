@echo off
REM ---------------------------------------------------------------------------
REM Starts the Spring Boot API on http://localhost:8080
REM
REM This machine had neither Maven nor a JDK 17 when the project was set up, so
REM both were installed under %USERPROFILE%\devtools. Adjust the two paths below
REM if you move them or already have your own.
REM ---------------------------------------------------------------------------

setlocal

if "%JAVA_HOME%"=="" set "JAVA_HOME=%USERPROFILE%\devtools\jdk-17.0.20+8"
set "MAVEN_HOME=%USERPROFILE%\devtools\apache-maven-3.9.9"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JDK 17 not found at %JAVA_HOME%
  echo         Install a JDK 17 and set JAVA_HOME, then run this again.
  exit /b 1
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo [ERROR] Maven not found at %MAVEN_HOME%
  exit /b 1
)

cd /d "%~dp0backend"
echo Starting Safari TMS API on http://localhost:8080 ...
call mvn spring-boot:run

endlocal
