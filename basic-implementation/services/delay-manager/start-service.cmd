	@echo off

	@REM set EXECUTION_COMMAND=mvnw spring-boot:run --quiet
	set EXECUTION_COMMAND=java -Xms128M -jar target\delay-manager-1.0.jar 

	call set-env.cmd
	@REM set DELAY_MANAGER_AUTO_OFFSET_RESET_CONFIG=earliest
	@REM set DELAY_MANAGER_SESSION_TIMEOUT_SECONDS_CONFIG=120

	@echo on

	for /l %%i in (1,1,1) do start /B  %EXECUTION_COMMAND%
