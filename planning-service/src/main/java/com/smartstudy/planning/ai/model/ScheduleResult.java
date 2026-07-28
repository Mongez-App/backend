package com.smartstudy.planning.ai.model;

import java.util.List;

public record ScheduleResult(
    List<ScheduledPart> scheduledParts,
    List<ExtractedTask> unscheduledTasks,
    boolean overCapacity
) {}
