package site.arookieofc.controller;

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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Controller for handling file downloads (attachments, covers, etc.)
 */
@RestController
@Slf4j
@RequestMapping("/files")
public class AttachmentController {

    @Value("${app.upload.base-path:uploads}")
    private String basePath;

    public AttachmentController() {
        this("uploads");
    }

    AttachmentController(String basePath) {
        this.basePath = basePath;
    }

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
        return serveFile(relativePath, request, false);
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
        return serveFile(relativePath, request, true);
    }

    private ResponseEntity<Resource> serveFile(String relativePath, WebRequest request, boolean inline) {
        if (relativePath == null || relativePath.isEmpty()) {
            log.warn("{} request with empty path", inline ? "Preview" : "Download");
            return ResponseEntity.badRequest().build();
        }

        try {
            Path filePath = resolveFilePath(relativePath);
            if (filePath == null) {
                log.warn("Invalid {} path: {}", inline ? "preview" : "download", relativePath);
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

            String originalFilename = extractOriginalFilename(filePath);

            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            String disposition = inline ? "inline" : "attachment";

            log.info("{} file: {} as {}", inline ? "Previewing" : "Downloading", filePath, originalFilename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            disposition + "; filename=\"" + originalFilename + "\"; filename*=UTF-8''" + encodedFilename)
                    .eTag(etag)
                    .lastModified(lastModified)
                    .cacheControl(org.springframework.http.CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS)
                            .cachePublic()
                            .mustRevalidate())
                    .body(resource);

        } catch (IOException e) {
            log.error("Error serving file: {}", relativePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate ETag based on file path and last modified time
     */
    private String generateETag(Path filePath, long lastModified) {
        try {
            String input = filePath.toString() + lastModified;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "\"" + hexString.toString() + "\"";
        } catch (NoSuchAlgorithmException e) {
            log.warn("Failed to generate ETag, using fallback", e);
            return "\"" + filePath.toString().hashCode() + "-" + lastModified + "\"";
        }
    }

    private Path resolveFilePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String path = relativePath.trim().replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        try {
            if (path.isBlank() || path.contains("..") || path.contains(":") || Paths.get(path).isAbsolute()) {
                return null;
            }

            Path root = Paths.get(basePath).toAbsolutePath().normalize();
            Path filePath = root.resolve(path).normalize();
            if (!filePath.startsWith(root)) {
                return null;
            }
            return filePath;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private String extractOriginalFilename(Path filePath) {
        String filename = filePath.getFileName().toString();
        if (filename.contains("_")) {
            filename = filename.substring(filename.indexOf("_") + 1);
        }
        return filename.replaceAll("[\\r\\n\"]", "_");
    }
}

