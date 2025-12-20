package com.drtshock.playervaults.util;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class S3Service {

    private final PlayerVaults plugin;
    private S3Client s3Client;
    private boolean enabled;
    private String bucket;

    public S3Service(PlayerVaults plugin) {
        this.plugin = plugin;
        this.initialize();
    }

    private void initialize() {
        Config.Storage.S3 config = plugin.getConf().getStorage().getS3();
        this.enabled = config.isEnabled();
        this.bucket = config.getBucket();

        if (!enabled)
            return;

        try {
            S3ClientBuilder builder = S3Client.builder()
                    .region(Region.of(config.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())));

            if (config.getEndpoint() != null && !config.getEndpoint().isEmpty()) {
                builder.endpointOverride(URI.create(config.getEndpoint()));
            }

            this.s3Client = builder.build();
            Logger.info("S3Service initialized successfully.");
        } catch (Exception e) {
            Logger.warn("Failed to initialize S3Service: " + e.getMessage());
            this.enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void uploadBackup(File file) {
        if (!enabled || s3Client == null)
            return;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key("backups/" + file.getName())
                    .build();

            s3Client.putObject(request, RequestBody.fromFile(file));
        } catch (Exception e) {
            throw new RuntimeException("S3 Upload failed", e);
        }
    }

    public List<String> listBackups() {
        if (!enabled || s3Client == null)
            return Collections.emptyList();

        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix("backups/")
                    .build();

            ListObjectsV2Response result = s3Client.listObjectsV2(request);
            return result.contents().stream()
                    .map(S3Object::key)
                    .filter(key -> key.endsWith(".zip"))
                    .sorted(Collections.reverseOrder()) // Show newest first
                    .collect(Collectors.toList());
        } catch (Exception e) {
            Logger.warn("Failed to list S3 backups: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public void downloadBackup(String key, Path destPath) {
        if (!enabled || s3Client == null)
            return;

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.getObject(request, destPath);
        } catch (Exception e) {
            throw new RuntimeException("S3 Download failed", e);
        }
    }

    public void shutdown() {
        if (s3Client != null) {
            s3Client.close();
        }
    }
}
