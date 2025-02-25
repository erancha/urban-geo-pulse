	cd ../../services/%1%
	call mvnw clean compile package
	set IMAGE_NAME=erancha/urbangeopulse:%1%-1.0
	docker build -t %IMAGE_NAME% -f ../../scripts/deployment/Dockerfile_%1% .
	docker push %IMAGE_NAME%
	cd ../../scripts/deployment