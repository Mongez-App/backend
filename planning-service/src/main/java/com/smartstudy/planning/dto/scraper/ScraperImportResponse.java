package com.smartstudy.planning.dto.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScraperImportResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("data")
    private ScraperData data;

    @JsonProperty("message")
    private String message;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScraperData {

        @JsonProperty("courseId")
        private String courseId;

        @JsonProperty("courseName")
        private String courseName;

        @JsonProperty("resources")
        private List<ScraperResource> resources;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScraperResource {

        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("duration")
        private int duration;

        @JsonProperty("type")
        private String type;
    }
}