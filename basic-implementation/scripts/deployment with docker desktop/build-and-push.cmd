@echo off
pushd ..\..\services\%1%
call mvnw clean compile package
if "%2"=="" (set VERSION=1.0) else (set VERSION=%2)
set IMAGE_NAME=erancha/urbangeopulse:%1-%VERSION%
echo Building and pushing %IMAGE_NAME%
docker build -t %IMAGE_NAME% -f ..\..\scripts\deployment\Dockerfile_%1 .
docker push %IMAGE_NAME%
popd
