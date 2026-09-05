package site.arookieofc.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void generatedEtagUsesSha256HexFormat() throws Exception {
        Path attachments = tempDir.resolve("attachments");
        Files.createDirectories(attachments);
        Path file = attachments.resolve("demo.txt");
        Files.writeString(file, "demo");
        AttachmentController controller = newController();

        var response = controller.previewFile(
                "/attachments/demo.txt",
                webRequest());

        assertEquals(200, response.getStatusCode().value());
        String etag = response.getHeaders().getFirst(HttpHeaders.ETAG);
        assertTrue(etag != null && etag.matches("\"[0-9a-f]{64}\""));
    }

    @Test
    void invalidPathCharactersReturnBadRequest() {
        AttachmentController controller = newController();

        var response = controller.previewFile(
                "attachments/\u0000bad.txt",
                webRequest());

        assertEquals(400, response.getStatusCode().value());
    }

    private AttachmentController newController() {
        return new AttachmentController(tempDir.toString());
    }

    private ServletWebRequest webRequest() {
        return new ServletWebRequest(new MockHttpServletRequest());
    }
}
