@echo off
REM ---------------------------------------------------------------------------
REM Creates the database (if it is not there yet) and starts the Spring Boot API
REM on http://localhost:8080, for a PC whose SQL Server uses a SQL login (sa)
REM instead of Windows authentication. It asks for the password when it starts,
REM so no password is stored in this file or in the repository.
REM
REM Edit the settings below if your server, port or login are different.
REM ---------------------------------------------------------------------------

setlocal

REM ---- SQL Server settings -----------------------------------------------------
REM localhost works when this runs on the same PC as SQL Server
REM (the computer name, e.g. DESKTOP-BPF7P7K, works too).
set "SQL_HOST=localhost"
set "SQL_PORT=1433"
set "SQL_USER=sa"
REM Leave empty to be asked each time.
set "SQL_PASSWORD="

REM A new, empty database. The API creates the tables and fills them with demo
REM data on the first start. An existing database with this name is reused.
set "DB_NAME=ceylon_trails"

if not defined SQL_PASSWORD set /p "SQL_PASSWORD=SQL Server password for %SQL_USER%: "

REM ---- 1. Find Java and Maven ----------------------------------------------------
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
if not defined JAVA_HOME if exist "%USERPROFILE%\devtools\jdk-17.0.20+8\bin\java.exe" (
  set "JAVA_HOME=%USERPROFILE%\devtools\jdk-17.0.20+8"
  set "PATH=%USERPROFILE%\devtools\jdk-17.0.20+8\bin;%PATH%"
)
where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java was not found. Install JDK 17 and set JAVA_HOME, then run this again.
  goto :fail
)

where mvn >nul 2>nul
if errorlevel 1 if exist "%USERPROFILE%\devtools\apache-maven-3.9.9\bin\mvn.cmd" set "PATH=%USERPROFILE%\devtools\apache-maven-3.9.9\bin;%PATH%"
where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Maven was not found. Install Maven 3.9+ and add its bin folder to PATH,
  echo         or open the backend folder in IntelliJ and run SafariTmsApplication there
  echo         with SAFARI_DB_URL, SAFARI_DB_USER and SAFARI_DB_PASSWORD set as in this file.
  goto :fail
)

echo Using Java - it must be version 17 to 22; JDK 24 does not work with Spring Boot 3.3:
java -version 2>&1 | findstr /i "version"
echo.

REM ---- 2. Create the database if it does not exist ------------------------------
REM Uses PowerShell's built-in SQL client, so sqlcmd does not need to be installed.
echo Checking database %DB_NAME% on %SQL_HOST%,%SQL_PORT% ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $c = New-Object System.Data.SqlClient.SqlConnection('Server=%SQL_HOST%,%SQL_PORT%;Database=master;User Id=%SQL_USER%;Password=%SQL_PASSWORD%;TrustServerCertificate=True;Connect Timeout=10'); $c.Open(); $q = $c.CreateCommand(); $q.CommandText = 'IF DB_ID(''%DB_NAME%'') IS NULL BEGIN CREATE DATABASE [%DB_NAME%]; SELECT 1 END ELSE SELECT 0'; $created = $q.ExecuteScalar(); $c.Close(); if ($created -eq 1) { Write-Host 'Created database %DB_NAME%.' } else { Write-Host 'Database %DB_NAME% already exists - using it.' } } catch { Write-Host ('[ERROR] ' + $_.Exception.Message); exit 1 }"
if errorlevel 1 (
  echo.
  echo Could not reach SQL Server. Check that:
  echo   - the SQL Server service is running
  echo   - TCP/IP is enabled on port %SQL_PORT% in SQL Server Configuration Manager
  echo   - the sa login is enabled and the password above is right
  goto :fail
)
echo.

REM ---- 3. Start the API against that database ----------------------------------
set "SAFARI_DB_URL=jdbc:sqlserver://%SQL_HOST%:%SQL_PORT%;databaseName=%DB_NAME%;encrypt=true;trustServerCertificate=true"
set "SAFARI_DB_USER=%SQL_USER%"
set "SAFARI_DB_PASSWORD=%SQL_PASSWORD%"

cd /d "%~dp0backend"
echo Starting Safari TMS API on http://localhost:8080 (database %DB_NAME%) ...
call mvn spring-boot:run
goto :end

:fail
echo.
pause
exit /b 1

:end
endlocal
