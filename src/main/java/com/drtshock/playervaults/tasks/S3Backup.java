package com.drtshock.playervaults.tasks;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.drtshock.playervaults.util.Logger;
import com.drtshock.playervaults.util.S3Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class S3Backup implements Runnable {

    private final PlayerVaults plugin;

    public S3Backup(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Config.Storage.S3 s3Config = plugin.getConf().getStorage().getS3();
        if (!s3Config.isEnabled()) {
            return;
        }

        S3Service service = plugin.getS3Service();
        if (service == null || !service.isEnabled()) {
            Logger.warn("S3 Backup skipped: S3Service not available.");
            return;
        }

        Logger.info("Starting S3 Backup...");

        File vaultDir = plugin.getVaultData();
        if (!vaultDir.exists() || !vaultDir.isDirectory()) {
            Logger.warn("Vault directory not found for backup: " + vaultDir.getAbsolutePath());
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File zipFile = new File(plugin.getDataFolder(), "backup-" + timestamp + ".zip");

        try {
            // 1. Zip the directory
            zipDirectory(vaultDir, zipFile);

            // 2. Upload to S3 using Service
            service.uploadBackup(zipFile);

            Logger.info("S3 Backup completed successfully: " + zipFile.getName());

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to perform S3 backup", e);
        } finally {
            // 3. Cleanup zip
            if (zipFile.exists()) {
                zipFile.delete();
            }
        }
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Path sourcePath = sourceDir.toPath();
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".lock"))
                        return FileVisitResult.CONTINUE; // Skip lock files

                    try {
                        String relativePath = sourcePath.relativize(file).toString();
                        zos.putNextEntry(new ZipEntry(relativePath));
                        Files.copy(file, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        // File might have been deleted or moved; skip it and continue
                        Logger.warn("Skipping file during backup (modified/deleted): " + file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    // Handle failure to access file (e.g. permission or not found)
                    Logger.warn("Failed to visit file during backup: " + file + " (" + exc.getMessage() + ")");
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
