package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller for handling file downloads (attachments, covers, etc.)
 */
@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/files")
public class AttachmentController {

    @Value("${app.upload.base-path:uploads}")
    private String basePath;

    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

    /**
     * Download attachment file
     * URL pattern: /api/files/download?path=/attachments/831a6454-0878-4b39-87e6-36197d7a11d2_README.md
     *
     * @param relativePath Relative path to the file (e.g., /attachments/xxxx.pdf)
     * @return File resource for download
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String relativePath,
                                                WebRequest request) {
        if (relativePath == null || relativePath.isEmpty()) {
            log.warn("Download request with empty path");
            return ResponseEntity.badRequest().build();
        }

        try {
            // Remove leading slash if present
            String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;

            // Prevent path traversal attacks
            if (path.contains("..") || path.contains("\\")) {
                log.warn("Potential path traversal attempt: {}", path);
                return ResponseEntity.badRequest().build();
            }

            // Build file path
            Path filePath = Paths.get(basePath).resolve(path).normalize();

            // Check if file exists and is within base path (security check)
            if (!filePath.startsWith(Paths.get(basePath).normalize())) {
                log.warn("File path outside base directory: {}", filePath);
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                log.warn("File not found: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // Get file metadata for caching
            FileTime lastModifiedTime = Files.getLastModifiedTime(filePath);
            long lastModified = lastModifiedTime.toMillis();
            String etag = generateETag(filePath, lastModified);

            // Check If-None-Match (ETag)
            if (request.checkNotModified(etag)) {
                log.debug("File not modified (ETag match): {}", filePath);
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .build();
            }

            // Check If-Modified-Since
            if (request.checkNotModified(lastModified)) {
                log.debug("File not modified (timestamp match): {}", filePath);
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .lastModified(lastModified)
                        .build();
            }

            // Load file as Resource
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("File not readable: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // Determine content type
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            // Extract original filename from the UUID-prefixed filename
            String filename = filePath.getFileName().toString();
            String originalFilename = filename;

            // If filename contains UUID prefix (format: uuid_originalname.ext), extract original name
            if (filename.contains("_")) {
                int underscoreIndex = filename.indexOf("_");
                originalFilename = filename.substring(underscoreIndex + 1);
            }

            // Encode filename for Content-Disposition header (support Chinese characters)
            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            log.info("Downloading file: {} as {}", filePath, originalFilename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + originalFilename + "\"; filename*=UTF-8''" + encodedFilename)
                    .eTag(etag)
                    .lastModified(lastModified)
                    .cacheControl(org.springframework.http.CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS)
                            .cachePublic()
                            .mustRevalidate())
                    .body(resource);

        } catch (IOException e) {
            log.error("Error downloading file: {}", relativePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Preview file in browser (inline display)
     * URL pattern: /api/files/preview?path=/attachments/xxxx.pdf
     *
     * @param relativePath Relative path to the file
     * @return File resource for preview
     */
    @GetMapping("/preview")
    public ResponseEntity<Resource> previewFile(@RequestParam("path") String relativePath,
                                               WebRequest request) {
        if (relativePath == null || relativePath.isEmpty()) {
            log.warn("Preview request with empty path");
            return ResponseEntity.badRequest().build();
        }

        try {
            // Remove leading slash if present
            String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;

            // Prevent path traversal attacks
            if (path.contains("..") || path.contains("\\")) {
                log.warn("Potential path traversal attempt: {}", path);
                return ResponseEntity.badRequest().build();
            }

            // Build file path
            Path filePath = Paths.get(basePath).resolve(path).normalize();

            // Check if file exists and is within base path
            if (!filePath.startsWith(Paths.get(basePath).normalize())) {
                log.warn("File path outside base directory: {}", filePath);
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                log.warn("File not found: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // Get file metadata for caching
            FileTime lastModifiedTime = Files.getLastModifiedTime(filePath);
            long lastModified = lastModifiedTime.toMillis();
            String etag = generateETag(filePath, lastModified);

            // Check If-None-Match (ETag)
            if (request.checkNotModified(etag)) {
                log.debug("File not modified (ETag match): {}", filePath);
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .build();
            }

            // Check If-Modified-Since
            if (request.checkNotModified(lastModified)) {
                log.debug("File not modified (timestamp match): {}", filePath);
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .lastModified(lastModified)
                        .build();
            }

            // Load file as Resource
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("File not readable: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // Determine content type
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            String filename = filePath.getFileName().toString();
            String originalFilename = filename;
            if (filename.contains("_")) {
                int underscoreIndex = filename.indexOf("_");
                originalFilename = filename.substring(underscoreIndex + 1);
            }

            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            log.info("Previewing file: {}", filePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + originalFilename + "\"; filename*=UTF-8''" + encodedFilename)
                    .eTag(etag)
                    .lastModified(lastModified)
                    .cacheControl(org.springframework.http.CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS)
                            .cachePublic()
                            .mustRevalidate())
                    .body(resource);

        } catch (IOException e) {
            log.error("Error previewing file: {}", relativePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate ETag based on file path and last modified time
     */
    private String generateETag(Path filePath, long lastModified) {
        try {
            String input = filePath.toString() + lastModified;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "\"" + hexString.toString() + "\"";
        } catch (Exception e) {
            log.warn("Failed to generate ETag, using fallback", e);
            return "\"" + filePath.toString().hashCode() + "-" + lastModified + "\"";
        }
    }
}

