package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.request.OrgCreateCourseRequest;
import com.smartstudy.planning.dto.request.OrgCreateEventRequest;
import com.smartstudy.planning.dto.request.OrgCreateTeamRequest;
import com.smartstudy.planning.dto.request.OrgMemberActionRequest;
import com.smartstudy.planning.dto.response.OrgJoinRequestListResponse;
import com.smartstudy.planning.dto.response.OrgMemberStatusResponse;
import com.smartstudy.planning.dto.response.OrgCourseListResponse;
import com.smartstudy.planning.dto.response.OrgCourseResponse;
import com.smartstudy.planning.dto.response.OrgEventListResponse;
import com.smartstudy.planning.dto.response.OrgEventResponse;
import com.smartstudy.planning.dto.response.OrgFileUploadResponse;
import com.smartstudy.planning.dto.response.OrgMaterialListResponse;
import com.smartstudy.planning.dto.response.OrgMaterialResponse;
import com.smartstudy.planning.dto.response.OrgTeamListResponse;
import com.smartstudy.planning.dto.response.OrgTeamResponse;
import com.smartstudy.planning.exception.OrgApiException;
import com.smartstudy.planning.service.OrganizationAdminService;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Organization-admin API per the org API contract. The gateway verifies the
 * Firebase token and forwards the uid as X-User-Id; for organization accounts
 * that uid IS the organizationId (see identity-service OrganizationAuthService).
 */
@RestController
@RequestMapping("/organization")
@RequiredArgsConstructor
public class OrganizationAdminController {

    private static final Logger log = LoggerFactory.getLogger(OrganizationAdminController.class);
    private final OrganizationAdminService organizationAdminService;

    @GetMapping("/getTeams")
    public OrgTeamListResponse getTeams(
            @RequestHeader(value = "X-User-Id", required = false) String orgId) {
        log.info("Incoming request: GET /organization/getTeams | orgId: {}", orgId);
        return organizationAdminService.getTeams(requireOrg(orgId));
    }

    @PostMapping("/createTeam")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgTeamResponse createTeam(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @Valid @RequestBody OrgCreateTeamRequest request) {
        log.info("Incoming request: POST /organization/createTeam | orgId: {}", orgId);
        return organizationAdminService.createTeam(requireOrg(orgId), request);
    }

    @PostMapping("/uploadTeamPhoto")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgFileUploadResponse uploadTeamPhoto(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @RequestPart("file") MultipartFile file) {
        log.info("Incoming request: POST /organization/uploadTeamPhoto | orgId: {}", orgId);
        return organizationAdminService.uploadTeamPhoto(requireOrg(orgId), file);
    }

    @GetMapping("/files/{fileName}")
    public ResponseEntity<Resource> getTeamPhoto(@PathVariable String fileName) {
        Path path = organizationAdminService.resolveTeamPhoto(fileName);
        MediaType mediaType = fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }

    @GetMapping("/getJoinRequests")
    public OrgJoinRequestListResponse getJoinRequests(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @RequestParam String teamId) {
        log.info("Incoming request: GET /organization/getJoinRequests | orgId: {} | teamId: {}", orgId, teamId);
        return organizationAdminService.getJoinRequests(requireOrg(orgId), teamId);
    }

    @PostMapping("/approveMember")
    public OrgMemberStatusResponse approveMember(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @Valid @RequestBody OrgMemberActionRequest request) {
        log.info("Incoming request: POST /organization/approveMember | orgId: {}", orgId);
        return organizationAdminService.approveMember(requireOrg(orgId), request);
    }

    @PostMapping("/rejectMember")
    public OrgMemberStatusResponse rejectMember(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @Valid @RequestBody OrgMemberActionRequest request) {
        log.info("Incoming request: POST /organization/rejectMember | orgId: {}", orgId);
        return organizationAdminService.rejectMember(requireOrg(orgId), request);
    }

    @GetMapping("/getCourses")
    public OrgCourseListResponse getCourses(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @RequestParam String teamId) {
        log.info("Incoming request: GET /organization/getCourses | orgId: {} | teamId: {}", orgId, teamId);
        return organizationAdminService.getCourses(requireOrg(orgId), teamId);
    }

    @PostMapping("/createCourse")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgCourseResponse createCourse(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @Valid @RequestBody OrgCreateCourseRequest request) {
        log.info("Incoming request: POST /organization/createCourse | orgId: {}", orgId);
        return organizationAdminService.createCourse(requireOrg(orgId), request);
    }

    @PostMapping("/uploadCourseMaterial")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgMaterialResponse uploadCourseMaterial(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @RequestPart("file") MultipartFile file,
            @RequestParam("courseId") UUID courseId) {
        log.info("Incoming request: POST /organization/uploadCourseMaterial | orgId: {} | courseId: {}", orgId, courseId);
        return organizationAdminService.uploadCourseMaterial(requireOrg(orgId), courseId, file);
    }

    @GetMapping("/getCourseMaterials")
    public OrgMaterialListResponse getCourseMaterials(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @RequestParam UUID courseId) {
        log.info("Incoming request: GET /organization/getCourseMaterials | orgId: {} | courseId: {}", orgId, courseId);
        return organizationAdminService.getCourseMaterials(requireOrg(orgId), courseId);
    }

    @GetMapping("/materials/{materialId}/file")
    public ResponseEntity<Resource> getMaterialFile(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @PathVariable UUID materialId) {
        requireOrg(orgId);
        Path path = organizationAdminService.resolveMaterialFile(materialId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(path));
    }

    @GetMapping("/getEvents")
    public OrgEventListResponse getEvents(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @RequestParam String teamId) {
        log.info("Incoming request: GET /organization/getEvents | orgId: {} | teamId: {}", orgId, teamId);
        return organizationAdminService.getEvents(requireOrg(orgId), teamId);
    }

    @PostMapping("/createEvent")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgEventResponse createEvent(
            @RequestHeader(value = "X-User-Id", required = false) String orgId,
            @Valid @RequestBody OrgCreateEventRequest request) {
        log.info("Incoming request: POST /organization/createEvent | orgId: {}", orgId);
        return organizationAdminService.createEvent(requireOrg(orgId), request);
    }

    private String requireOrg(String orgId) {
        if (orgId == null || orgId.isBlank()) {
            throw OrgApiException.unauthorized("Firebase ID token is missing or invalid.");
        }
        return orgId;
    }
}
