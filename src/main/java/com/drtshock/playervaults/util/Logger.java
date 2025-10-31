package com.drtshock.playervaults.util;

import com.drtshock.playervaults.PlayerVaults;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

public class Logger {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static void log(Level level, String message) {
        String time = LocalTime.now().format(TIME_FORMATTER);
        PlayerVaults.getInstance().getLogger().log(level, "[" + time + "/" + level.getName() + "] " + message);
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warn(String message) {
        log(Level.WARNING, message);
    }

    public static void severe(String message) {
        log(Level.SEVERE, message);
    }

    public static void debug(String message) {
        if (PlayerVaults.getInstance().getConf().isDebug()) {
            log(Level.INFO, "[DEBUG] " + message);
        }
    }
}
