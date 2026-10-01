package com.insurance.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class StorageService {

    @Value("${storage.s3.endpoint:}")        private String endpoint;
    @Value("${storage.s3.access-key:}")      private String accessKey;
    @Value("${storage.s3.secret-key:}")      private String secretKey;
    @Value("${storage.s3.region:ap-south-1}") private String region;
    @Value("${storage.s3.bucket:insurance-documents}") private String defaultBucket;

    private S3Client s3Client;
    private S3Presigner presigner;
    private boolean devMode = false;

    @PostConstruct
    public void init() {
        if (accessKey == null || accessKey.isBlank()) {
            log.warn("S3 credentials not configured — using dev/local mode");
            devMode = true;
            return;
        }
        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider credProvider = StaticCredentialsProvider.create(creds);

        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credProvider);
        var presignBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credProvider);

        if (!endpoint.isBlank()) {
            // MinIO or localstack
            builder.endpointOverride(URI.create(endpoint))
                   .forcePathStyle(true);
            presignBuilder.endpointOverride(URI.create(endpoint));
        }

        this.s3Client  = builder.build();
        this.presigner = presignBuilder.build();
        ensureBucketExists(defaultBucket);
        log.info("S3/MinIO storage initialised — bucket: {}", defaultBucket);
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    public String upload(MultipartFile file, String folder) {
        String key = folder + "/" + UUID.randomUUID() + "_" + sanitise(file.getOriginalFilename());

        if (devMode) {
            log.info("[STORAGE-DEV] Upload simulated: key={} size={}", key, file.getSize());
            return key;
        }

        try {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(defaultBucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(req, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded: {}", key);
            return key;
        } catch (Exception e) {
            log.error("Upload failed for key {}: {}", key, e.getMessage());
            throw new RuntimeException("File upload failed", e);
        }
    }

    // ── Pre-signed download URL (15 minutes) ──────────────────────────────────

    public String generatePresignedUrl(String storageKey, int expiryMinutes) {
        if (devMode) {
            return "http://localhost:9000/" + defaultBucket + "/" + storageKey + "?dev=true";
        }
        try {
            GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expiryMinutes))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(defaultBucket)
                            .key(storageKey)
                            .build())
                    .build();
            return presigner.presignGetObject(presignReq).url().toString();
        } catch (Exception e) {
            log.error("Presign failed for key {}: {}", storageKey, e.getMessage());
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    // ── Download stream ───────────────────────────────────────────────────────

    public InputStream download(String storageKey) {
        if (devMode) {
            log.info("[STORAGE-DEV] Download simulated: key={}", storageKey);
            return InputStream.nullInputStream();
        }
        try {
            GetObjectRequest req = GetObjectRequest.builder()
                    .bucket(defaultBucket)
                    .key(storageKey)
                    .build();
            return s3Client.getObject(req);
        } catch (Exception e) {
            log.error("Download failed for key {}: {}", storageKey, e.getMessage());
            throw new RuntimeException("File download failed", e);
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void delete(String storageKey) {
        if (devMode) {
            log.info("[STORAGE-DEV] Delete simulated: key={}", storageKey);
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(defaultBucket).key(storageKey).build());
            log.info("Deleted from storage: {}", storageKey);
        } catch (Exception e) {
            log.error("Delete failed for key {}: {}", storageKey, e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void ensureBucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created bucket: {}", bucket);
        }
    }

    private String sanitise(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public String getDefaultBucket() { return defaultBucket; }
}
