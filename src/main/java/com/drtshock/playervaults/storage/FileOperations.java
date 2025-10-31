package com.drtshock.playervaults.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public interface FileOperations {
    boolean renameTo(File source, File destination) throws IOException;
    boolean exists(File file);
    boolean delete(File file);
    void save(YamlConfiguration yaml, File file) throws IOException;
    YamlConfiguration load(File file);
    File[] listFiles(File directory, java.io.FilenameFilter filter);
}
