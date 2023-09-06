	set SERVICE_NAME=%1%
	powershell -command "Get-WmiObject Win32_Process | Where-Object CommandLine -like '*%SERVICE_NAME%*' | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"