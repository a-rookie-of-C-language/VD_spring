package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controller for handling file downloads (attachments, covers, etc.)
 */
@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/api/files")
public class AttachmentController {

    @Value("${app.upload.base-path:uploads}")
    private String basePath;

    /**
     * Download attachment file
     * URL pattern: /api/files/download?path=/attachments/831a6454-0878-4b39-87e6-36197d7a11d2_README.md
     *
     * @param relativePath Relative path to the file (e.g., /attachments/xxxx.pdf)
     * @return File resource for download
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String relativePath) {
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
    public ResponseEntity<Resource> previewFile(@RequestParam("path") String relativePath) {
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

            // Use inline instead of attachment
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + originalFilename + "\"; filename*=UTF-8''" + encodedFilename)
                    .body(resource);

        } catch (IOException e) {
            log.error("Error previewing file: {}", relativePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

