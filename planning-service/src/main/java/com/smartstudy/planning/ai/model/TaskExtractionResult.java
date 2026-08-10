package com.smartstudy.planning.ai.model;

import java.util.List;

public record TaskExtractionResult(
    String summary,
    List<ExtractedTask> tasks
) {}
