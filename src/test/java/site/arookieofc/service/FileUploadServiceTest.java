package site.arookieofc.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void fileInfoAndDeleteStayInsideUploadRoot() throws Exception {
        FileUploadService service = newService(tempDir);
        Path attachmentDir = Files.createDirectories(tempDir.resolve("attachments"));
        Files.writeString(attachmentDir.resolve("demo.txt"), "demo");
        Path outside = Files.createTempFile("outside-upload-root", ".txt");
        Files.writeString(outside, "outside");

        assertNotNull(service.getFileInfo("/attachments/demo.txt"));
        assertNull(service.getFileInfo("../" + outside.getFileName()));
        assertFalse(service.deleteAttachment("../" + outside.getFileName()));
        assertTrue(Files.exists(outside));
    }

    @Test
    void uploadedAttachmentFilenameIsSanitized() throws Exception {
        FileUploadService service = newService(tempDir);
        MockMultipartFile file = uploadFile("bad name?.txt");

        String path = service.uploadAttachment(file);

        assertTrue(path.startsWith("/attachments/"));
        assertFalse(path.contains(" "));
        assertFalse(path.contains("?"));
        assertTrue(Files.exists(tempDir.resolve(path.substring(1))));
    }

    @Test
    void uploadedAttachmentFilenameBaseIsBounded() throws Exception {
        FileUploadService service = newService(tempDir);
        String longBaseName = "a".repeat(180);
        MockMultipartFile file = uploadFile(longBaseName + ".txt");

        String path = service.uploadAttachment(file);
        String storedFilename = Path.of(path).getFileName().toString();
        String preservedBase = storedFilename.substring(storedFilename.indexOf('_') + 1, storedFilename.lastIndexOf('.'));

        assertEquals(80, preservedBase.length());
        assertTrue(Files.exists(tempDir.resolve(path.substring(1))));
    }

    @Test
    void coverImageUrlReturnsRelativeUploadPathForPreviewEndpoint() throws Exception {
        FileUploadService service = newService(tempDir);
        Path coverDir = Files.createDirectories(tempDir.resolve("covers"));
        Files.writeString(coverDir.resolve("demo.png"), "demo");

        assertEquals("/covers/demo.png", service.getCoverImageUrl("/covers/demo.png"));
        assertEquals("/covers/demo.png", service.getCoverImageUrl("covers/demo.png"));
    }

    @Test
    void uploadRejectsConfiguredDirectoryOutsideUploadRoot() {
        FileUploadService service = newServiceWithCoverDirectoryOutsideRoot(tempDir);
        MockMultipartFile file = uploadFile("cover.png", "image/png");

        assertThrows(java.io.IOException.class, () -> service.uploadCoverImage(file));
    }

    @Test
    void invalidRelativePathsAreRejectedWithoutThrowing() {
        FileUploadService service = newService(tempDir);
        String invalidPath = "covers/\u0000bad.png";

        assertNull(service.getCoverImageUrl(invalidPath));
        assertNull(service.getFileInfo(invalidPath));
        assertFalse(service.deleteAttachment(invalidPath));
    }

    @Test
    void directoriesAreRejectedAsFileTargets() throws Exception {
        FileUploadService service = newService(tempDir);
        Files.createDirectories(tempDir.resolve("covers"));

        assertNull(service.getCoverImageUrl("/covers"));
        assertNull(service.getFileInfo("/covers"));
        assertNull(service.readCoverImageAsDataUrl("/covers"));
        assertFalse(service.deleteCoverImage("/covers"));
    }

    private FileUploadService newService(Path basePath) {
        return new FileUploadService(basePath.toString(), "covers", "attachments");
    }

    private FileUploadService newServiceWithCoverDirectoryOutsideRoot(Path basePath) {
        return new FileUploadService(basePath.toString(), "../covers", "attachments");
    }

    private MockMultipartFile uploadFile(String filename) {
        return uploadFile(filename, "text/plain");
    }

    private MockMultipartFile uploadFile(String filename, String contentType) {
        return new MockMultipartFile(
                "file",
                filename,
                contentType,
                "content".getBytes()
        );
    }
}
