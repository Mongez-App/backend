package com.smartstudy.planning.service;

import com.smartstudy.planning.client.IdentityServiceClient;
import com.smartstudy.planning.config.StorageProperties;
import com.smartstudy.planning.dto.request.OrgCreateCourseRequest;
import com.smartstudy.planning.dto.request.OrgCreateEventRequest;
import com.smartstudy.planning.dto.request.OrgCreateTeamRequest;
import com.smartstudy.planning.dto.request.OrgMemberActionRequest;
import com.smartstudy.planning.dto.response.OrgJoinRequestListResponse;
import com.smartstudy.planning.dto.response.OrgJoinRequestResponse;
import com.smartstudy.planning.dto.response.OrgMemberStatusResponse;
import com.smartstudy.planning.dto.response.OrgCourseListResponse;
import com.smartstudy.planning.dto.response.OrgCourseResponse;
import com.smartstudy.planning.dto.response.OrgEventListResponse;
import com.smartstudy.planning.dto.response.OrgEventResponse;
import com.smartstudy.planning.dto.response.OrgFileUploadResponse;
import com.smartstudy.planning.dto.response.OrgMaterialListResponse;
import com.smartstudy.planning.dto.response.OrgMaterialResponse;
import com.smartstudy.planning.dto.response.OrgMemberActionResponse;
import com.smartstudy.planning.dto.response.OrgMemberListResponse;
import com.smartstudy.planning.dto.response.OrgMemberResponse;
import com.smartstudy.planning.dto.response.OrgTeamListResponse;
import com.smartstudy.planning.dto.response.OrgTeamResponse;
import com.smartstudy.planning.dto.response.UserSummaryData;
import com.smartstudy.planning.enums.TeamMemberStatus;
import com.smartstudy.planning.exception.OrgApiException;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.CourseType;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.EventType;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialStatus;
import com.smartstudy.planning.model.Team;
import com.smartstudy.planning.model.TeamMember;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.planning.repository.TeamMemberRepository;
import com.smartstudy.planning.repository.TeamRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Organization-admin module: an organization account (X-User-Id == organizationId)
 * manages the teams inside it, plus the courses, materials, and events under
 * those teams. Follows the org API contract's camelCase payloads and
 * {"error","message"} error shape.
 */
@Service
@RequiredArgsConstructor
public class OrganizationAdminService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationAdminService.class);

    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final long MAX_MATERIAL_BYTES = 25L * 1024 * 1024;
    private static final int UPCOMING_EVENT_WINDOW_DAYS = 7;
    private static final Pattern PHOTO_FILE_PATTERN =
            Pattern.compile("^[a-f0-9\\-]{36}\\.(png|jpg|jpeg)$");

    public static final String FILES_URL_PREFIX = "/api/v1/organization/files/";
    public static final String MATERIAL_FILE_URL_TEMPLATE = "/api/v1/organization/materials/%s/file";
    private static final String PHOTOS_DIR = "org-photos";

    /** Avatar tints the Members tab renders behind initials. */
    private static final List<String> AVATAR_COLORS = List.of(
            "#7C5CFC", "#F5A623", "#2D9CDB", "#27AE60", "#EB5757", "#BB6BD9");

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CourseRepository courseRepository;
    private final MaterialRepository materialRepository;
    private final EventRepository eventRepository;
    private final TaskRepository taskRepository;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final IdentityServiceClient identityServiceClient;

    // ------------------------------------------------------------------
    // Teams
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrgTeamListResponse getTeams(String orgId) {
        log.info("Org {} fetching teams", orgId);
        List<OrgTeamResponse> teams = teamRepository.findByOrganizationIdOrderByCreatedAtAsc(orgId)
                .stream()
                .map(team -> new OrgTeamResponse(
                        team.getId(),
                        team.getName(),
                        team.getImageUrl(),
                        null,
                        teamMemberRepository.countByTeamIdAndStatus(team.getId(), TeamMemberStatus.ACCEPTED),
                        teamProgress(team.getId()),
                        upcomingEventLabels(team.getId()),
                        null,
                        null))
                .toList();
        return new OrgTeamListResponse(teams, teams.size());
    }

    @Transactional
    public OrgTeamResponse createTeam(String orgId, OrgCreateTeamRequest request) {
        log.info("Org {} creating team: {}", orgId, request.name());
        if (teamRepository.existsByOrganizationIdAndNameIgnoreCase(orgId, request.name().trim())) {
            throw OrgApiException.conflict("A team with this name already exists.");
        }
        Team team = Team.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name().trim())
                .organizationId(orgId)
                .ownerId(orgId)
                .imageUrl(request.photoUrl())
                .inviteCode(generateUniqueInviteCode())
                .build();
        // Team has an assigned id, so save() merges: @PrePersist timestamps land on
        // the returned managed copy, not on the instance built above.
        Team saved = teamRepository.save(team);
        return new OrgTeamResponse(
                saved.getId(),
                saved.getName(),
                saved.getImageUrl(),
                saved.getOwnerId(),
                0L,
                0,
                List.of(),
                saved.getCreatedAt(),
                saved.getUpdatedAt());
    }

    public OrgFileUploadResponse uploadTeamPhoto(String orgId, MultipartFile file) {
        log.info("Org {} uploading team photo: {}", orgId, file.getOriginalFilename());
        return storeImage(file);
    }

    public OrgFileUploadResponse uploadProfilePhoto(String orgId, MultipartFile file) {
        log.info("Org {} uploading profile photo: {}", orgId, file.getOriginalFilename());
        return storeImage(file);
    }

    private OrgFileUploadResponse storeImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw OrgApiException.validation("Uploaded file is empty.");
        }
        String extension = imageExtensionFor(file);
        if (extension == null) {
            throw OrgApiException.unsupportedMediaType("Only PNG and JPG files are supported.");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw OrgApiException.payloadTooLarge("File exceeds the maximum allowed size of 5MB.");
        }
        String fileName = UUID.randomUUID() + "." + extension;
        Path dir = Path.of(storageProperties.baseDir()).resolve(PHOTOS_DIR);
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new OrgApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "InternalServerError", "Failed to store the uploaded photo.");
        }
        return new OrgFileUploadResponse(FILES_URL_PREFIX + fileName);
    }

    public Path resolveTeamPhoto(String fileName) {
        if (!PHOTO_FILE_PATTERN.matcher(fileName).matches()) {
            throw OrgApiException.notFound("File not found.");
        }
        Path path = Path.of(storageProperties.baseDir()).resolve(PHOTOS_DIR).resolve(fileName);
        if (Files.notExists(path)) {
            throw OrgApiException.notFound("File not found.");
        }
        return path;
    }

    // ------------------------------------------------------------------
    // Members
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrgMemberListResponse getMembers(String orgId, String teamId) {
        log.info("Org {} fetching members for team {}", orgId, teamId);
        requireOrgTeam(orgId, teamId);

        List<TeamMember> pending = teamMemberRepository
                .findByTeamIdAndStatus(teamId, TeamMemberStatus.PENDING)
                .stream()
                .sorted(Comparator.comparing(TeamMember::getCreatedAt))
                .toList();
        List<TeamMember> active = teamMemberRepository
                .findByTeamIdAndStatus(teamId, TeamMemberStatus.ACCEPTED)
                .stream()
                .sorted(Comparator.comparing(OrganizationAdminService::joinedAtOf))
                .toList();

        Map<String, UserSummaryData> users = lookupUsers(orgId,
                Stream.concat(pending.stream(), active.stream())
                        .map(TeamMember::getUserId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));

        List<OrgMemberResponse> pendingMembers = pending.stream()
                .map(member -> toMemberResponse(member, users, member.getCreatedAt(), null))
                .toList();
        List<OrgMemberResponse> teamMembers = active.stream()
                .map(member -> toMemberResponse(member, users, null, joinedAtOf(member)))
                .toList();

        return new OrgMemberListResponse(teamId, pendingMembers, teamMembers,
                pendingMembers.size(), teamMembers.size());
    }

    @Transactional
    public OrgMemberActionResponse acceptMember(String orgId, String memberId) {
        log.info("Org {} accepting member {}", orgId, memberId);
        TeamMember member = requirePendingMember(orgId, memberId);
        member.setStatus(TeamMemberStatus.ACCEPTED);
        member.setJoinedAt(Instant.now());
        teamMemberRepository.save(member);
        return new OrgMemberActionResponse(member.getId().toString(), member.getTeamId(),
                contractStatus(TeamMemberStatus.ACCEPTED), member.getJoinedAt());
    }

    @Transactional
    public OrgMemberActionResponse declineMember(String orgId, String memberId) {
        log.info("Org {} declining member {}", orgId, memberId);
        TeamMember member = requirePendingMember(orgId, memberId);
        member.setStatus(TeamMemberStatus.REJECTED);
        teamMemberRepository.save(member);
        return new OrgMemberActionResponse(member.getId().toString(), member.getTeamId(),
                contractStatus(TeamMemberStatus.REJECTED), null);
    }

    /**
     * Loads a membership row addressed by its own id, checks the caller manages
     * the team it belongs to, and rejects anything no longer pending.
     */
    private TeamMember requirePendingMember(String orgId, String memberId) {
        UUID id;
        try {
            id = UUID.fromString(memberId.trim());
        } catch (IllegalArgumentException ex) {
            throw OrgApiException.notFound("Pending member request was not found.");
        }
        TeamMember member = teamMemberRepository.findById(id)
                .orElseThrow(() -> OrgApiException.notFound("Pending member request was not found."));
        Team team = teamRepository.findById(member.getTeamId())
                .orElseThrow(() -> OrgApiException.notFound("Team with the given ID was not found."));
        if (!orgId.equals(team.getOrganizationId())) {
            throw OrgApiException.forbidden(
                    "You do not have permission to manage members for this team.");
        }
        if (member.getStatus() == TeamMemberStatus.ACCEPTED) {
            throw OrgApiException.conflict("This member has already been accepted.");
        }
        if (member.getStatus() != TeamMemberStatus.PENDING) {
            throw OrgApiException.notFound("Pending member request was not found.");
        }
        return member;
    }

    private OrgMemberResponse toMemberResponse(TeamMember member,
                                               Map<String, UserSummaryData> users,
                                               Instant requestedAt,
                                               Instant joinedAt) {
        String name = displayName(member.getUserId(), users.get(member.getUserId()));
        return new OrgMemberResponse(
                member.getId().toString(),
                name,
                initialsOf(name),
                avatarColorOf(member.getUserId()),
                contractStatus(member.getStatus()),
                requestedAt,
                joinedAt);
    }

    private Map<String, UserSummaryData> lookupUsers(String orgId, Set<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserSummaryData> users = identityServiceClient.lookupUsers(orgId, List.copyOf(userIds));
        if (users == null) {
            return Map.of();
        }
        return users.stream()
                .filter(user -> user.id() != null)
                .collect(Collectors.toMap(UserSummaryData::id, user -> user, (first, second) -> first));
    }

    /**
     * Rows accepted before joined_at existed have no timestamp of their own, so
     * the row's last write stands in for the join date.
     */
    private static Instant joinedAtOf(TeamMember member) {
        return member.getJoinedAt() != null ? member.getJoinedAt() : member.getUpdatedAt();
    }

    private static String contractStatus(TeamMemberStatus status) {
        return switch (status) {
            case PENDING -> "pending";
            case ACCEPTED -> "active";
            case REJECTED -> "declined";
        };
    }

    private static String displayName(String userId, UserSummaryData user) {
        if (user != null && user.name() != null && !user.name().isBlank()) {
            return user.name().trim();
        }
        if (user != null && user.email() != null && !user.email().isBlank()) {
            String email = user.email().trim();
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }
        return "Member " + userId.substring(0, Math.min(6, userId.length()));
    }

    private static String initialsOf(String name) {
        StringBuilder initials = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return initials.isEmpty() ? "?" : initials.toString();
    }

    /** Stable per-user avatar tint, so a member keeps the same color everywhere. */
    private static String avatarColorOf(String userId) {
        return AVATAR_COLORS.get(Math.floorMod(userId.hashCode(), AVATAR_COLORS.size()));
    }

    // ------------------------------------------------------------------
    // Join requests (superseded by the member endpoints above; kept for
    // clients still on the previous contract)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrgJoinRequestListResponse getJoinRequests(String orgId, String teamId) {
        log.info("Org {} fetching join requests for team {}", orgId, teamId);
        requireOrgTeam(orgId, teamId);
        List<OrgJoinRequestResponse> requests = teamMemberRepository
                .findByTeamIdAndStatus(teamId, TeamMemberStatus.PENDING)
                .stream()
                .sorted(Comparator.comparing(TeamMember::getCreatedAt))
                .map(member -> new OrgJoinRequestResponse(member.getUserId(), member.getCreatedAt()))
                .toList();
        return new OrgJoinRequestListResponse(teamId, requests, requests.size());
    }

    @Transactional
    public OrgMemberStatusResponse approveMember(String orgId, OrgMemberActionRequest request) {
        log.info("Org {} approving user {} for team {}", orgId, request.userId(), request.teamId());
        return resolveJoinRequest(orgId, request, TeamMemberStatus.ACCEPTED);
    }

    @Transactional
    public OrgMemberStatusResponse rejectMember(String orgId, OrgMemberActionRequest request) {
        log.info("Org {} rejecting user {} for team {}", orgId, request.userId(), request.teamId());
        return resolveJoinRequest(orgId, request, TeamMemberStatus.REJECTED);
    }

    private OrgMemberStatusResponse resolveJoinRequest(String orgId, OrgMemberActionRequest request,
                                                       TeamMemberStatus target) {
        requireOrgTeam(orgId, request.teamId());
        TeamMember member = teamMemberRepository
                .findByTeamIdAndUserId(request.teamId(), request.userId())
                .orElseThrow(() -> OrgApiException.notFound("Join request was not found."));
        if (member.getStatus() == TeamMemberStatus.ACCEPTED) {
            throw OrgApiException.conflict("This user is already a member of the team.");
        }
        if (member.getStatus() != TeamMemberStatus.PENDING) {
            throw OrgApiException.notFound("Join request was not found.");
        }
        member.setStatus(target);
        if (target == TeamMemberStatus.ACCEPTED) {
            member.setJoinedAt(Instant.now());
        }
        teamMemberRepository.save(member);
        return new OrgMemberStatusResponse(request.teamId(), request.userId(), target.name());
    }

    // ------------------------------------------------------------------
    // Courses & materials
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrgCourseListResponse getCourses(String orgId, String teamId) {
        log.info("Org {} fetching courses for team {}", orgId, teamId);
        requireOrgTeam(orgId, teamId);
        List<OrgCourseResponse> courses = courseRepository.findByTeamId(teamId)
                .stream()
                .map(course -> new OrgCourseResponse(
                        course.getId(),
                        null,
                        course.getName(),
                        null,
                        null,
                        null,
                        courseProgress(course.getId()),
                        null,
                        null))
                .toList();
        return new OrgCourseListResponse(teamId, courses, courses.size());
    }

    @Transactional
    public OrgCourseResponse createCourse(String orgId, OrgCreateCourseRequest request) {
        log.info("Org {} creating course '{}' in team {}", orgId, request.name(), request.teamId());
        Team team = requireOrgTeam(orgId, request.teamId());
        if (request.startDate().isAfter(request.endDate())) {
            throw OrgApiException.validation("startDate must be on or before endDate.");
        }
        Course course = Course.builder()
                .userId(orgId)
                .teamId(team.getId())
                .organizationId(orgId)
                .organizationName(team.getOrganizationName())
                .name(request.name().trim())
                .imageUrl(request.thumbnailUrl())
                .startDate(request.startDate().atStartOfDay(ZoneOffset.UTC).toInstant())
                .endDate(request.endDate().atStartOfDay(ZoneOffset.UTC).toInstant())
                .courseType(CourseType.MATERIAL_COURSE)
                .hidden(false)
                .build();
        Course saved = courseRepository.save(course);

        long materialCount = 0;
        if (request.materialIds() != null && !request.materialIds().isEmpty()) {
            materialCount = linkMaterials(orgId, saved.getId(), request.materialIds());
        }

        return new OrgCourseResponse(
                saved.getId(),
                saved.getTeamId(),
                saved.getName(),
                request.startDate(),
                request.endDate(),
                saved.getImageUrl(),
                0,
                materialCount,
                saved.getCreatedAt());
    }

    private long linkMaterials(String orgId, UUID courseId, List<UUID> materialIds) {
        List<Material> materials = materialRepository.findByIdIn(materialIds);
        Set<UUID> foundIds = materials.stream().map(Material::getId).collect(Collectors.toSet());
        for (UUID requested : materialIds) {
            if (!foundIds.contains(requested)) {
                throw OrgApiException.notFound("Material with the given ID was not found.");
            }
        }
        for (Material material : materials) {
            if (!orgId.equals(material.getUserId())) {
                throw OrgApiException.forbidden("You do not have permission to attach this material.");
            }
            material.setCourseId(courseId);
            if (material.getFilePath() != null && !material.getFilePath().isBlank()) {
                material.setFilePath(fileStorageService.moveToCourse(
                        material.getFilePath(), orgId, courseId, material.getId()));
            }
        }
        materialRepository.saveAll(materials);
        return materials.size();
    }

    @Transactional
    public OrgMaterialResponse uploadCourseMaterial(String orgId, UUID courseId, MultipartFile file) {
        log.info("Org {} uploading material {} | courseId: {}", orgId, file.getOriginalFilename(), courseId);
        if (courseId != null) {
            requireOrgCourse(orgId, courseId);
        }
        if (file.isEmpty()) {
            throw OrgApiException.validation("Uploaded file is empty.");
        }
        if (!isPdf(file)) {
            throw OrgApiException.unsupportedMediaType("Only PDF files are supported for course materials.");
        }
        if (file.getSize() > MAX_MATERIAL_BYTES) {
            throw OrgApiException.payloadTooLarge("File exceeds the maximum allowed size of 25MB.");
        }
        String fileName = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                ? "material.pdf" : file.getOriginalFilename();
        if (courseId != null) {
            boolean duplicate = materialRepository.findByCourseId(courseId).stream()
                    .anyMatch(m -> fileName.equalsIgnoreCase(m.getName()));
            if (duplicate) {
                throw OrgApiException.conflict("A material with this filename already exists in this course.");
            }
        }

        // Status READY from the start: org materials are shared content, not input
        // for the per-student AI pipeline (which polls PROCESSING materials).
        Material material = Material.builder()
                .courseId(courseId)
                .userId(orgId)
                .name(fileName)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/pdf")
                .fileSizeBytes(file.getSize())
                .status(MaterialStatus.READY)
                .build();
        Material saved = materialRepository.save(material);

        try {
            String filePath = courseId != null
                    ? fileStorageService.save(orgId, courseId, saved.getId(), file.getInputStream())
                    : fileStorageService.saveUnattached(orgId, saved.getId(), file.getInputStream());
            saved.setFilePath(filePath);
        } catch (IOException ex) {
            throw new OrgApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "InternalServerError", "Failed to store the uploaded material.");
        }
        saved.setPageCount(countPdfPages(fileStorageService.resolve(saved.getFilePath())));
        saved.setProcessedAt(Instant.now());
        materialRepository.save(saved);

        return toMaterialResponse(saved, true);
    }

    @Transactional(readOnly = true)
    public OrgMaterialListResponse getCourseMaterials(String orgId, UUID courseId) {
        log.info("Org {} fetching materials for course {}", orgId, courseId);
        requireOrgCourse(orgId, courseId);
        List<OrgMaterialResponse> materials = materialRepository.findByCourseId(courseId)
                .stream()
                .sorted(Comparator.comparing(Material::getUploadedAt))
                .map(material -> toMaterialResponse(material, false))
                .toList();
        return new OrgMaterialListResponse(courseId, materials, materials.size());
    }

    @Transactional(readOnly = true)
    public Path resolveMaterialFile(UUID materialId) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> OrgApiException.notFound("Material with the given ID was not found."));
        Path path;
        if (material.getFilePath() != null && !material.getFilePath().isBlank()) {
            path = fileStorageService.resolve(material.getFilePath());
        } else if (material.getCourseId() != null) {
            path = fileStorageService.load(material.getUserId(), material.getCourseId(), material.getId());
        } else {
            throw OrgApiException.notFound("Material file was not found on the server.");
        }
        if (Files.notExists(path)) {
            throw OrgApiException.notFound("Material file was not found on the server.");
        }
        return path;
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrgEventListResponse getEvents(String orgId, String teamId) {
        log.info("Org {} fetching events for team {}", orgId, teamId);
        requireOrgTeam(orgId, teamId);
        List<Course> courses = courseRepository.findByTeamId(teamId);
        if (courses.isEmpty()) {
            return new OrgEventListResponse(teamId, List.of(), 0);
        }
        Map<UUID, String> courseNames = courses.stream()
                .collect(Collectors.toMap(Course::getId, Course::getName));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<OrgEventResponse> events = eventRepository
                .findByCourseIdInAndTaskIdIsNull(new ArrayList<>(courseNames.keySet()))
                .stream()
                .map(event -> {
                    LocalDate eventDate = event.getStartDate().atZone(ZoneOffset.UTC).toLocalDate();
                    long daysLeft = ChronoUnit.DAYS.between(today, eventDate);
                    return new OrgEventResponse(
                            event.getId(),
                            null,
                            null,
                            courseNames.get(event.getCourseId()),
                            eventTypeLabel(event.getEventType()),
                            eventDate,
                            daysLeft,
                            null);
                })
                .filter(e -> e.daysLeft() >= 0)
                .sorted(Comparator.comparingLong(OrgEventResponse::daysLeft))
                .toList();
        return new OrgEventListResponse(teamId, events, events.size());
    }

    @Transactional
    public OrgEventResponse createEvent(String orgId, OrgCreateEventRequest request) {
        log.info("Org {} creating {} event for course {} in team {}",
                orgId, request.eventType(), request.courseId(), request.teamId());
        requireOrgTeam(orgId, request.teamId());
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> OrgApiException.notFound("Course with the given ID was not found."));
        if (!request.teamId().equals(course.getTeamId())) {
            throw OrgApiException.unprocessable("The selected course does not belong to this team.");
        }
        EventType eventType = EventType.fromWireValue(request.eventType())
                .orElseThrow(() -> OrgApiException.validation(
                        "eventType must be one of: " + EventType.allowedValues() + "."));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (request.eventDate().isBefore(today)) {
            throw OrgApiException.validation("eventDate must be a valid date in the future.");
        }

        Event event = Event.builder()
                .userId(orgId)
                .title(course.getName() + " " + eventType.label())
                .startDate(request.eventDate().atStartOfDay(ZoneOffset.UTC).toInstant())
                .courseId(course.getId())
                .eventType(eventType.wireValue())
                .build();
        Event saved = eventRepository.save(event);

        return new OrgEventResponse(
                saved.getId(),
                request.teamId(),
                course.getId(),
                course.getName(),
                eventType.label(),
                request.eventDate(),
                ChronoUnit.DAYS.between(today, request.eventDate()),
                saved.getCreatedAt());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Team requireOrgTeam(String orgId, String teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> OrgApiException.notFound("Team with the given ID was not found."));
        if (!orgId.equals(team.getOrganizationId())) {
            throw OrgApiException.forbidden("You do not have permission to modify this team.");
        }
        return team;
    }

    private Course requireOrgCourse(String orgId, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> OrgApiException.notFound("Course with the given ID was not found."));
        if (!orgId.equals(course.getOrganizationId())) {
            throw OrgApiException.forbidden("You do not have permission to modify this course.");
        }
        return course;
    }

    private int teamProgress(String teamId) {
        List<Course> courses = courseRepository.findByTeamId(teamId);
        long total = 0;
        long completed = 0;
        for (Course course : courses) {
            total += taskRepository.countByCourseId(course.getId());
            completed += taskRepository.countByCourseIdAndCompletedTrue(course.getId());
        }
        return total == 0 ? 0 : (int) Math.round((completed * 100.0) / total);
    }

    private int courseProgress(UUID courseId) {
        long total = taskRepository.countByCourseId(courseId);
        if (total == 0) {
            return 0;
        }
        long completed = taskRepository.countByCourseIdAndCompletedTrue(courseId);
        return (int) Math.round((completed * 100.0) / total);
    }

    private List<String> upcomingEventLabels(String teamId) {
        List<UUID> courseIds = courseRepository.findByTeamId(teamId)
                .stream().map(Course::getId).toList();
        if (courseIds.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        Instant limit = now.plus(UPCOMING_EVENT_WINDOW_DAYS, ChronoUnit.DAYS);
        Set<String> labels = eventRepository.findByCourseIdInAndTaskIdIsNull(courseIds)
                .stream()
                .filter(e -> !e.getStartDate().isBefore(now.truncatedTo(ChronoUnit.DAYS))
                        && !e.getStartDate().isAfter(limit))
                .map(e -> eventTypeLabel(e.getEventType()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return List.copyOf(labels);
    }

    private String eventTypeLabel(String wireValue) {
        return EventType.fromWireValue(wireValue)
                .map(EventType::label)
                .orElse(wireValue);
    }

    private OrgMaterialResponse toMaterialResponse(Material material, boolean full) {
        double sizeMb = material.getFileSizeBytes() != null
                ? Math.round(material.getFileSizeBytes() / 100_000.0) / 10.0 : 0.0;
        return new OrgMaterialResponse(
                material.getId(),
                material.getName(),
                full ? fileTypeOf(material) : null,
                material.getPageCount(),
                sizeMb,
                full ? MATERIAL_FILE_URL_TEMPLATE.formatted(material.getId()) : null,
                full ? material.getUploadedAt() : null);
    }

    private String fileTypeOf(Material material) {
        String name = material.getName() != null ? material.getName().toLowerCase() : "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "pdf";
    }

    private boolean isPdf(MultipartFile file) {
        String contentType = file.getContentType();
        String name = file.getOriginalFilename();
        return "application/pdf".equalsIgnoreCase(contentType)
                || (name != null && name.toLowerCase().endsWith(".pdf"));
    }

    private String imageExtensionFor(MultipartFile file) {
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (contentType.equals("image/png") || name.endsWith(".png")) {
            return "png";
        }
        if (contentType.equals("image/jpeg") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "jpg";
        }
        return null;
    }

    private Integer countPdfPages(Path pdfPath) {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            return document.getNumberOfPages();
        } catch (IOException ex) {
            log.warn("Could not read page count from {}: {}", pdfPath, ex.getMessage());
            return null;
        }
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8);
        } while (teamRepository.findByInviteCode(code).isPresent());
        return code;
    }
}
