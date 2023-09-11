package com.urbangeopulse.info.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Component
public class InfoDataService {

    private final static Logger logger = Logger.getLogger(InfoDataService.class.getName());

    private final JdbcTemplate jdbcTemplate;

    public InfoDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param startTimestamp - start time stamp.
     * @param endTimestamp   - end time stamp.
     * @param locationType   - 'street', 'neighborhood' or 'borough'.
     * @param sortBy         - 'pedestrians' or 'mobilized'.
     * @param recordsCount   - number of records to return.
     * @return the first most active 'recordsCount' streets or neighborhoods (depending on 'locationType') between timestamps 'startTimestamp' and 'endTimestamp', sorted by 'sortBy'.
     */
    public List<Map<String, Object>> getActiveLocations(Timestamp startTimestamp, Timestamp endTimestamp, String locationType, String sortBy, short recordsCount) {
        final String query = String.format("with agg_streets_activity_sum as\n" +
                "\t(\n" +
                "\t\tselect \n" +
                "\t\t\tname,\n" +
                "\t\t\tsum(asa.pedestrians_count) as sum_pedestrians_count,\n" +
                "\t\t\tsum(asa.mobilized_count) as sum_mobilized_count\n" +
                "\t\tfrom agg_%ss_activity asa\n" +
                "\t\tjoin nyc_%ss ns on asa.%s_gid = ns.gid\n" +
                "\t\twhere\n" +
                "\t\t\ttimestamp_in_sec between '%s' and '%s'\n" +
                "\t\tgroup by name\n" +
                "\t)\n" +
                "select name,sum_pedestrians_count,sum_mobilized_count from agg_streets_activity_sum asas\n" +
                "order by \n" +
                "\tsum_%s_count desc,name\n" +
                "limit %d\n" +
                ";\n", locationType, locationType, locationType, startTimestamp, endTimestamp, sortBy, recordsCount);
        return jdbcTemplate.queryForList(query);
    }
}