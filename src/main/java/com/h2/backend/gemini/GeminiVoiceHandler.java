package com.h2.backend.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class GeminiVoiceHandler extends TextWebSocketHandler {
    
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    // Use stable 1.5 flash model
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    public GeminiVoiceHandler() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode json = mapper.readTree(message.getPayload());

            if (json.has("text")) {
                String userMessage = json.get("text").asText();
                String contextData = json.has("config") ? json.get("config").asText() : "";
                String mode = json.has("mode") ? json.get("mode").asText() : "marketing"; 

                System.out.println("Processing [" + mode + "]: " + userMessage);
                
                String aiResponse = fetchGeminiResponse(userMessage, contextData, mode);
                sendFullResponse(session, aiResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try { sendFullResponse(session, "Error processing request."); } catch (IOException ex) {}
        }
    }

    private String fetchGeminiResponse(String userText, String contextData, String mode) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        
        String systemInstruction;
        
        // --- IMPROVED SYSTEM PROMPTS ---
        if ("hiring".equalsIgnoreCase(mode)) {
            // Interviewer Logic
            systemInstruction = """
                You are a professional AI Technical Recruiter Friday.
                GOAL: Conduct a short screening interview.
                RULES:
                1. Ask ONE question at a time.
                2. Do not list all requirements.
                3. Wait for the candidate's answer before moving on.
                4. Keep it conversational.
                CONTEXT:
                """ + contextData;
        } else {
            // Marketing Logic (The one you wanted fixed)
            systemInstruction = """
                You are a friendly Customer Support AI for a business.
                
                CRITICAL RULES:
                1. Answer ONLY based on the context provided below.
                2. Do NOT dump the entire information at once.
                3. If the user asks a broad question (e.g., "tell me about the gym"), give a 1-sentence summary and ask specifically what they want to know (e.g., "Are you interested in pricing, location, or our facilities?").
                4. Keep answers short (max 2-3 sentences).
                
                CONTEXT:
                """ + contextData;
        }

        ObjectNode systemInst = root.putObject("system_instruction");
        systemInst.putArray("parts").addObject().put("text", systemInstruction);

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("temperature", 0.7); 

        ArrayNode contents = root.putArray("contents");
        contents.addObject().put("role", "user")
                .putArray("parts").addObject().put("text", userText);

        RequestBody body = RequestBody.create(
            mapper.writeValueAsString(root), 
            MediaType.get("application/json")
        );

        Request request = new Request.Builder()
            .url(GEMINI_URL + geminiApiKey)
            .post(body)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Gemini API Error: " + response.code());
                return "AI Error: " + response.code();
            }
            
            String responseBody = response.body().string();
            JsonNode responseJson = mapper.readTree(responseBody);
            
            if (responseJson.has("candidates") && responseJson.get("candidates").size() > 0) {
                JsonNode candidate = responseJson.get("candidates").get(0);
                if (candidate.has("content") && candidate.get("content").has("parts")) {
                    JsonNode parts = candidate.get("content").get("parts");
                    if (parts.size() > 0) {
                        return parts.get(0).get("text").asText();
                    }
                }
            }
        }
        return "I'm sorry, I couldn't generate a response.";
    }

    private void sendFullResponse(WebSocketSession session, String text) throws IOException {
        if (session.isOpen()) {
            ObjectNode response = mapper.createObjectNode();
            response.put("fullText", text); 
            session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));
        }
    }
}