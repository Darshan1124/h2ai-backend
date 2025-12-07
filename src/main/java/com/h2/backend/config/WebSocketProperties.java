
package com.h2.backend.config;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

/**
 * Configuration to handle large WebSocket messages.
 * Tomcat has a default max frame payload of 65536 bytes (64KB).
 * We increase this to 50MB to handle large audio messages from Gemini.
 */
@Configuration
public class WebSocketProperties {
    
    @Bean
    public ServletContextInitializer servletContextInitializer() {
        return servletContext -> {
            // Configure WebSocket parameters
            servletContext.setInitParameter("org.apache.catalina.websocket.DEFAULT_BUFFER_SIZE", "2097152");
        };
    }
}
