package com.aerosentinel.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantChatResponse(
        String answer,
        String language,
        List<SourceReferenceDto> sources,
        String observationPeriod,
        String status,
        List<String> limitations,
        String correlationId,
        String timestamp,
        String clarificationPrompt
) {
    public static AssistantChatResponse clarification(String prompt, String correlationId, String timestamp, String language) {
        return new AssistantChatResponse(
                prompt,
                language != null ? language : "en",
                List.of(),
                null,
                "NEEDS_CLARIFICATION",
                List.of("Location context required to retrieve local environmental observations."),
                correlationId,
                timestamp,
                prompt
        );
    }
}
