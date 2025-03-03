@echo off
echo Removing application services...

docker stack rm app
timeout /t 20 >nul

echo.
echo Application services removed.
timeout /t 5 >nul
