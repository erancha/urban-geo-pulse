	set POSTGIS_SERVER_HOST_NAME=localhost
	set POSTGIS_SERVER_PORT=5433
	set PEOPLE_GEO_LOCATIONS_TOPIC_NAME=people_geo_locations
	set MOBILIZATION_CLASSIFIER_CONSUMER_THREADS_COUNT=20
	@REM Note! MOBILIZATION_CLASSIFIER_CONSUMER_THREADS_COUNT is also used as a number of partitions for PEOPLE_GEO_LOCATIONS_TOPIC_NAME ('partitions count' > 'consumers count' will lead to idle consumers).
