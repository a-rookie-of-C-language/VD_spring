package site.arookieofc.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
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

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final long MAX_ATTACHMENT_SIZE = 50 * 1024 * 1024; // 50MB for attachments

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    // Allowed attachment extensions (documents, images, archives, etc.)
    private static final List<String> ALLOWED_ATTACHMENT_EXTENSIONS = Arrays.asList(
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
        // Validate file is not empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size cannot exceed 3MB");
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Only image formats are supported: jpg, jpeg, png, gif, webp");
        }

        // Validate file extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only image formats are supported: jpg, jpeg, png, gif, webp");
        }

        // Generate unique filename
        String newFilename = UUID.randomUUID().toString() + "." + extension;

        // Create directory if not exists
        Path uploadPath = Paths.get(basePath, coverPath);
        File uploadDir = uploadPath.toFile();
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (!created) {
                throw new IOException("Failed to create upload directory");
            }
        }

        // Save file
        Path filePath = uploadPath.resolve(newFilename);
        Files.copy(file.getInputStream(), filePath);

        log.info("File uploaded successfully: {}", filePath);

        // Return relative path
        return "/" + coverPath + "/" + newFilename;
    }

    /**
     * Delete cover image file
     *
     * @param relativePath Relative path to the file
     * @return true if deleted successfully
     */
    public boolean deleteCoverImage(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }

        try {
            // Remove leading slash if present
            String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            Path filePath = Paths.get(basePath, path);
            File file = filePath.toFile();

            if (file.exists() && file.isFile()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.info("File deleted successfully: {}", filePath);
                }
                return deleted;
            }
        } catch (Exception e) {
            log.error("Failed to delete file: {}", relativePath, e);
        }

        return false;
    }

    public String readCoverImageAsDataUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        try {
            String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            Path filePath = Paths.get(basePath, path);
            if (!Files.exists(filePath)) {
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
        } catch (Exception e) {
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
        String normalizedPath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        Path filePath = Paths.get(basePath, normalizedPath);
        if (!Files.exists(filePath)) {
            return null;
        }

        // Return path with /files prefix for AttachmentController
        // Input: /covers/xxx.png -> Output: /files/covers/xxx.png
        if (relativePath.startsWith("/covers/")) {
            return "/files" + relativePath;
        } else if (relativePath.startsWith("covers/")) {
            return "/files/" + relativePath;
        } else {
            // Handle legacy paths without /covers/ prefix
            return "/files/covers" + (relativePath.startsWith("/") ? "" : "/") + relativePath;
        }
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
        // Validate file is not empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Validate file size
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new IllegalArgumentException("File size cannot exceed 50MB");
        }

        // Validate file extension
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        if (!ALLOWED_ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type not supported. Allowed types: " + ALLOWED_ATTACHMENT_EXTENSIONS);
        }

        // Generate unique filename while preserving original name info
        String baseName = FilenameUtils.getBaseName(filename);
        String newFilename = UUID.randomUUID().toString() + "_" + baseName + "." + extension;

        // Create directory if not exists
        Path uploadPath = Paths.get(basePath, attachmentPath);
        File uploadDir = uploadPath.toFile();
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (!created) {
                throw new IOException("Failed to create upload directory");
            }
        }

        // Save file
        Path filePath = uploadPath.resolve(newFilename);
        Files.copy(file.getInputStream(), filePath);

        log.info("Attachment uploaded successfully: {}", filePath);

        // Return relative path
        return "/" + attachmentPath + "/" + newFilename;
    }

    /**
     * Delete attachment file
     *
     * @param relativePath Relative path to the file
     * @return true if deleted successfully
     */
    public boolean deleteAttachment(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }

        try {
            // Remove leading slash if present
            String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            Path filePath = Paths.get(basePath, path);
            File file = filePath.toFile();

            if (file.exists() && file.isFile()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.info("Attachment deleted successfully: {}", filePath);
                }
                return deleted;
            }
        } catch (Exception e) {
            log.error("Failed to delete attachment: {}", relativePath, e);
        }

        return false;
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
            String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            Path filePath = Paths.get(basePath, path);

            if (!Files.exists(filePath)) {
                return null;
            }

            File file = filePath.toFile();
            String filename = file.getName();

            java.util.Map<String, Object> info = new java.util.HashMap<>();
            info.put("fileName", filename);
            info.put("filePath", relativePath);
            info.put("fileSize", file.length());
            info.put("fileType", FilenameUtils.getExtension(filename));

            return info;
        } catch (Exception e) {
            log.error("Failed to get file info: {}", relativePath, e);
            return null;
        }
    }
}
