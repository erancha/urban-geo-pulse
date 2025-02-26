@echo off

if defined LOG_FOLDER (
	echo LOG_FOLDER is %LOG_FOLDER%
	exit /b
)

REM Get the current date and time in a format that is suitable for folder names:
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set "datetime=%%I"
set "datestamp=%datetime:~0,8%"
set "timestamp=%datetime:~8,6%"
set "formatted_datetime=%datestamp:~0,4%-%datestamp:~4,2%-%datestamp:~6,2%__%timestamp:~0,2%_%timestamp:~2,2%"

REM Create the log folder:
set LOG_FOLDER="logs/%formatted_datetime%"
if not exist %LOG_FOLDER% (
	mkdir %LOG_FOLDER%
)
pushd %LOG_FOLDER%
set LOG_FOLDER="%CD%"
echo LOG_FOLDER set to %LOG_FOLDER%
popd
