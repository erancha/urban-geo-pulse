    @REM Input topic(s) of the current service , output topic(s) of the previous service in the pipeline 
    @REM The value is the topic name , number of partitions.
    @REM (each previous service has its own defaults - the current variables define the topics for the needs of current service).

    set PEDESTRIANS_GEO_LOCATIONS_TOPIC=pedestrians_geo_locations,4
    set MOBILIZED_GEO_LOCATIONS_TOPIC=mobilized_geo_locations,3