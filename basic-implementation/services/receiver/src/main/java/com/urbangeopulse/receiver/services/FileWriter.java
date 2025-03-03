package com.urbangeopulse.receiver.services;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class FileWriter implements Writer {
    private final Logger logger;
    private final java.io.FileWriter fileWriter;

    public FileWriter(String topicName, Logger logger) {
        try {
            fileWriter = new java.io.FileWriter(topicName);
        } catch (IOException e) {
            com.urbangeopulse.utils.misc.Logger.logException(e, logger);
            throw new RuntimeException(e);
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