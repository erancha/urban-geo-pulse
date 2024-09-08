package com.urbangeopulse.utils.misc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.logging.Level;

public class Logger {
    public static void main(String[] args) {
        new Logger().f1();
    }

    /*static */void f1() {
        final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Logger.class.getName());
        Logger.logException(new Exception("test"), logger);
    }

    public static void logException(Exception ex, java.util.logging.Logger logger, Level severity) {
        logException(ex, "", logger, severity);
    }

    public static void logException(Exception ex, java.util.logging.Logger logger) {
        logException(ex, "", logger);
    }

    public static void logException(Exception ex, String message, java.util.logging.Logger logger) {
        logException(ex, message, logger, Level.SEVERE);
    }

    public static void logException(Exception ex, String message, java.util.logging.Logger logger, Level severity) {
        Writer buffer = new StringWriter();
        PrintWriter pw = new PrintWriter(buffer);
        ex.printStackTrace(pw);
        logger.log(severity, message + "\n" + buffer);
    }
}
