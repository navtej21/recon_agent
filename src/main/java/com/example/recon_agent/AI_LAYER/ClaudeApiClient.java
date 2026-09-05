package com.example.recon_agent.AI_LAYER;
/*
 * Minimal wrapper around Claude's Messages API - sends one prompt, returns
 * the raw text of the first content block. No conversation history, no
 * tool use - just single-shot completions for exception explanations.

 */

import com.example.recon_agent.MODEL.ExceptionExplanation;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;


@Component
public class ClaudeApiClient {

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.url}")
    private String apiUrl;

    @Value("${anthropic.model}")
    private String apiModel;


    private RestTemplate restTemplate = new RestTemplate();


    public String complete(String prompt) {

        HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.set("x-api-key", apiKey);
        httpHeaders.set("anthropic-version", "2023-06-01");
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);


        Map<String, Object> body = Map.of(
                "model", apiModel,
                "max_tokens", 300,
                "messages", List.of(Map.of("role", "user", "content", prompt)));


        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, httpHeaders);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(apiUrl, request, Map.class);

        List<Map<String, String>> content = (List<Map<String, String>>) response.get("content");
        return content.get(0).get("text");
    }


    public ExceptionExplanation explainException(String contextDesc) {
        String specificprompt = """
                You are a payments reconciliation analyst. Given the following
                unresolved transaction record(s) that could not be automatically
                matched across Razorpay settlement, bank statement, and merchant
                ledger sources, explain the most likely cause in plain English.
                
                Respond ONLY with valid JSON, no markdown formatting, no
                preamble, in exactly this shape:
                {"likelyCause": "...", "recommendation": "..."}
                
                Transaction context:
                %s
                """.formatted(contextDesc);

        String rawResponse = complete(specificprompt);

        return parseExplanation(rawResponse);
    }


    private ExceptionExplanation parseExplanation(String rawJson) {
        // strip potential markdown fences Claude sometimes adds despite instructions
        String cleaned = rawJson.replaceAll("```json|```", "").trim();

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(cleaned, ExceptionExplanation.class);
        } catch (Exception e) {
            return new ExceptionExplanation("Unable to parse LLM response", "Manual review required");
        }
    }
}

