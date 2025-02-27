    @REM Input topic(s) of the current service , output topic(s) of the previous service in the pipeline 
    @REM The value is the topic name , number of partitions.
    @REM (each previous service has its own defaults - the current variables define the topics for the needs of current service).

    set PEDESTRIANS_STREETS_TOPIC=pedestrians_streets,5
    set PEDESTRIANS_NEIGHBORHOODS_TOPIC=pedestrians_neighborhoods,4

    set MOBILIZED_STREETS_TOPIC=mobilized_streets,3
    set MOBILIZED_NEIGHBORHOODS_TOPIC=mobilized_neighborhoods,2