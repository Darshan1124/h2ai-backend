package com.h2.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class VoiceService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    // Gemini 1.5 Flash is Free and Multimodal (can hear audio)
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    public VoiceService() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    // --- 1. FREE Speech-to-Text (using Gemini) ---
    public String convertAudioToText(byte[] audioData) throws IOException {
        String base64Audio = Base64.getEncoder().encodeToString(audioData);

        // Build JSON for Gemini
        ObjectNode root = mapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

        // Part 1: Instruction
        parts.addObject().put("text", "Please transcribe this audio exactly. Do not add any description, just the spoken text.");

        // Part 2: The Audio File
        ObjectNode inlineData = parts.addObject().putObject("inline_data");
        inlineData.put("mime_type", "audio/webm"); // Browser sends WebM
        inlineData.put("data", base64Audio);

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
                System.err.println("Gemini STT Error: " + response.body().string());
                return "";
            }
            JsonNode json = mapper.readTree(response.body().string());
            
            // FIX: Use .path() for safe traversal to avoid NullPointerException
            if (json.has("candidates") && !json.get("candidates").isEmpty()) {
                String text = json.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text")
                        .asText(""); // Default to empty string if missing
                
                return text.trim();
            }
            return "";
        }
    }

    // --- 2. FREE Text-to-Speech (using Google Translate Hack) ---
    public byte[] convertTextToAudio(String text) throws IOException {
        if (text == null || text.isEmpty()) return new byte[0];

        // Clean text (remove markdown stars) to prevent weird sounds
        String cleanText = text.replaceAll("[*#]", "").trim();
        
        // Encode text for URL
        String encodedText = URLEncoder.encode(cleanText, StandardCharsets.UTF_8.toString());

        // Use Google Translate TTS endpoint (Unofficial but widely used for free tiers)
        // client=tw-ob is the magic parameter
        String ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&q=" + encodedText + "&tl=en&client=tw-ob";

        Request request = new Request.Builder()
            .url(ttsUrl)
            .get() // GET request for this one
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("TTS Failed: " + response.code());
            return response.body().bytes();
        }
    }
}