package com.h2.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.h2.backend.gemini.GeminiVoiceHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
	
    private final GeminiVoiceHandler geminiVoiceHandler;

    public WebSocketConfig(GeminiVoiceHandler geminiVoiceHandler) {
        this.geminiVoiceHandler = geminiVoiceHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Map the URL /ws/voice to our handler
        // allowedOrigins("*") is crucial for allowing React (localhost:3000) to connect
        registry.addHandler(geminiVoiceHandler, "/ws/voice")
                .setAllowedOrigins("*");
    }
}

