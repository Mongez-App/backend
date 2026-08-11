package com.smartstudy.planning.ai.tool;

import com.smartstudy.planning.ai.model.AvailableSlot;
import com.smartstudy.planning.ai.model.ExtractedTask;
import com.smartstudy.planning.ai.model.ScheduleResult;
import com.smartstudy.planning.ai.model.ScheduledPart;
import com.smartstudy.planning.model.Priority;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SchedulerEngineTool {

    private static final int MIN_SLOT_MINUTES = 20;

    @Tool(name = "schedule_tasks", description = "Schedule a list of ordered study tasks into available time slots. Tasks are sorted by priority (HIGH first) then by sequenceOrder. Tasks of the same material must maintain their relative sequenceOrder. Minimum allocation is 20 minutes. Returns ScheduleResult with scheduled parts, unscheduled tasks, and overCapacity flag.")
    public ScheduleResult schedule(List<ExtractedTask> tasks, List<AvailableSlot> slots) {
        tasks = new ArrayList<>(tasks);
        tasks.sort(Comparator
                .comparingInt((ExtractedTask t) -> t.priority() != null ? t.priority().ordinal() : Priority.MEDIUM.ordinal())
                .thenComparingInt(ExtractedTask::sequenceOrder));

        List<AvailableSlot> slotList = new ArrayList<>(slots);
        slotList.sort(Comparator.comparing(AvailableSlot::date));
        int[] remainingMinutes = slotList.stream().mapToInt(AvailableSlot::availableMinutes).toArray();

        List<ScheduledPart> scheduledParts = new ArrayList<>();
        List<ExtractedTask> unscheduledTasks = new ArrayList<>();

        for (ExtractedTask task : tasks) {
            int remaining = task.estimatedMinutes();
            List<ScheduledPart> parts = new ArrayList<>();
            int partNumber = 0;
            int totalParts = 0;

            for (int i = 0; i < slotList.size(); i++) {
                if (remainingMinutes[i] < MIN_SLOT_MINUTES) {
                    continue;
                }
                int allocate = Math.min(remaining, remainingMinutes[i]);
                partNumber++;
                totalParts++;
                ScheduledPart part = new ScheduledPart(
                        totalParts > 1 ? task.title() + " - Part " + partNumber + " of " + totalParts : task.title(),
                        slotList.get(i).date(),
                        allocate,
                        task.sequenceOrder(),
                        totalParts > 1 ? partNumber : null,
                        totalParts > 1 ? totalParts : null,
                        task.description(),
                        task.coveredSections(),
                        task.priority() != null ? task.priority() : Priority.MEDIUM
                );
                parts.add(part);
                remainingMinutes[i] -= allocate;
                remaining -= allocate;
                if (remaining == 0) {
                    break;
                }
            }

            if (remaining > 0) {
                unscheduledTasks.add(task);
            } else {
                scheduledParts.addAll(parts);
            }
        }

        return new ScheduleResult(scheduledParts, unscheduledTasks, !unscheduledTasks.isEmpty());
    }
}