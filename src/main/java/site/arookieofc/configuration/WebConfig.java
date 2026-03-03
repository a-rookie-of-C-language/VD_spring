package site.arookieofc.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${app.upload.base-path:uploads}")
    private String basePath;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Map /covers/** URLs to the uploads/covers/ directory
        String uploadPath = Paths.get(basePath).toAbsolutePath().toString().replace("\\", "/");
        
        // Configure covers with stronger cache control (30 days)
        registry.addResourceHandler("/covers/**")
                .addResourceLocations("file:" + uploadPath + "/covers/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS)
                        .cachePublic()
                        .mustRevalidate())
                .resourceChain(true);

        // Configure attachments with cache control (30 days)
        registry.addResourceHandler("/attachments/**")
                .addResourceLocations("file:" + uploadPath + "/attachments/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS)
                        .cachePublic()
                        .mustRevalidate())
                .resourceChain(true);
    }
}
