package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Internal DTO matching the structured JSON the LLM is instructed to return.
 * Not exposed to the client directly — used for parsing and persistence.
 */
public record LlmStructuredResponse(
    @JsonProperty("answer") String answer,
    @JsonProperty("used_context") boolean usedContext,
    @JsonProperty("confidence") String confidence,
    @JsonProperty("suggested_follow_up") String suggestedFollowUp,
    @JsonProperty("sources") List<SourceReference> sources
) {
    public record SourceReference(
        @JsonProperty("section") String section,
        @JsonProperty("page") Integer page
    ) {}
}
