package com.urbangeopulse.info.services;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
@Profile("postgres")
public class InfoPostgresDataService implements InfoDataService {

    private final static Logger logger = Logger.getLogger(InfoPostgresDataService.class.getName());

    private final JdbcTemplate jdbcTemplate;

    public InfoPostgresDataService(JdbcTemplate jdbcTemplate) {
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
    @Override
    public List<Map<String, Object>> getActiveLocations(
            Timestamp startTimestamp,
            Timestamp endTimestamp,
            String locationType,
            String sortBy,
            Short recordsCount) {

        List<Object> params = new ArrayList<>();
        String whereClause = "timestamp_in_sec between ? and ?";
        params.add(startTimestamp);
        params.add(endTimestamp);

        final String query = String.format(
                "with agg_activity_sum as (\n" +
                "   select \n" +
                "      name,\n" +
                "      sum(asa.pedestrians_count) as sum_pedestrians_count,\n" +
                "      sum(asa.mobilized_count) as sum_mobilized_count\n" +
                "   from\n" +
                "      agg_%ss_activity asa\n" +
                "   join nyc_%ss ns on\n" +
                "      asa.%s_gid = ns.gid\n" +
                "   where\n" +
                "      %s\n" +
                "   group by \n" +
                "      name\n" +
                ")\n" +
                "select\n" +
                "   name,\n" +
                "   sum_pedestrians_count,\n" +
                "   sum_mobilized_count\n" +
                "from\n" +
                "   agg_activity_sum\n" +
                "order by \n" +
                "   sum_%s_count desc,\n" +
                "   name\n" +
                "limit ?",
                locationType, locationType, locationType,
                whereClause,
                sortBy);
        params.add(recordsCount);

        return jdbcTemplate.queryForList(query, params.toArray());
    }
}