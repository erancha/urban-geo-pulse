	xcopy /d src\main\java\com\urbangeopulse\utils\* ..\receiver\src\main\java\com\urbangeopulse\utils
	xcopy /d src\main\java\com\urbangeopulse\utils\* ..\locations-finder\src\main\java\com\urbangeopulse\utils
	xcopy /d src\main\java\com\urbangeopulse\utils\* ..\delay-manager\src\main\java\com\urbangeopulse\utils
	xcopy /d src\main\java\com\urbangeopulse\utils\* ..\activity-aggregator\src\main\java\com\urbangeopulse\utils
	xcopy /d src\main\java\com\urbangeopulse\utils\* ..\general-commands-executor\src\main\java\com\urbangeopulse\utils

	xcopy /d ..\locations-finder\src\main\java\com\urbangeopulse\utils\* ..\receiver\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\locations-finder\src\main\java\com\urbangeopulse\utils\* ..\delay-manager\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\locations-finder\src\main\java\com\urbangeopulse\utils\* ..\mobilization-sorter\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\locations-finder\src\main\java\com\urbangeopulse\utils\* ..\activity-aggregator\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\locations-finder\src\main\java\com\urbangeopulse\utils\* ..\general-commands-executor\src\main\java\com\urbangeopulse\utils

	xcopy /d ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\* ..\receiver\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\* ..\mobilization-sorter\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\* ..\locations-finder\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\* ..\delay-manager\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\* ..\general-commands-executor\src\main\java\com\urbangeopulse\utils
	
	xcopy /d ..\receiver\src\main\java\com\urbangeopulse\utils\* ..\mobilization-sorter\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\receiver\src\main\java\com\urbangeopulse\utils\* ..\locations-finder\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\receiver\src\main\java\com\urbangeopulse\utils\* ..\delay-manager\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\receiver\src\main\java\com\urbangeopulse\utils\* ..\activity-aggregator\src\main\java\com\urbangeopulse\utils
	xcopy /d ..\receiver\src\main\java\com\urbangeopulse\utils\* ..\general-commands-executor\src\main\java\com\urbangeopulse\utils
	
	REM pause
	timeout /t 5 >nul