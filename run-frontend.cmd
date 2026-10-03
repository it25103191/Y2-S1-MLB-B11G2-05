@echo off
REM Starts the React dev server on http://localhost:5173

setlocal
cd /d "%~dp0frontend"

if not exist node_modules (
  echo Installing dependencies...
  call npm install --no-audit --no-fund
)

echo Starting Sundara Safari UI on http://localhost:5173 ...
call npm run dev

endlocal
