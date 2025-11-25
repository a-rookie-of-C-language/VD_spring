package site.arookieofc.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${app.upload.base-path:uploads}")
    private String basePath;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Map /covers/** URLs to the uploads/covers/ directory
        String uploadPath = Paths.get(basePath).toAbsolutePath().toString().replace("\\", "/");
        
        registry.addResourceHandler("/covers/**")
                .addResourceLocations("file:" + uploadPath + "/covers/");
    }
}
