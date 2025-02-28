package com.urbangeopulse.receiver.services;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class FileWriter implements Writer {
    private static final String DEFAULT_FILE_NAME = "./people-geo-locations.csv";
    public static final String BACKUP_FILENAME = new File(DEFAULT_FILE_NAME).exists() ? DEFAULT_FILE_NAME : "./NYC_people-geo-locations--Duffield_St.csv";

    private Logger logger;
    private java.io.FileWriter fileWriter;

    public FileWriter(String topicName, Logger logger) {
        try {
            fileWriter = new java.io.FileWriter(BACKUP_FILENAME);
        } catch (IOException e) {
            com.urbangeopulse.utils.misc.Logger.logException(e, logger);
        }
        this.logger = logger;
    }

    @Override
    public void save(String value, String key) {
        String data = value + "|" + key;
        try {
            assert (fileWriter != null);
            fileWriter.write(data);
            fileWriter.write(System.lineSeparator());
            fileWriter.flush();
        } catch (IOException e) {
            com.urbangeopulse.utils.misc.Logger.logException(e, logger);
        }
    }
}