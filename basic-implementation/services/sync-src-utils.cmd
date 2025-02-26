	@echo off
	set from=%1
	echo From %from%
	
	xcopy /scdy %from%\src\main\java\com\urbangeopulse\utils\* receiver\src\main\java\com\urbangeopulse\utils\
	xcopy /scdy %from%\src\main\java\com\urbangeopulse\utils\* mobilization-classifier\src\main\java\com\urbangeopulse\utils\
	xcopy /scdy %from%\src\main\java\com\urbangeopulse\utils\* locations-finder\src\main\java\com\urbangeopulse\utils\
	xcopy /scdy %from%\src\main\java\com\urbangeopulse\utils\* delay-manager\src\main\java\com\urbangeopulse\utils\
	xcopy /scdy %from%\src\main\java\com\urbangeopulse\utils\* activity-aggregator\src\main\java\com\urbangeopulse\utils\
