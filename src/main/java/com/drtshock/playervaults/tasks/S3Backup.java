package com.drtshock.playervaults.tasks;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.drtshock.playervaults.util.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
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

            // 2. Upload to S3
            uploadToS3(zipFile, s3Config);

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

    private void uploadToS3(File file, Config.Storage.S3 config) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())));

        if (config.getEndpoint() != null && !config.getEndpoint().isEmpty()) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }

        try (S3Client s3 = builder.build()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key("backups/" + file.getName())
                    .build();

            s3.putObject(request, RequestBody.fromFile(file));
        } catch (Exception e) {
            throw new RuntimeException("S3 Upload failed", e);
        }
    }
}
