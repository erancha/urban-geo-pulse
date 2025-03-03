@REM Input topic(s) of the current service , output topic(s) of the previous service in the pipeline.
@REM The format is: `topic name , number of partitions`.

@REM Eeach previous service has its own defaults for its output topic(s); 
@REM The current variables define the input topic(s) optimized for the needs of current service - 
@REM     the number of partitions is reused as the number of consumer threads, to ensure that no consumer is left idle without a partition).

set PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,8