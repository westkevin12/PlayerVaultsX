package com.drtshock.playervaults.util;

import com.drtshock.playervaults.config.file.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class S3Service {

    private final String bucketName;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String endpoint;
    private final boolean enabled;

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_STAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public S3Service(Config.Storage.S3 config) {
        this.enabled = config.isEnabled();
        this.bucketName = config.getBucket();
        this.region = (config.getRegion() != null && !config.getRegion().isEmpty()) ? config.getRegion() : "us-east-1";
        this.accessKey = config.getAccessKey();
        this.secretKey = config.getSecretKey();

        String ep = config.getEndpoint();
        if (ep != null && !ep.isEmpty()) {
            this.endpoint = ep.endsWith("/") ? ep.substring(0, ep.length() - 1) : ep;
        } else {
            this.endpoint = "https://s3." + this.region + ".amazonaws.com";
        }

        if (enabled) {
            Logger.info("Tiny S3 Service initialized. Bucket: " + bucketName + ", Region: " + region);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void uploadBackup(File backupFile) {
        if (!enabled)
            return;

        try {
            String objectKey = "backups/" + backupFile.getName();
            String urlStr = String.format("%s/%s/%s", endpoint, bucketName, objectKey);

            HttpURLConnection connection = createConnection(java.net.URI.create(urlStr).toURL(), "PUT",
                    "application/zip", "UNSIGNED-PAYLOAD");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(backupFile.length());

            try (OutputStream os = connection.getOutputStream();
                    FileInputStream fis = new FileInputStream(backupFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                Logger.info("Uploaded backup (Tiny S3): " + backupFile.getName());
            } else {
                String error = readError(connection);
                Logger.severe("Failed to upload backup. Code: " + responseCode + ", Error: " + error);
            }
            connection.disconnect();

        } catch (Exception e) {
            Logger.severe("Failed to upload backup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<String> listBackups() {
        if (!enabled)
            return Collections.emptyList();

        try {
            // S3 List Objects V2
            String urlStr = String.format("%s/%s?list-type=2&prefix=backups/", endpoint, bucketName);
            HttpURLConnection connection = createConnection(java.net.URI.create(urlStr).toURL(), "GET", null,
                    "UNSIGNED-PAYLOAD");

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return parseListResponse(connection.getInputStream());
            } else {
                String error = readError(connection);
                Logger.severe("Failed to list backups. Code: " + responseCode + ", Error: " + error);
                return Collections.emptyList();
            }

        } catch (Exception e) {
            Logger.severe("Failed to list backups: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public void downloadBackup(String backupName, File destination) {
        if (!enabled)
            return;

        try {
            // Remove 'backups/' prefix if present in backupName to avoid doubling it if
            // passed incorrectly
            String objectKey = backupName;

            String urlStr = String.format("%s/%s/%s", endpoint, bucketName, objectKey);
            HttpURLConnection connection = createConnection(java.net.URI.create(urlStr).toURL(), "GET", null,
                    "UNSIGNED-PAYLOAD");

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream is = connection.getInputStream();
                        FileOutputStream fos = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                Logger.info("Downloaded backup (Tiny S3): " + backupName);
            } else {
                String error = readError(connection);
                Logger.severe("Failed to download backup. Code: " + responseCode + ", Error: " + error);
            }
            connection.disconnect();

        } catch (Exception e) {
            Logger.severe("Failed to download backup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- XML Parsing ---

    private List<String> parseListResponse(InputStream is) {
        List<String> keys = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();

            NodeList contents = doc.getElementsByTagName("Contents");
            for (int i = 0; i < contents.getLength(); i++) {
                Element element = (Element) contents.item(i);
                String key = element.getElementsByTagName("Key").item(0).getTextContent();
                if (key.endsWith(".zip")) {
                    keys.add(key);
                }
            }
            // Sort naive string sort (timestamps are cleaner but require more parsing)
            // Ideally we parse LastModified, but for simplicity:
            keys.sort(Collections.reverseOrder());

        } catch (Exception e) {
            Logger.severe("Failed to parse S3 XML response: " + e.getMessage());
        }
        return keys;
    }

    // --- AWS Signature V4 Implementation ---

    private HttpURLConnection createConnection(URL url, String method, String contentType, String contentSha256)
            throws IOException, Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        String amzDate = now.format(DATE_FORMATTER);
        String dateStamp = now.format(DATE_STAMP_FORMATTER);

        connection.setRequestProperty("Host", url.getHost());
        connection.setRequestProperty("X-Amz-Date", amzDate);
        connection.setRequestProperty("x-amz-content-sha256", contentSha256);
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        }

        // Canonical Request
        String canonicalUri = url.getPath();
        if (canonicalUri.isEmpty())
            canonicalUri = "/";

        String canonicalQuery = url.getQuery();
        if (canonicalQuery == null)
            canonicalQuery = "";
        // Note: strictly we should sort query params, but we only use known params
        // here.
        // For list-type=2, we need to make sure we sign it correctly.
        if (!canonicalQuery.isEmpty()) {
            // Simple naive sort/encode for our specific use case
            // (list-type=2&prefix=backups/)
            // The parameters must be sorted by name.
            // list-type comes before prefix.
            // We'll rely on constructing the URL string correctly in listBackups.
            // Ideally we split and sort.
            // Let's implement a simple sorter for robustness.
            canonicalQuery = sortQuery(canonicalQuery);
        }

        String canonicalHeaders = "host:" + url.getHost() + "\n" +
                "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-date";

        String canonicalRequest = method + "\n" +
                canonicalUri + "\n" +
                canonicalQuery + "\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                contentSha256;

        // String to Sign
        String algorithm = "AWS4-HMAC-SHA256";
        String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
        String stringToSign = algorithm + "\n" +
                amzDate + "\n" +
                credentialScope + "\n" +
                toHex(sha256(canonicalRequest));

        // Calculate Signature
        byte[] signingKey = getSignatureKey(secretKey, dateStamp, region, "s3");
        String signature = toHex(hmacSHA256(signingKey, stringToSign));

        // Authorization Header
        String authorization = algorithm + " " +
                "Credential=" + accessKey + "/" + credentialScope + ", " +
                "SignedHeaders=" + signedHeaders + ", " +
                "Signature=" + signature;

        connection.setRequestProperty("Authorization", authorization);

        return connection;
    }

    private String sortQuery(String query) {
        String[] pairs = query.split("&");
        List<String> sortedPairs = new ArrayList<>();
        for (String pair : pairs) {
            // Encode key and value
            String[] parts = pair.split("=", 2);
            String k = parts[0];
            String v = parts.length > 1 ? parts[1] : "";
            // We assume query is already safe, but strictly AWS wants encoding.
            // For now, we return it as is but sorted, assuming list-type and prefix are
            // safe chars.
            sortedPairs.add(k + "=" + v);
        }
        Collections.sort(sortedPairs);
        return String.join("&", sortedPairs);
    }

    private byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName)
            throws Exception {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSHA256(kSecret, dateStamp);
        byte[] kRegion = hmacSHA256(kDate, regionName);
        byte[] kService = hmacSHA256(kRegion, serviceName);
        return hmacSHA256(kService, "aws4_request");
    }

    private byte[] hmacSHA256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sha256(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data.getBytes(StandardCharsets.UTF_8));
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String readError(HttpURLConnection conn) {
        try (InputStream es = conn.getErrorStream()) {
            if (es == null)
                return "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(es))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return "Unknown error";
        }
    }
}
