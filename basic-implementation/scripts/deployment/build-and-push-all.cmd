	call build-and-push receiver
	call build-and-push mobilization-classifier
	call build-and-push locations-finder
	call build-and-push activity-aggregator
	call build-and-push info

	call deploy.cmd

	REM pause
	timeout /t 5 >nul