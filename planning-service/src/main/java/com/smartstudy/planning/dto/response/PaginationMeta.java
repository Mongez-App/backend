package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaginationMeta(
    @JsonProperty("page") int page,
    @JsonProperty("size") int size,
    @JsonProperty("has_next") boolean hasNext
) {}
