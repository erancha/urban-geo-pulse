	xcopy /d src\main\java\com\urbangeopulse\utils\kafka\* ..\receiver\src\main\java\com\urbangeopulse\utils\kafka
	xcopy /d src\main\java\com\urbangeopulse\utils\kafka\* ..\locations-finder\src\main\java\com\urbangeopulse\utils\kafka
	xcopy /d src\main\java\com\urbangeopulse\utils\kafka\* ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\kafka

	xcopy /d ..\locations-finder\src\main\java\com\urbangeopulse\utils\kafka\* ..\receiver\src\main\java\com\urbangeopulse\utils\kafka
	xcopy /d ..\locations-finder\src\main\java\com\urbangeopulse\utils\kafka\* ..\mobilization-sorter\src\main\java\com\urbangeopulse\utils\kafka
	xcopy /d ..\locations-finder\src\main\java\com\urbangeopulse\utils\kafka\* ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\kafka

	xcopy /d ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\kafka\* ..\receiver\src\main\java\com\urbangeopulse\utils\kafka
	xcopy /d ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\kafka\* ..\mobilization-sorter\src\main\java\com\urbangeopulse\utils\kafka
	xcopy /d ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\kafka\* ..\locations-finder\src\main\java\com\urbangeopulse\utils\kafka
	
	xcopy /d ..\receiver\src\main\java\com\urbangeopulse\utils\kafka\* ..\mobilization-sorter\src\main\java\com\urbangeopulse\utils\kafka
	xcopy /d ..\receiver\src\main\java\com\urbangeopulse\utils\kafka\* ..\locations-finder\src\main\java\com\urbangeopulse\utils\kafka
	xcopy /d ..\receiver\src\main\java\com\urbangeopulse\utils\kafka\* ..\activity-aggregator\src\main\java\com\urbangeopulse\utils\kafka
	
	REM pause
	timeout /t 5 >nul