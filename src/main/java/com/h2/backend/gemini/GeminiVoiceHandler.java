package com.h2.backend.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class GeminiVoiceHandler extends TextWebSocketHandler {
    
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client;
    
    // Regex to remove markdown (*, #, _) so the Voice doesn't say "Asterisk"
    private static final Pattern MARKDOWN_CLEANER = Pattern.compile("[*#_`]");

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    // Using Gemini 2.5 Flash (Standard stable model)
    private static final String GEMINI_STREAM_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=";

    // Base Persona - The AI always acts like this, plus the user's details
 // Updated: Sharp, Concise, High-Value Persona
    private static final String BASE_PERSONA = """
        You are Alex, Senior Director at TechFix.
        
        STRICT RULES:
        1. BE CONCISE: Maximum 20 words per answer. Time is money.
        2. TONE: Professional, decisive, and sharp. No fluff.
        3. CONTENT: Answer the user's question directly. Do not repeat the question.
        
        EXAMPLES:
        User: "How much for a laptop?"
        You: "Our comprehensive laptop restoration is $50, which includes a full diagnostic check."
        
        User: "Can I come in?"
        You: "Certainly. We welcome clients between 9 AM and 6 PM daily."
        
        COMPANY DATA:
        """;

    public GeminiVoiceHandler() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode json = mapper.readTree(message.getPayload());

            if (json.has("text")) {
                String userMessage = json.get("text").asText();
                // Get the custom company details from the frontend
                String companyContext = json.has("config") ? json.get("config").asText() : "";
                
                System.out.println("Processing: " + userMessage);
                streamGeminiResponse(session, userMessage, companyContext);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void streamGeminiResponse(WebSocketSession session, String userText, String companyContext) {
        try {
            ObjectNode root = mapper.createObjectNode();
            
            // --- 1. Construct System Instruction (The Brain) ---
            // We combine the fixed persona with the user's dynamic input
            String fullSystemPrompt = BASE_PERSONA + "\n" + companyContext;
            
            ObjectNode systemInst = root.putObject("system_instruction");
            systemInst.putArray("parts").addObject().put("text", fullSystemPrompt);

            // --- 2. Add User Message ---
            ArrayNode contents = root.putArray("contents");
            contents.addObject().put("role", "user")
                   .putArray("parts").addObject().put("text", userText);

            // --- 3. Build Request ---
            RequestBody body = RequestBody.create(
                mapper.writeValueAsString(root), 
                MediaType.get("application/json")
            );

            Request request = new Request.Builder()
                .url(GEMINI_STREAM_URL + geminiApiKey)
                .post(body)
                .build();

            // --- 4. Stream & Clean Response ---
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Gemini Error: " + response.code());
                    return;
                }

                BufferedSource source = response.body().source();
                while (!source.exhausted()) {
                    String line = source.readUtf8Line();
                    if (line != null && line.startsWith("data: ")) {
                        String jsonStr = line.substring(6);
                        if (jsonStr.trim().equals("[DONE]")) break;

                        try {
                            JsonNode chunkNode = mapper.readTree(jsonStr);
                            if (chunkNode.has("candidates")) {
                                JsonNode parts = chunkNode.get("candidates").get(0).get("content").get("parts");
                                if (parts != null && parts.size() > 0) {
                                    String rawText = parts.get(0).get("text").asText();
                                    
                                    // CLEANING: Remove * and # before sending to Frontend
                                    String cleanText = MARKDOWN_CLEANER.matcher(rawText).replaceAll("");
                                    
                                    if (!cleanText.isEmpty()) {
                                        sendChunk(session, cleanText);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                sendChunk(session, "[END]");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendChunk(WebSocketSession session, String text) throws IOException {
        if (session.isOpen()) {
            ObjectNode response = mapper.createObjectNode();
            response.put("chunk", text);
            session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));
        }
    }
}