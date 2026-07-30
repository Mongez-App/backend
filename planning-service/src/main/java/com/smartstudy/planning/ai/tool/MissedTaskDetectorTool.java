package com.smartstudy.planning.ai.tool;

import com.smartstudy.planning.ai.model.MissedTaskSummary;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MissedTaskDetectorTool {

    private static final Logger log = LoggerFactory.getLogger(MissedTaskDetectorTool.class);
    private final TaskRepository taskRepository;
    private final CourseRepository courseRepository;

    @Tool(name = "detect_missed_tasks", description = "Detect missed (overdue, incomplete) tasks for a user/course. Escalates priority based on proximity to exam date. Clears locked status on tasks that have become missed. Returns MissedTaskSummary with count, escalated info, and requiresFullReschedule flag.")
    public MissedTaskSummary detect(String userId, UUID courseId) {
        Course course = courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND"));

        LocalDate today = LocalDate.now();
        LocalDate examDate = course.getExamDate() != null
                ? course.getExamDate().atZone(ZoneOffset.UTC).toLocalDate()
                : LocalDate.now().plusYears(1);
        long daysToExam = ChronoUnit.DAYS.between(today, examDate);

        List<Task> missedTasks = taskRepository.findByUserIdAndCourseIdAndScheduledDateBeforeAndCompletedFalseAndMissedFalse(
                userId, courseId, today);

        if (missedTasks.isEmpty()) {
            return new MissedTaskSummary(0, 0, false, List.of(), daysToExam);
        }

        int escalatedCount = 0;
        for (Task task : missedTasks) {
            task.setMissed(true);
            task.setLocked(false);
            taskRepository.save(task);
            log.info("Task {} marked as missed (scheduled {}, today {})", task.getId(), task.getScheduledDate(), today);

            com.smartstudy.planning.model.Priority current = task.getPriority();
            com.smartstudy.planning.model.Priority escalated = escalate(current, daysToExam);
            if (escalated != null && escalated.compareTo(current) > 0) {
                task.setPriority(escalated);
                taskRepository.save(task);
                escalatedCount++;
            }
        }

        boolean requiresFullReschedule = !missedTasks.isEmpty();
        return new MissedTaskSummary(
                missedTasks.size(),
                escalatedCount,
                requiresFullReschedule,
                missedTasks.stream().map(Task::getId).toList(),
                daysToExam
        );
    }

    private com.smartstudy.planning.model.Priority escalate(
            com.smartstudy.planning.model.Priority current, long daysToExam) {
        com.smartstudy.planning.model.Priority target;
        if (daysToExam <= 7) {
            target = com.smartstudy.planning.model.Priority.HIGH;
        } else if (daysToExam <= 14) {
            target = com.smartstudy.planning.model.Priority.HIGH;
        } else if (daysToExam <= 30) {
            target = com.smartstudy.planning.model.Priority.HIGH;
        } else {
            target = com.smartstudy.planning.model.Priority.MEDIUM;
        }

        if (target.compareTo(current) > 0) {
            return target;
        }
        return null;
    }
}
