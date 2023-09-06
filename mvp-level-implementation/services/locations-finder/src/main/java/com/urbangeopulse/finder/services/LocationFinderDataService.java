package com.urbangeopulse.finder.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * data required by the service.
 */
@Component
public class LocationFinderDataService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Logger logger = Logger.getLogger(LocationFinderDataService.class.getName());

    /**
     * @param pointGeom - point to locate.
     * @param locationType - where to locate the point ("street" or "neighborhood").
     * @param srid - the srid to use when locating the point.
     * @return location name(s) - street(s) or neighborhood(s).
     */
    public List<String> findLocation(String pointGeom, String locationType, int srid) {
        final String TABLE_NAME = String.format("nyc_%ss", locationType);
        logger.finer(String.format("point: '%s', TABLE_NAME: '%s', srid: %d", pointGeom, TABLE_NAME, srid));

        final String query = String.format("select name from %s where ST_Intersects(ST_SetSrid(ST_GeomFromText('%s'),%d),geom);", TABLE_NAME, pointGeom, srid);
        final List<String> locationNames = jdbcTemplate.queryForList(query).stream().map((Map<String, Object> locationRecord) -> (String)locationRecord.get("name")).collect(Collectors.toList());
        if (locationNames.size() == 0 && !"neighborhood".equals(locationType)) logger.warning(String.format("0 %ss found for point %s", locationType, pointGeom)); // there're de-facto points outsides of any neighborhood..
        else {
            if (locationNames.size() > 10) logger.warning(String.format("%d %ss found for point %s", locationNames.size(), locationType, pointGeom));
            locationNames.forEach(locationName -> {
                if (locationName == null) logger.finer(String.format("NULL name returned for query: %s.", query)); // valid scenario, e.g.: select name from nyc_streets where ST_Intersects(ST_SetSrid(ST_GeomFromText('POINT(589785.1546829303 4521061.217850462)'),26918),geom);
            });
        }

        return locationNames;
    }
}
