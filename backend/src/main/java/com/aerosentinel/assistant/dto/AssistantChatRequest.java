package com.aerosentinel.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssistantChatRequest(
        @NotBlank(message = "Message cannot be empty")
        @Size(max = 1000, message = "Message must not exceed 1000 characters")
        String message,

        @Valid
        LocationDto location,

        String locale
) {}
