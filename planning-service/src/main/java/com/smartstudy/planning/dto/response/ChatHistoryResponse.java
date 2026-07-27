package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Paginated chat history response for GET endpoint.
 */
public record ChatHistoryResponse(
    @JsonProperty("pagination") PaginationMeta pagination,
    @JsonProperty("messages") List<ChatMessageResponse> messages
) {}
