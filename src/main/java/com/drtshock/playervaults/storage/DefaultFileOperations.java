package com.drtshock.playervaults.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class DefaultFileOperations implements FileOperations {
    @Override
    public boolean renameTo(File source, File destination) throws IOException {
        return source.renameTo(destination);
    }

    @Override
    public boolean exists(File file) {
        return file.exists();
    }

    @Override
    public boolean delete(File file) {
        return file.delete();
    }

    @Override
    public void save(YamlConfiguration yaml, File file) throws IOException {
        yaml.save(file);
    }

    @Override
    public YamlConfiguration load(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public File[] listFiles(File directory, java.io.FilenameFilter filter) {
        return directory.listFiles(filter);
    }
}
