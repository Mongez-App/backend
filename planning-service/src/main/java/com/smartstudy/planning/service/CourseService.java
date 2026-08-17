package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.AgentPlanResult;
import com.smartstudy.planning.dto.request.CreateCourseRequest;
import com.smartstudy.planning.dto.request.CreateMaterialRequest;
import com.smartstudy.planning.dto.request.UpdateCourseRequest;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.dto.response.CourseResponse;
import com.smartstudy.planning.dto.response.CreateMaterialResponse;
import com.smartstudy.planning.dto.response.MaterialResponse;
import com.smartstudy.planning.dto.response.StatusResponse;
import com.smartstudy.planning.dto.response.UploadMaterialResponse;
import com.smartstudy.planning.dto.scraper.ScraperImportResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.CourseType;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialStatus;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.model.StudyBlock;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.model.TeamMember;
import com.smartstudy.planning.processing.QdrantIndexingService;
import com.smartstudy.planning.repository.ChatMessageRepository;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.StudyBlockRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.planning.repository.TeamMemberRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);
    private static final String SCRAPER_URL = "https://mongez-scraper.vercel.app/api/v1/imports";
    private final CourseRepository courseRepository;
    private final MaterialRepository materialRepository;
    private final StudyBlockRepository studyBlockRepository;
    private final TaskRepository taskRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EventRepository eventRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final QdrantIndexingService qdrantIndexingService;
    private final StudyPlannerAgent studyPlannerAgent;
    private final TaskPriorityService taskPriorityService;
    private final UserPreferencesService userPreferencesService;
    private final RestTemplateBuilder restTemplateBuilder;
    private final FileStorageService fileStorageService;
    private final RoadmapService roadmapService;

    @Value("${smartstudy.url-course.min-split-minutes:1}")
    private int minSplitMinutes;

    @Value("${smartstudy.url-course.max-split-minutes:60}")
    private int maxSplitMinutes;

    @Transactional(readOnly = true)
    public List<CourseResponse> getCourses(String userId) {
        log.info("Fetching courses for userId: {}", userId);
        return courseRepository.findByUserIdAndHiddenFalseOrderByCreatedAtAsc(userId)
                .stream()
                .map(course -> toResponse(userId, course, false, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourse(String userId, UUID courseId) {
        log.info("Fetching course {} for userId: {}", courseId, userId);
        return toResponse(userId, getOwnedCourse(userId, courseId), true, null);
    }

    @Transactional
    public CourseResponse createCourse(String userId, CreateCourseRequest request, int dailyStudyMinutes,
                                       Set<DayOfWeek> preferredDays) {
        CourseType courseType = request.courseType() != null ? request.courseType() : CourseType.MATERIAL_COURSE;
        String materialUrl = request.materialUrl();

        if (courseType == CourseType.URL_COURSE && (materialUrl == null || materialUrl.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MATERIAL_URL_REQUIRED_FOR_URL_COURSE");
        }

        Course course = Course.builder()
                .userId(userId)
                .name(request.name())
                .courseCode(request.courseCode())
                .imageUrl(request.imageUrl())
                .startDate(request.startDate())
                .examDate(request.examDate())
                .courseType(courseType)
                .materialUrl(materialUrl)
                .hidden(false)
                .build();

        List<ScraperImportResponse.ScraperResource> scraperResources = null;

        if (courseType == CourseType.URL_COURSE) {
            scraperResources = callScraperAndGetResources(materialUrl);
        }

        Course saved = courseRepository.save(course);

        if (scraperResources != null && !scraperResources.isEmpty()) {
            importUrlCourseResources(userId, saved.getId(), saved.getStartDate().atZone(ZoneOffset.UTC).toLocalDate(),
                    dailyStudyMinutes, preferredDays, scraperResources);
        }

        createInitialStudyBlock(userId, saved);
        rescheduleRoadmap(userId);
        return toResponse(userId, saved, false, "Your roadmap has been generated for " + saved.getName() + ".");
    }

    private List<ScraperImportResponse.ScraperResource> callScraperAndGetResources(String materialUrl) {
        log.info("Calling scraper API for URL: {}", materialUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        Map<String, String> body = new HashMap<>();
        body.put("url", materialUrl);
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            RestTemplate restTemplate = restTemplateBuilder.build();
            ResponseEntity<ScraperImportResponse> response = restTemplate.exchange(
                    SCRAPER_URL,
                    HttpMethod.POST,
                    requestEntity,
                    ScraperImportResponse.class
            );

            ScraperImportResponse scraperResponse = response.getBody();
            if (scraperResponse == null || !scraperResponse.isSuccess() || scraperResponse.getData() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SCRAPER_IMPORT_FAILED");
            }

            return scraperResponse.getData().getResources();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Scraper API call failed for URL: {}", materialUrl, ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SCRAPER_IMPORT_FAILED");
        }
    }

    private void importUrlCourseResources(String userId, UUID courseId, LocalDate startDate, int dailyStudyMinutes,
                                          Set<DayOfWeek> preferredDays, List<ScraperImportResponse.ScraperResource> resources) {
        int effectiveMax = Math.min(maxSplitMinutes, dailyStudyMinutes);
        int sequenceOrder = 0;
        LocalDate firstStudyDay = nextPreferredDay(startDate, preferredDays);

        for (ScraperImportResponse.ScraperResource resource : resources) {
            int duration = resource.getDuration();
            String resourceName = resource.getName();

            if (duration <= effectiveMax || effectiveMax <= minSplitMinutes) {
                taskRepository.save(Task.builder()
                        .userId(userId)
                        .courseId(courseId)
                        .title(resourceName)
                        .durationMinutes(duration)
                        .priority(Priority.MEDIUM)
                        .completed(false)
                        .scheduledDate(firstStudyDay)
                        .sequenceOrder(sequenceOrder++)
                        .locked(false)
                        .missed(false)
                        .build());
            } else {
                int remaining = duration;
                int partNumber = 0;
                int totalParts = (int) Math.ceil((double) duration / effectiveMax);
                LocalDate currentDate = firstStudyDay;
                int dayRemaining = dailyStudyMinutes;

                while (remaining > 0) {
                    int chunk = Math.min(remaining, effectiveMax);

                    if (chunk > dayRemaining) {
                        currentDate = nextPreferredDay(currentDate.plusDays(1), preferredDays);
                        dayRemaining = dailyStudyMinutes;
                    }

                    partNumber++;
                    taskRepository.save(Task.builder()
                            .userId(userId)
                            .courseId(courseId)
                            .title(resourceName + " - Part " + partNumber)
                            .durationMinutes(chunk)
                            .priority(Priority.MEDIUM)
                            .completed(false)
                            .scheduledDate(currentDate)
                            .sequenceOrder(sequenceOrder++)
                            .splitPart(partNumber)
                            .totalParts(totalParts)
                            .locked(false)
                            .missed(false)
                            .build());
                    dayRemaining -= chunk;
                    remaining -= chunk;
                }
            }
        }
    }

    /**
     * First date on or after {@code from} that falls on one of the user's preferred
     * study days, so imported tasks never land on a day the user excluded.
     */
    private LocalDate nextPreferredDay(LocalDate from, Set<DayOfWeek> preferredDays) {
        if (preferredDays == null || preferredDays.isEmpty()) {
            return from;
        }
        LocalDate candidate = from;
        for (int i = 0; i < 7; i++) {
            if (preferredDays.contains(candidate.getDayOfWeek())) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        return from;
    }

    @Transactional
    public CourseResponse updateCourse(String userId, UUID courseId, UpdateCourseRequest request) {
        log.info("Updating course {} for userId: {}", courseId, userId);
        Course course = getStrictlyOwnedCourse(userId, courseId);
        Instant previousExamDate = course.getExamDate();
        if (request.name() != null) {
            course.setName(request.name());
        }
        if (request.courseCode() != null) {
            course.setCourseCode(request.courseCode());
        }
        if (request.imageUrl() != null) {
            course.setImageUrl(request.imageUrl());
        }
        if (request.startDate() != null) {
            course.setStartDate(request.startDate());
        }
        if (request.examDate() != null) {
            course.setExamDate(request.examDate());
        }
        if (request.courseType() != null) {
            course.setCourseType(request.courseType());
        }
        if (request.materialUrl() != null) {
            course.setMaterialUrl(request.materialUrl());
        }
        if (request.hidden() != null) {
            course.setHidden(request.hidden());
        }
        if (request.examDate() != null && !request.examDate().equals(previousExamDate)) {
            refreshMaterialTaskPriorities(userId, courseId);
        }
        return toResponse(userId, course, true, "Your roadmap was updated to reflect the course changes.");
    }

    @Transactional
    public StatusResponse deleteCourse(String userId, UUID courseId) {
        log.info("Deleting course {} for userId: {}", courseId, userId);
        Course course = getStrictlyOwnedCourse(userId, courseId);

        List<Task> tasks = taskRepository.findByUserIdAndCourseIdOrderByCreatedAtAsc(userId, courseId);
        if (!tasks.isEmpty()) {
            chatMessageRepository.deleteAllByTaskIdIn(tasks.stream().map(Task::getId).toList());
        }
        eventRepository.deleteByUserIdAndCourseId(userId, courseId);
        taskRepository.deleteAll(tasks);
        studyBlockRepository.deleteByCourseIdAndUserId(courseId, userId);

        List<Material> materials = materialRepository.findByCourseIdAndUserIdOrderByUploadedAtAsc(courseId, userId);
        for (Material material : materials) {
            qdrantIndexingService.deleteByMaterialId(material.getId());
        }
        materialRepository.deleteAll(materials);
        fileStorageService.deleteCourseDir(userId, courseId);

        courseRepository.delete(course);
        rescheduleRoadmap(userId);
        return new StatusResponse("success", new AlertResponse(
                course.getName() + " has been removed along with its materials, tasks, events, and roadmap."));
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> getMaterials(String userId, UUID courseId) {
        log.info("Fetching materials for course {} | userId: {}", courseId, userId);
        getOwnedCourse(userId, courseId);
        List<MaterialResponse> userMaterials = materialRepository.findByCourseIdAndUserIdOrderByUploadedAtAsc(courseId, userId)
                .stream()
                .map(this::toMaterialResponse)
                .toList();
        if (!userMaterials.isEmpty()) {
            return userMaterials;
        }
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND"));
        if (course.getTeamId() != null && teamMemberRepository.existsByTeamIdAndUserIdAndStatus(
                course.getTeamId(), userId, com.smartstudy.planning.enums.TeamMemberStatus.ACCEPTED)) {
            return materialRepository.findByCourseId(courseId)
                    .stream()
                    .map(this::toMaterialResponse)
                    .toList();
        }
        return userMaterials;
    }

    @Transactional
    public MaterialResponse createMaterial(String userId, UUID courseId,
                                              MultipartFile file, int dailyStudyMinutes, String preferredDays) {
        String originalFileName = file.getOriginalFilename();
        String materialName = (originalFileName == null || originalFileName.isBlank())
                ? "material.pdf" : originalFileName;
        log.info("Creating material for course {} | userId: {} | fileName: {}", courseId, userId, materialName);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MATERIAL_FILE_EMPTY");
        }
        Course course = getOwnedCourse(userId, courseId);
        Material material = Material.builder()
                .courseId(courseId)
                .userId(userId)
                .name(materialName)
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .status(MaterialStatus.PENDING)
                .build();
        Material saved = materialRepository.save(material);

        try {
            String filePath = fileStorageService.save(
                    userId, courseId, saved.getId(), file.getInputStream());
            saved.setFilePath(filePath);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "MATERIAL_UPLOAD_FAILED", ex);
        }

        saved.setPageCount(countPdfPages(fileStorageService.resolve(saved.getFilePath())));
        saved.setStatus(MaterialStatus.PROCESSING);
        materialRepository.save(saved);
        triggerAgentForMaterial(userId, courseId, saved.getId(), dailyStudyMinutes, preferredDays);
        return toMaterialResponse(saved);
    }

    /**
     * Step 1 of two-step upload: register material metadata (JSON).
     * Returns the materialId and upload URL.
     */
    @Transactional
    public CreateMaterialResponse registerMaterialMetadata(String userId, UUID courseId,
                                                           CreateMaterialRequest request) {
        log.info("Registering material metadata for course {} | userId: {} | fileName: {}",
                courseId, userId, request.fileName());
        getOwnedCourse(userId, courseId);

        Material material = Material.builder()
                .courseId(courseId)
                .userId(userId)
                .name(request.fileName())
                .contentType(request.contentType())
                .fileSizeBytes(request.fileSizeBytes())
                .pageCount(request.pageCount())
                .deviceFileUri(request.deviceFileUri())
                .status(MaterialStatus.PENDING)
                .build();
        Material saved = materialRepository.save(material);

        String uploadUrl = "/api/v1/upload/" + saved.getId();
        log.info("Material metadata registered: {} — upload at {}", saved.getId(), uploadUrl);
        return new CreateMaterialResponse(saved.getId(), uploadUrl, MaterialStatus.PENDING.name());
    }

    /**
     * Step 2 of two-step upload: receive the binary file for a PENDING material.
     */
    @Transactional
    public UploadMaterialResponse uploadMaterialFile(String userId, UUID materialId,
                                                      MultipartFile file,
                                                      int dailyStudyMinutes, String preferredDays) {
        log.info("Uploading file for material {} | userId: {}", materialId, userId);

        Material material = materialRepository.findByIdAndUserIdAndStatus(materialId, userId, MaterialStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MATERIAL_NOT_FOUND"));

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MATERIAL_FILE_EMPTY");
        }

        try {
            String filePath = fileStorageService.save(
                    userId, material.getCourseId(), materialId, file.getInputStream());
            material.setFilePath(filePath);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "MATERIAL_UPLOAD_FAILED", ex);
        }

        if (material.getPageCount() == null || material.getPageCount() <= 0) {
            material.setPageCount(countPdfPages(fileStorageService.resolve(material.getFilePath())));
        }
        material.setStatus(MaterialStatus.PROCESSING);
        material.setProcessingStartedAt(Instant.now());
        materialRepository.save(material);

        triggerAgentForMaterial(userId, material.getCourseId(), materialId, dailyStudyMinutes, preferredDays);

        return new UploadMaterialResponse(
                materialId,
                MaterialStatus.PROCESSING.name(),
                "File uploaded successfully. AI processing has started in the background."
        );
    }

    @Async
    public void triggerAgentForMaterial(String userId, UUID courseId, UUID materialId,
                                        int dailyStudyMinutes, String preferredDays) {
        Material material = materialRepository.findByIdAndUserId(materialId, userId)
                .orElseThrow(() -> new IllegalStateException("Material not found for agent: " + materialId));
        material.setStatus(MaterialStatus.PROCESSING);
        materialRepository.save(material);

        try {
            boolean isIncremental = taskRepository.existsByCourseIdAndUserIdAndMaterialIdIsNotNull(courseId, userId);
            AgentPlanResult result = studyPlannerAgent.generatePlan(userId, courseId, materialId, dailyStudyMinutes, preferredDays, isIncremental);

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

    @Transactional
    public StatusResponse deleteMaterial(String userId, UUID courseId, UUID materialId) {
        log.info("Deleting material {} from course {} | userId: {}", materialId, courseId, userId);
        getOwnedCourse(userId, courseId);
        Material material = materialRepository.findByIdAndUserId(materialId, userId)
                .filter(m -> courseId.equals(m.getCourseId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MATERIAL_NOT_FOUND"));

        deleteTasksForMaterial(userId, materialId);
        qdrantIndexingService.deleteByMaterialId(materialId);
        fileStorageService.delete(userId, courseId, materialId);
        materialRepository.delete(material);
        rescheduleRoadmap(userId);
        return new StatusResponse("success",
                new AlertResponse("The material and its tasks were removed from your roadmap."));
    }

    private void deleteTasksForMaterial(String userId, UUID materialId) {
        List<Task> tasks = taskRepository.findByMaterialIdAndUserId(materialId, userId);
        if (tasks.isEmpty()) {
            return;
        }
        List<UUID> taskIds = tasks.stream().map(Task::getId).toList();
        chatMessageRepository.deleteAllByTaskIdIn(taskIds);
        eventRepository.deleteByUserIdAndTaskIdIn(userId, taskIds);
        studyBlockRepository.deleteByUserIdAndTaskIdIn(userId, taskIds);
        taskRepository.deleteAll(tasks);
    }

    private Integer countPdfPages(Path pdfPath) {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            return document.getNumberOfPages();
        } catch (IOException ex) {
            log.warn("Could not read page count from {}: {}", pdfPath, ex.getMessage());
            return null;
        }
    }

    public Course getOwnedCourse(String userId, UUID courseId) {
        Course course = courseRepository.findByIdAndUserId(courseId, userId)
                .orElse(null);
        if (course == null) {
            course = courseRepository.findById(courseId).orElse(null);
            if (course == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND");
            }
            if (course.getTeamId() != null && teamMemberRepository.existsByTeamIdAndUserIdAndStatus(
                    course.getTeamId(), userId, com.smartstudy.planning.enums.TeamMemberStatus.ACCEPTED)) {
                return course;
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND");
        }
        return course;
    }

    /**
     * Like getOwnedCourse, but requires the caller to be the actual owner.
     * Team/organization courses are visible to members, yet only the owner may
     * modify or delete them — they stay with the team otherwise.
     */
    private Course getStrictlyOwnedCourse(String userId, UUID courseId) {
        Course course = getOwnedCourse(userId, courseId);
        if (!userId.equals(course.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COURSE_NOT_OWNED");
        }
        return course;
    }

    private CourseResponse toResponse(String userId, Course course, boolean includeHidden, String alertMessage) {
        return new CourseResponse(
                course.getId(),
                course.getUserId(),
                course.getName(),
                course.getCourseCode(),
                course.getImageUrl(),
                course.getStartDate(),
                course.getExamDate(),
                course.getCourseType(),
                course.getMaterialUrl(),
                includeHidden ? course.isHidden() : null,
                completionPercentage(userId, course.getId()),
                alertMessage != null ? new AlertResponse(alertMessage) : null);
    }

    private MaterialResponse toMaterialResponse(Material material) {
        double sizeMb = material.getFileSizeBytes() != null
                ? material.getFileSizeBytes() / 1_000_000.0 : 0.0;
        return new MaterialResponse(
                material.getId(),
                material.getName(),
                material.getContentType(),
                material.getFileSizeBytes(),
                material.getPageCount(),
                material.getDeviceFileUri(),
                sizeMb,
                material.getStatus().name(),
                material.getUploadedAt(),
                material.getProcessedAt(),
                material.getErrorMessage());
    }

    private double completionPercentage(String userId, UUID courseId) {
        long total = taskRepository.countByUserIdAndCourseId(userId, courseId);
        if (total == 0) {
            return 0.0;
        }
        long completed = taskRepository.countByUserIdAndCourseIdAndCompletedTrue(userId, courseId);
        return Math.round((completed * 10000.0) / total) / 100.0;
    }

    private void createInitialStudyBlock(String userId, Course course) {
        studyBlockRepository.save(StudyBlock.builder()
                .userId(userId)
                .courseId(course.getId())
                .topic(course.getName() + " overview")
                .scheduledDate(course.getStartDate().atZone(ZoneOffset.UTC).toLocalDate())
                .durationMinutes(60)
                .completed(false)
                .build());
    }

    private void rescheduleRoadmap(String userId) {
        UserPreferencesService.StudyPreferences preferences = userPreferencesService.resolve(userId);
        roadmapService.reschedule(userId, preferences.dailyStudyMinutes(), preferences.preferredDays());
    }

    private void refreshMaterialTaskPriorities(String userId, UUID courseId) {
        List<Task> tasks = taskRepository.findByUserIdAndCourseIdOrderByCreatedAtAsc(userId, courseId).stream()
                .filter(task -> task.getMaterialId() != null)
                .toList();

        if (tasks.isEmpty()) {
            return;
        }

        for (Task task : tasks) {
            task.setPriority(taskPriorityService.determinePriority(userId, courseId, task.getScheduledDate()));
        }

        taskRepository.saveAll(tasks);
        log.info("Refreshed priorities for {} material tasks in course {} after exam date change",
                tasks.size(), courseId);
    }
}
