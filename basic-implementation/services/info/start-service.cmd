	@echo off

	REM set EXECUTION_COMMAND=mvnw spring-boot:run --quiet
	set EXECUTION_COMMAND=java -Xms128M -jar target\info-1.0.jar 

	call set-env.cmd
	
	@echo on

	start /B %EXECUTION_COMMAND%
