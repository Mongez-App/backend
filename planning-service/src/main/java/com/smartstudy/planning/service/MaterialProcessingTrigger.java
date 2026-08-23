package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.AgentPlanResult;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialStatus;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Runs the AI study-plan pipeline for a material outside the caller's request
 * thread. Lives in its own bean so that Spring's @Async proxy actually
 * intercepts the call (self-invocation inside {@link CourseService} would run
 * synchronously and block the upload request for the whole agent run).
 */
@Component
public class MaterialProcessingTrigger {

    private static final Logger log = LoggerFactory.getLogger(MaterialProcessingTrigger.class);

    private final MaterialRepository materialRepository;
    private final TaskRepository taskRepository;
    private final StudyPlannerAgent studyPlannerAgent;
    private final UserPreferencesService userPreferencesService;
    private final RoadmapService roadmapService;

    public MaterialProcessingTrigger(MaterialRepository materialRepository,
                                     TaskRepository taskRepository,
                                     StudyPlannerAgent studyPlannerAgent,
                                     UserPreferencesService userPreferencesService,
                                     RoadmapService roadmapService) {
        this.materialRepository = materialRepository;
        this.taskRepository = taskRepository;
        this.studyPlannerAgent = studyPlannerAgent;
        this.userPreferencesService = userPreferencesService;
        this.roadmapService = roadmapService;
    }

    @Async
    public void processMaterial(String userId, UUID courseId, UUID materialId,
                                int dailyStudyMinutes, String preferredDays) {
        Material material = materialRepository.findByIdAndUserId(materialId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MATERIAL_NOT_FOUND"));
        material.setStatus(MaterialStatus.PROCESSING);
        material.setProcessingStartedAt(Instant.now());
        materialRepository.save(material);

        try {
            boolean isIncremental = taskRepository.existsByCourseIdAndUserIdAndMaterialIdIsNotNull(courseId, userId);
            AgentPlanResult result = studyPlannerAgent.generatePlan(userId, courseId, materialId,
                    dailyStudyMinutes, preferredDays, isIncremental);

            if ("error".equals(result.status()) || "over_capacity".equals(result.status())) {
                material.setStatus(MaterialStatus.FAILED);
                log.error("Agent failed for material {}: {}", materialId, result.alert().message());
                material.setErrorMessage(result.alert().message());
            } else {
                material.setStatus(MaterialStatus.READY);
                material.setProcessedAt(Instant.now());
                log.info("Agent completed for material {}: status={}", materialId, result.status());

                try {
                    rescheduleRoadmap(userId);
                } catch (Exception rescheduleEx) {
                    // Keep the material processing result intact even if roadmap recalculation fails.
                    log.warn("Roadmap reschedule failed after material {} processing: {}", materialId,
                            rescheduleEx.getMessage(), rescheduleEx);
                }
            }
            materialRepository.save(material);
        } catch (Exception ex) {
            log.error("Unexpected exception during agent processing for material {}: {}", materialId, ex.getMessage(), ex);
            material.setStatus(MaterialStatus.FAILED);
            material.setErrorMessage(ex.getMessage());
            materialRepository.save(material);
        }
    }

    private void rescheduleRoadmap(String userId) {
        UserPreferencesService.StudyPreferences preferences = userPreferencesService.resolve(userId);
        roadmapService.reschedule(userId, preferences.dailyStudyMinutes(), preferences.preferredDays());
    }
}
