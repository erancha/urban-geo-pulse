	@echo off
	REM call npm install -g markdown-toc
	
	@echo on
	call markdown-toc -i architecture-document-phase-1-REST.md
		
	REM @echo on
	REM call markdown-toc -i architecture-document-phase-2-MQTT.md
	
	@echo on
	timeout /t 3 >nul
	REM pause