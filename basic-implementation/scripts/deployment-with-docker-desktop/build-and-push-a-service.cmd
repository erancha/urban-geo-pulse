@echo off
setlocal EnableDelayedExpansion
pushd ..\..\services\%1%
call mvnw clean compile package
if "%2"=="" (set VERSION=1.0) else (set VERSION=%2)
set IMAGE_NAME=erancha/urbangeopulse:%1-%VERSION%
echo Building and pushing %IMAGE_NAME%
docker build -t "%IMAGE_NAME%" -f ..\..\scripts\deployment-with-docker-desktop\Dockerfile_%1 .
if not "%3"=="" (
    set ADDITIONAL_TAG=erancha/urbangeopulse:%1-%3
    echo Also tagging as !ADDITIONAL_TAG!
    docker tag "%IMAGE_NAME%" "!ADDITIONAL_TAG!"
    docker push "!ADDITIONAL_TAG!"
)
docker push "%IMAGE_NAME%"
popd
endlocal
