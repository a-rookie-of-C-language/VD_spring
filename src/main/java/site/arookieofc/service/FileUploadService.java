package site.arookieofc.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Base64;

@Slf4j
@Service
public class FileUploadService {

    @Value("${app.upload.base-path:uploads}")
    private String basePath;

    @Value("${app.upload.cover-path:covers}")
    private String coverPath;

    @Value("${app.upload.attachment-path:attachments}")
    private String attachmentPath;

    public FileUploadService() {
        this("uploads", "covers", "attachments");
    }

    FileUploadService(String basePath, String coverPath, String attachmentPath) {
        this.basePath = basePath;
        this.coverPath = coverPath;
        this.attachmentPath = attachmentPath;
    }

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final long MAX_ATTACHMENT_SIZE = 50 * 1024 * 1024; // 50MB for attachments
    private static final int MAX_STORED_FILENAME_BASE_LENGTH = 80;

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    // Allowed attachment extensions (documents, images, archives, etc.)
    private static final List<String> ALLOWED_ATTACHMENT_EXTENSIONS = List.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "md", "csv",
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "zip", "rar", "7z"
    );

    /**
     * Upload activity cover image
     *
     * @param file MultipartFile from request
     * @return Relative path to the uploaded file
     * @throws IOException              if file operations fail
     * @throws IllegalArgumentException if validation fails
     */
    public String uploadCoverImage(MultipartFile file) throws IOException {
        validateFileNotEmpty(file);
        validateMaxSize(file, MAX_FILE_SIZE, "File size cannot exceed 20MB");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only image formats are supported: jpg, jpeg, png, gif, webp");
        }

        String extension = requireAllowedExtension(
                requireOriginalFilename(file),
                ALLOWED_EXTENSIONS,
                "Only image formats are supported: jpg, jpeg, png, gif, webp"
        );
        String newFilename = UUID.randomUUID().toString() + "." + extension;

        return storeFile(file, coverPath, newFilename, "File");
    }

    /**
     * Delete cover image file
     *
     * @param relativePath Relative path to the file
     * @return true if deleted successfully
     */
    public boolean deleteCoverImage(String relativePath) {
        return deleteFile(relativePath, "File");
    }

    public String readCoverImageAsDataUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        try {
            Path filePath = resolveExistingFilePath(relativePath);
            if (filePath == null) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(filePath);
            String ct = Files.probeContentType(filePath);
            if (ct == null) {
                String ext = FilenameUtils.getExtension(filePath.getFileName().toString()).toLowerCase();
                ct = switch (ext) {
                    case "png" -> "image/png";
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "gif" -> "image/gif";
                    case "webp" -> "image/webp";
                    default -> "application/octet-stream";
                };
            }
            String b64 = Base64.getEncoder().encodeToString(bytes);
            return "data:" + ct + ";base64," + b64;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Get cover image URL (recommended for better performance and caching)
     *
     * @param relativePath Relative path to the image
     * @return URL path to access the image directly, or null if path is invalid
     */
    public String getCoverImageUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        // Verify file exists
        Path filePath = resolveExistingFilePath(relativePath);
        if (filePath == null) {
            return null;
        }

        String normalizedPath = normalizeRelativePath(relativePath);
        return normalizedPath == null ? null : "/" + normalizedPath;
    }

    /**
     * Upload attachment file (documents, images, archives, etc.)
     *
     * @param file             MultipartFile from request
     * @return Relative path to the uploaded file
     * @throws IOException              if file operations fail
     * @throws IllegalArgumentException if validation fails
     */
    public String uploadAttachment(MultipartFile file) throws IOException {
        validateFileNotEmpty(file);
        validateMaxSize(file, MAX_ATTACHMENT_SIZE, "File size cannot exceed 50MB");

        String filename = requireOriginalFilename(file);
        String extension = requireAllowedExtension(
                filename,
                ALLOWED_ATTACHMENT_EXTENSIONS,
                "File type not supported. Allowed types: " + ALLOWED_ATTACHMENT_EXTENSIONS
        );
        String baseName = sanitizeFilenameBase(FilenameUtils.getBaseName(filename));
        String newFilename = UUID.randomUUID().toString() + "_" + baseName + "." + extension;

        return storeFile(file, attachmentPath, newFilename, "Attachment");
    }

    /**
     * Delete attachment file
     *
     * @param relativePath Relative path to the file
     * @return true if deleted successfully
     */
    public boolean deleteAttachment(String relativePath) {
        return deleteFile(relativePath, "Attachment");
    }

    /**
     * Get file info without reading content
     *
     * @param relativePath Relative path to the file
     * @return Map with file metadata
     */
    public java.util.Map<String, Object> getFileInfo(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        try {
            Path filePath = resolveExistingFilePath(relativePath);
            if (filePath == null) {
                return null;
            }

            String filename = filePath.getFileName().toString();

            java.util.Map<String, Object> info = new java.util.HashMap<>();
            info.put("fileName", filename);
            info.put("filePath", relativePath);
            info.put("fileSize", Files.size(filePath));
            info.put("fileType", FilenameUtils.getExtension(filename));

            return info;
        } catch (IOException e) {
            log.error("Failed to get file info: {}", relativePath, e);
            return null;
        }
    }

    private Path resolveExistingFilePath(String relativePath) {
        String normalizedPath = normalizeRelativePath(relativePath);
        if (normalizedPath == null) {
            return null;
        }
        Path root = Paths.get(basePath).toAbsolutePath().normalize();
        Path filePath = root.resolve(normalizedPath).normalize();
        if (!filePath.startsWith(root) || !Files.isRegularFile(filePath)) {
            return null;
        }
        return filePath;
    }

    private String storeFile(MultipartFile file, String directory, String filename, String label) throws IOException {
        Path uploadPath = resolveUploadDirectory(directory);
        Path filePath = uploadPath.resolve(filename).normalize();
        if (!filePath.startsWith(uploadPath)) {
            throw new IOException("Invalid upload filename");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath);
        }

        log.info("{} uploaded successfully: {}", label, filePath);
        return "/" + normalizeConfiguredPath(directory) + "/" + filename;
    }

    private boolean deleteFile(String relativePath, String label) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }

        try {
            Path filePath = resolveExistingFilePath(relativePath);
            if (filePath == null) {
                return false;
            }
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("{} deleted successfully: {}", label, filePath);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete {}: {}", label.toLowerCase(), relativePath, e);
            return false;
        }
    }

    private void validateFileNotEmpty(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
    }

    private void validateMaxSize(MultipartFile file, long maxSize, String message) {
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(message);
        }
    }

    private String requireOriginalFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }
        return filename;
    }

    private String requireAllowedExtension(String filename, Collection<String> allowedExtensions, String message) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase(Locale.ROOT);
        if (!allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException(message);
        }
        return extension;
    }

    private Path resolveUploadDirectory(String directory) throws IOException {
        String normalizedDirectory = normalizeConfiguredPath(directory);
        Path root = Paths.get(basePath).toAbsolutePath().normalize();
        Path uploadPath = root.resolve(normalizedDirectory).normalize();
        if (!uploadPath.startsWith(root)) {
            throw new IOException("Upload directory must stay inside upload root");
        }
        Files.createDirectories(uploadPath);
        return uploadPath;
    }

    private String normalizeConfiguredPath(String path) throws IOException {
        String normalizedPath = normalizeRelativePath(path);
        if (normalizedPath == null) {
            throw new IOException("Invalid upload directory");
        }
        return normalizedPath;
    }

    private String normalizeRelativePath(String relativePath) {
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
        } catch (InvalidPathException e) {
            return null;
        }
        return path;
    }

    private String sanitizeFilenameBase(String baseName) {
        if (baseName == null || baseName.isBlank()) {
            return "file";
        }
        String sanitized = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank()) {
            return "file";
        }
        return sanitized.length() <= MAX_STORED_FILENAME_BASE_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_STORED_FILENAME_BASE_LENGTH);
    }
}
