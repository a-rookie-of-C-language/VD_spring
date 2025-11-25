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
    
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    
    /**
     * Upload activity cover image
     * @param file MultipartFile from request
     * @return Relative path to the uploaded file
     * @throws IOException if file operations fail
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
                if ("png".equals(ext)) ct = "image/png";
                else if ("jpg".equals(ext) || "jpeg".equals(ext)) ct = "image/jpeg";
                else if ("gif".equals(ext)) ct = "image/gif";
                else if ("webp".equals(ext)) ct = "image/webp";
                else ct = "application/octet-stream";
            }
            String b64 = Base64.getEncoder().encodeToString(bytes);
            return "data:" + ct + ";base64," + b64;
        } catch (Exception e) {
            return null;
        }
    }
}
