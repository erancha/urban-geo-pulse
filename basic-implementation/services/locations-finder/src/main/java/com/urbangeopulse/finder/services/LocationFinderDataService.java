package com.urbangeopulse.finder.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * data required by the service.
 */
@Component
public class LocationFinderDataService {
    private static final Logger logger = Logger.getLogger(LocationFinderDataService.class.getName());

    private final JdbcTemplate jdbcTemplate;

    public LocationFinderDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param pointGeom - point to locate.
     * @param locationType - where to locate the point ("street" or "neighborhood").
     * @param srid - the srid to use when locating the point.
     * @return location gid(s) - street(s) or neighborhood(s).
     */
    public List<String> findLocation(String pointGeom, String locationType, int srid) {
        final String TABLE_NAME = String.format("nyc_%ss", locationType);
        logger.finer(String.format("point: '%s', TABLE_NAME: '%s', srid: %d", pointGeom, TABLE_NAME, srid));

        final String query = String.format("select gid from %s where ST_Intersects(ST_SetSrid(ST_GeomFromText('%s'),%d),geom);", TABLE_NAME, pointGeom, srid);
        
        Connection connection = null;
        List<String> locationGids = new ArrayList<>();
        try {
            connection = DataSourceUtils.getConnection(jdbcTemplate.getDataSource());
            try (PreparedStatement stmt = connection.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    locationGids.add(rs.getString("gid"));
                }
            }
                
            if (locationGids.isEmpty() && !"neighborhood".equals(locationType)) {
                logger.warning(String.format("0 %ss found for point %s", locationType, pointGeom)); // there're de-facto points outsides of any neighborhood..
            } else {
                if (locationGids.size() > 10) logger.warning(String.format("%d %ss found for point %s", locationGids.size(), locationType, pointGeom));
                locationGids.forEach(locationGid -> {
                    if (locationGid == null) logger.finer(String.format("NULL name returned for query: %s.", query)); // valid scenario, e.g.: select name from nyc_streets where ST_Intersects(ST_SetSrid(ST_GeomFromText('POINT(589785.1546829303 4521061.217850462)'),26918),geom);
                });
            }
            
            return locationGids;
        } catch (SQLException e) {
            logger.severe(String.format("An error occurred while executing query: %s", query));
            logger.severe(String.format("Error details: %s", e.getMessage()));
            return locationGids;
        } finally {
            if (connection != null) {
                DataSourceUtils.releaseConnection(connection, jdbcTemplate.getDataSource());
            }
        }
    }
    
    private void logException(SQLException e, Logger logger) {
        logger.severe(String.format("An error occurred while executing query: %s", e.getMessage()));
    }
}
