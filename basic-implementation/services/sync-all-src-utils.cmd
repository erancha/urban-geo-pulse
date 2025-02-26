	call sync-src-utils.cmd receiver
	call sync-src-utils.cmd mobilization-classifier
	call sync-src-utils.cmd locations-finder
	call sync-src-utils.cmd delay-manager
	call sync-src-utils.cmd activity-aggregator

	timeout /t 5 >nul