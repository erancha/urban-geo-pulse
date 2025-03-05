@echo off

call 3rdparty-up.cmd

@REM Initialize environment
call set-sql-env.cmd
call set-log-env.cmd

@REM Start services in order
call start-a-service receiver                
call start-a-service mobilization-classifier
call start-a-service locations-finder
call start-a-service delay-manager
call start-a-service activity-aggregator        openlog
call start-a-service info

@REM Wait for services to initialize
timeout /t 5 >nul
