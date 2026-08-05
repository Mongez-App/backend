# Team Endpoints Implementation Plan

## Overview
Implement 8 new endpoints for team management and team-scoped course access. Teams are a new domain concept that doesn't exist in the current codebase.

## Affected Services
| Service | Responsibility |
|---------|----------------|
| **identity-service** | Team, Organization, TeamMembership, JoinRequest entities; `GET /teams`, `GET /teams/discover`, `GET /teams/search`, `POST /teams/join` |
| **planning-service** | Add `teamId` to Course; `GET /teams/{teamId}/courses`, `GET /courses/{courseId}/tasks` (modify response), `GET /courses/{courseId}/materials` (add path), `GET /courses/{courseId}/events` (new) |
| **api-gateway** | Add route for `/api/v1/teams/**` → identity-service |

---

## 1. Database Schema Changes

### identity_db (identity-service)
```sql
-- Organizations (optional, can be embedded in Team)
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    image_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Teams
CREATE TABLE teams (
    id VARCHAR(64) PRIMARY KEY,           -- string IDs like "team-001-mn"
    organization_id UUID REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    image_url TEXT,
    description TEXT,
    is_public BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Team Membership
CREATE TABLE team_memberships (
    team_id VARCHAR(64) REFERENCES teams(id) ON DELETE CASCADE,
    user_id VARCHAR(128) NOT NULL,        -- Firebase UID
    role VARCHAR(20) NOT NULL DEFAULT 'member', -- 'admin', 'member'
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (team_id, user_id)
);

-- Join Requests (pending approval)
CREATE TABLE join_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id VARCHAR(64) REFERENCES teams(id) ON DELETE CASCADE,
    user_id VARCHAR(128) NOT NULL,
    invite_code VARCHAR(64),              -- nullable, used if joined via code
    status VARCHAR(20) NOT NULL DEFAULT 'pending', -- 'pending', 'approved', 'rejected'
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by VARCHAR(128)
);
```

### planning_db (planning-service)
```sql
-- Add team_id to courses table
ALTER TABLE courses ADD COLUMN team_id VARCHAR(64);
CREATE INDEX idx_courses_team_id ON courses(team_id);

-- Add download_url to materials table (for "path" field in response)
ALTER TABLE materials ADD COLUMN download_url TEXT;
```

---

## 2. New Entities (identity-service)

| Entity | Package | Key Fields |
|--------|---------|------------|
| `Organization` | `com.smartstudy.identity.model` | id (UUID), name, description, imageUrl, createdAt, updatedAt |
| `Team` | `com.smartstudy.identity.model` | id (String), organizationId (UUID), name, imageUrl, description, isPublic, createdAt, updatedAt |
| `TeamMembership` | `com.smartstudy.identity.model` | teamId (String), userId (String), role (enum), joinedAt |
| `JoinRequest` | `com.smartstudy.identity.model` | id (UUID), teamId (String), userId (String), inviteCode (String), status (enum), appliedAt, reviewedAt, reviewedBy |

### Enums
- `TeamRole` — ADMIN, MEMBER
- `JoinRequestStatus` — PENDING, APPROVED, REJECTED

---

## 3. New Repositories (identity-service)

| Repository | Key Methods |
|------------|-------------|
| `OrganizationRepository` | `findById`, `save` |
| `TeamRepository` | `findById`, `findByUserId(userId)`, `findPublicTeams(pageable)`, `searchByNameOrOrg(query, pageable)` |
| `TeamMembershipRepository` | `findByUserId(userId)`, `findByTeamId(teamId)`, `existsByTeamIdAndUserId(teamId, userId)`, `save`, `deleteByTeamIdAndUserId` |
| `JoinRequestRepository` | `findByUserId(userId)`, `findByTeamIdAndStatus(teamId, status)`, `findById(id)`, `save`, `existsByTeamIdAndUserIdAndStatus(teamId, userId, PENDING)` |

---

## 4. New Services (identity-service)

### `TeamService` (interface + impl)
```java
// Core operations
List<TeamResponse> getUserTeams(String userId);                    // GET /teams
DiscoverResponse getDiscoverData(String userId);                   // GET /teams/discover
List<TeamSearchResult> searchTeams(String userId, String query);  // GET /teams/search
JoinTeamResult joinTeam(String userId, String inviteCode);        // POST /teams/join

// Internal (for Feign)
Team getTeamById(String teamId);
boolean isUserMember(String teamId, String userId);
```

### `TeamServiceClient` (Feign client in identity-service → planning-service)
```java
@FeignClient(name = "planning-service", fallbackFactory = ...)
interface TeamServiceClient {
    @GetMapping("/internal/teams/{teamId}/courses")
    List<CourseTeamResponse> getTeamCourses(@PathVariable String teamId, @RequestHeader("X-User-Id") String userId);

    @GetMapping("/internal/teams/{teamId}/stats")
    TeamStatsResponse getTeamStats(@PathVariable String teamId, @RequestHeader("X-User-Id") String userId);
}
```

---

## 5. New Services (planning-service)

### Add to `Course` entity
```java
@Column(name = "team_id")
private String teamId;  // nullable, for team-scoped courses
```

### Add to `CourseRepository`
```java
List<Course> findByTeamIdAndUserId(String teamId, String userId);
List<Course> findByTeamIdAndUserId(String teamId, String userId);
```

### `TeamCourseService` (new service, called by Feign)
```java
List<CourseTeamResponse> getTeamCourses(String teamId, String userId);  // filters by teamId + userId
TeamStatsResponse getTeamStats(String teamId, String userId);           // completion %, upcoming events
```

### Modify `TaskService.getTasksByCourse`
Change response from `List<TaskResponse>` to wrapper:
```java
public record CourseTasksResponse(
    Meta meta,
    List<TaskResponse> data
) {}

public record Meta(
    int preferredStudyTimeMinutes,
    int totalTasks
) {}
```
Need to fetch user's preferred study minutes from identity-service via new Feign client or header.

### Modify `CourseService.getMaterials`
Add `downloadUrl` to `MaterialResponse` (or create new `CourseMaterialResponse`):
```java
public record CourseMaterialResponse(
    UUID materialId,
    String name,
    double fileSizeMb,
    String path,         // download URL
    String status,
    Instant uploadedAt
) {}
```

### New `EventService.getCourseEvents`
```java
List<CourseEventResponse> getCourseEvents(String userId, UUID courseId);
```
Response format per spec:
```java
public record CourseEventResponse(
    String eventId,
    String eventTitle,
    String eventType,
    Instant eventDate
) {}
```

---

## 6. New DTOs (identity-service)

### Request
```java
// POST /teams/join
public record JoinTeamRequest(
    @JsonProperty("inviteCode") String inviteCode
) {}

// Alternative: join by teamId directly (if no invite code)
public record JoinTeamByIdRequest(
    @JsonProperty("teamId") String teamId
) {}
```

### Response
```java
// GET /teams
public record TeamResponse(
    @JsonProperty("team_id") String teamId,
    @JsonProperty("name") String name,
    @JsonProperty("organization_name") String organizationName,
    @JsonProperty("image_url") String imageUrl,
    @JsonProperty("completion_percentage") double completionPercentage,
    @JsonProperty("events") List<TeamEventSummary> events
) {}

public record TeamEventSummary(
    @JsonProperty("event_type") String eventType
) {}

// GET /teams/discover
public record DiscoverResponse(
    @JsonProperty("pending_requests") List<PendingRequestResponse> pendingRequests,
    @JsonProperty("trending_teams") List<TrendingTeamResponse> trendingTeams
) {}

public record PendingRequestResponse(
    @JsonProperty("team_id") String teamId,
    @JsonProperty("name") String name,
    @JsonProperty("image_url") String imageUrl,
    @JsonProperty("organization_name") String organizationName,
    @JsonProperty("applied_at") Instant appliedAt,
    @JsonProperty("status") String status
) {}

public record TrendingTeamResponse(
    @JsonProperty("team_id") String teamId,
    @JsonProperty("name") String name,
    @JsonProperty("image_url") String imageUrl,
    @JsonProperty("organization_name") String organizationName,
    @JsonProperty("member_count") int memberCount,
    @JsonProperty("description") String description
) {}

// GET /teams/search
public record TeamSearchResponse(
    @JsonProperty("success") boolean success,
    @JsonProperty("data") List<TeamSearchItem> data
) {}

public record TeamSearchItem(
    @JsonProperty("team_id") String teamId,
    @JsonProperty("name") String name,
    @JsonProperty("organization_name") String organizationName,
    @JsonProperty("status") String status  // "joined" | "not_joined" | "pending"
) {}

// POST /teams/join
public record JoinTeamResponse(
    @JsonProperty("success") boolean success,
    @JsonProperty("data") JoinTeamData data,
    @JsonProperty("message") String message
) {}

public record JoinTeamData(
    @JsonProperty("orgId") String orgId,
    @JsonProperty("orgName") String orgName,
    @JsonProperty("teamId") String teamId,
    @JsonProperty("status") String status
) {}

// Internal Feign responses
public record CourseTeamResponse(
    @JsonProperty("course_id") UUID courseId,
    @JsonProperty("team_id") String teamId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("name") String name,
    @JsonProperty("course_code") String courseCode,
    @JsonProperty("start_date") Instant startDate,
    @JsonProperty("exam_date") Instant examDate,
    @JsonProperty("course_image_url") String courseImageUrl,
    @JsonProperty("completion_percentage") double completionPercentage
) {}

public record TeamStatsResponse(
    double completionPercentage,
    List<TeamEventSummary> events
) {}
```

---

## 7. New DTOs (planning-service)

### Response
```java
// GET /courses/{courseId}/tasks - NEW WRAPPER
public record CourseTasksResponse(
    Meta meta,
    List<TaskResponse> data
) {}

public record Meta(
    @JsonProperty("preferred_study_time_minutes") int preferredStudyTimeMinutes,
    @JsonProperty("total_tasks") int totalTasks
) {}

// GET /courses/{courseId}/materials - UPDATED with path
public record CourseMaterialResponse(
    @JsonProperty("material_id") UUID materialId,
    @JsonProperty("name") String name,
    @JsonProperty("file_size_mb") double fileSizeMb,
    @JsonProperty("path") String path,
    @JsonProperty("status") String status,
    @JsonProperty("uploaded_at") Instant uploadedAt
) {}

// GET /courses/{courseId}/events - NEW
public record CourseEventResponse(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("event_title") String eventTitle,
    @JsonProperty("event_type") String eventType,
    @JsonProperty("event_date") Instant eventDate
) {}
```

---

## 8. New Controllers

### identity-service: `TeamController`
```java
@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
public class TeamController {
    private final TeamService teamService;

    @GetMapping
    public List<TeamResponse> getTeams(@RequestHeader("X-User-Id") String userId) { ... }

    @GetMapping("/{teamId}/courses")
    public List<CourseTeamResponse> getTeamCourses(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String teamId) { ... }  // calls TeamServiceClient (Feign)

    @GetMapping("/discover")
    public DiscoverResponse getDiscover(@RequestHeader("X-User-Id") String userId) { ... }

    @GetMapping("/search")
    public TeamSearchResponse searchTeams(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String q) { ... }

    @PostMapping("/join")
    public ResponseEntity<JoinTeamResponse> joinTeam(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody JoinTeamRequest request) { ... }
}
```

### planning-service: `TeamCourseController` (new) or extend existing
```java
// Internal endpoints (not exposed via gateway - for Feign only)
@RestController
@RequestMapping("/internal/teams")
@RequiredArgsConstructor
public class InternalTeamCourseController {
    private final TeamCourseService teamCourseService;

    @GetMapping("/{teamId}/courses")
    public List<CourseTeamResponse> getTeamCourses(
            @PathVariable String teamId,
            @RequestHeader("X-User-Id") String userId) { ... }

    @GetMapping("/{teamId}/stats")
    public TeamStatsResponse getTeamStats(
            @PathVariable String teamId,
            @RequestHeader("X-User-Id") String userId) { ... }
}
```

### planning-service: Modify existing
- `CourseController.getCourseTasks` → change return type to `CourseTasksResponse`, add meta
- `CourseController.getMaterials` → change return type to `CourseMaterialResponse` with path
- Add new `CourseController.getCourseEvents` → `GET /courses/{courseId}/events`

---

## 9. Gateway Configuration (api-gateway)

Add to `application.yml`:
```yaml
- id: identity-service-teams
  uri: lb://IDENTITY-SERVICE
  predicates:
    - Path=/api/v1/teams/**
  filters:
    - StripPrefix=2
    - name: CircuitBreaker
      args:
        name: identityCircuitBreaker
        fallbackUri: forward:/fallback/identity
```

**Order matters**: Place this BEFORE the general `identity-service` route if needed, or ensure it's more specific. Since `/api/v1/teams/**` is disjoint from `/api/v1/auth/**` and `/api/v1/users/**`, order doesn't strictly matter.

---

## 10. Cross-Service Feign Clients

### identity-service → planning-service (new)
```java
@FeignClient(name = "planning-service", fallbackFactory = TeamServiceClientFallbackFactory.class)
public interface TeamServiceClient {
    @GetMapping("/internal/teams/{teamId}/courses")
    List<CourseTeamResponse> getTeamCourses(
            @PathVariable("teamId") String teamId,
            @RequestHeader("X-User-Id") String userId);

    @GetMapping("/internal/teams/{teamId}/stats")
    TeamStatsResponse getTeamStats(
            @PathVariable("teamId") String teamId,
            @RequestHeader("X-User-Id") String userId);
}
```

### planning-service → identity-service (new, for user preferences)
```java
@FeignClient(name = "identity-service", fallbackFactory = IdentityServiceClientFallbackFactory.class)
public interface IdentityServiceClient {
    @GetMapping("/internal/users/{userId}/preferences")
    UserPreferencesResponse getPreferences(
            @PathVariable("userId") String userId,
            @RequestHeader("X-User-Id") String callerUserId);  // or service-to-service auth
}
```

**Alternative**: Pass `X-Daily-Study-Minutes` header from gateway (already exists) to avoid new Feign call.

---

## 11. Implementation Order

### Phase 1: Database & Core Entities (identity-service)
1. Create SQL migration scripts for identity_db tables (organizations, teams, team_memberships, join_requests)
2. Create JPA entities: Organization, Team, TeamMembership, JoinRequest, enums
3. Create repositories
4. Add Flyway migration (or rely on ddl-auto=update)

### Phase 2: Database & Course Changes (planning-service)
1. Add `teamId` column to courses table (SQL migration)
2. Add `downloadUrl` column to materials table
3. Update `Course` entity with `teamId`
4. Update `Material` entity with `downloadUrl`
5. Add repository methods

### Phase 3: Identity-Service Team Logic
1. Implement `TeamService` + `TeamServiceImpl`
2. Create DTOs for all team endpoints
3. Create `TeamController`
4. Create `TeamServiceClient` (Feign to planning-service)
5. Create fallback factory

### Phase 4: Planning-Service Team Course Logic
1. Create `TeamCourseService` + `TeamCourseServiceImpl`
2. Create DTOs: `CourseTasksResponse`, `CourseMaterialResponse`, `CourseEventResponse`, `CourseTeamResponse`, `TeamStatsResponse`
3. Create `InternalTeamCourseController` (Feign-facing)
4. Modify `TaskService.getTasksByCourse` to return `CourseTasksResponse` with meta
   - Need user's preferred study minutes → call IdentityServiceClient or use header
5. Modify `CourseService.getMaterials` to return `CourseMaterialResponse` with downloadUrl
6. Create `EventService.getCourseEvents` + `CourseController.getCourseEvents`

### Phase 5: Gateway & Integration
1. Add gateway route for `/api/v1/teams/**`
2. Test end-to-end flow
3. Verify `X-User-Id` header injection works for new routes

---

## 12. Key Design Decisions & Tradeoffs

| Decision | Rationale |
|----------|-----------|
| Teams in identity-service | User-centric domain; identity-service already manages users and memberships |
| `teamId` as String | Matches spec format (`team-001-mn`, `team_beta_ai_001`) and User's String ID pattern |
| Feign from identity → planning for team stats | Follows existing pattern (identity-service calls planning-service for user stats) |
| Internal endpoints in planning-service | `/internal/teams/{teamId}/...` not exposed via gateway; only for Feign |
| Modify existing `GET /courses/{courseId}/tasks` | Spec defines same path; breaking change but aligns with contract |
| Add `downloadUrl` to Material | Required for `path` field in materials response; store signed URL or relative path |
| Preferred study minutes via header | Reuse existing `X-Daily-Study-Minutes` header pattern instead of new Feign call |

---

## 13. Open Questions (Need User Decision)

1. **Team ID generation**: Who creates team IDs? Auto-generate UUIDs? Allow custom string IDs like "team-001-mn"? Or use slugified names?
   
2. **Invite code format**: Spec shows "hYDESxpX" (8 chars alphanumeric). Generate randomly? Allow admin-defined codes? Link to team or to membership?

3. **Team approval flow**: Join requests default to "pending". Who approves? Team admin? Organization admin? Auto-approve for public teams?

4. **Course team assignment**: How does a course get a `teamId`? Manual admin action? When a team member creates a course? Need an endpoint to assign courses to teams.

5. **Preferred study minutes source**: 
   - Option A: Call identity-service Feign for `UserPreference.dailyStudyHours` (convert to minutes)
   - Option B: Pass via `X-Daily-Study-Minutes` header (already used in CourseController)
   - Option C: Default to 60 minutes if not available
   
6. **Material download URL**: 
   - Store as full URL? 
   - Generate signed S3/CloudStorage URL on-the-fly?
   - Return relative path for frontend to construct URL?

7. **Trending teams algorithm**: How to compute "trending"? Member growth rate? Recent activity? Simple: `member_count` descending with minimum threshold?

8. **Organization model**: Is Organization a separate entity or just a field on Team? Spec shows `organization_name` on Team. If separate, need org CRUD endpoints.

---

## 14. Validation & Testing

### Unit Tests (per service)
- TeamService: membership logic, join request handling
- TeamCourseService: filtering by teamId + userId
- Controllers: request/response mapping, error cases

### Integration Tests
- Gateway routing for `/api/v1/teams/**`
- Feign client fallback behavior
- Cross-service data consistency (team membership ↔ course teamId)

### Manual Verification
1. Create team via admin → verify in DB
2. Join team with invite code → pending request created
3. Approve request → membership created
4. Assign course to team (need admin endpoint)
5. GET /teams → returns team with completion % and events
6. GET /teams/{teamId}/courses → returns team courses
7. GET /courses/{courseId}/tasks → returns meta + data
8. GET /courses/{courseId}/materials → returns path field
9. GET /courses/{courseId}/events → returns course events
10. GET /teams/discover → pending + trending
11. GET /teams/search?q=mobile → filtered results

---

## 15. Files to Create/Modify

### identity-service
- `src/main/resources/db/migration/V1__teams.sql` (new)
- `src/main/java/.../model/Organization.java` (new)
- `src/main/java/.../model/Team.java` (new)
- `src/main/java/.../model/TeamMembership.java` (new)
- `src/main/java/.../model/JoinRequest.java` (new)
- `src/main/java/.../model/TeamRole.java` (new enum)
- `src/main/java/.../model/JoinRequestStatus.java` (new enum)
- `src/main/java/.../repository/OrganizationRepository.java` (new)
- `src/main/java/.../repository/TeamRepository.java` (new)
- `src/main/java/.../repository/TeamMembershipRepository.java` (new)
- `src/main/java/.../repository/JoinRequestRepository.java` (new)
- `src/main/java/.../service/TeamService.java` (new interface)
- `src/main/java/.../service/impl/TeamServiceImpl.java` (new)
- `src/main/java/.../client/TeamServiceClient.java` (new Feign)
- `src/main/java/.../client/TeamServiceClientFallbackFactory.java` (new)
- `src/main/java/.../dto/request/JoinTeamRequest.java` (new)
- `src/main/java/.../dto/response/TeamResponse.java` (new)
- `src/main/java/.../dto/response/DiscoverResponse.java` (new)
- `src/main/java/.../dto/response/TeamSearchResponse.java` (new)
- `src/main/java/.../dto/response/JoinTeamResponse.java` (new)
- `src/main/java/.../dto/response/CourseTeamResponse.java` (new - internal)
- `src/main/java/.../dto/response/TeamStatsResponse.java` (new - internal)
- `src/main/java/.../controller/TeamController.java` (new)

### planning-service
- `src/main/resources/db/migration/V8__add_team_id_to_courses.sql` (new)
- `src/main/resources/db/migration/V9__add_download_url_to_materials.sql` (new)
- Update `Course.java` - add `teamId` field
- Update `Material.java` - add `downloadUrl` field
- Update `CourseRepository.java` - add `findByTeamIdAndUserId`
- Update `MaterialRepository.java` - add downloadUrl handling
- `src/main/java/.../service/TeamCourseService.java` (new interface)
- `src/main/java/.../service/TeamCourseServiceImpl.java` (new)
- `src/main/java/.../controller/InternalTeamCourseController.java` (new)
- `src/main/java/.../dto/response/CourseTasksResponse.java` (new)
- `src/main/java/.../dto/response/CourseMaterialResponse.java` (new)
- `src/main/java/.../dto/response/CourseEventResponse.java` (new)
- `src/main/java/.../dto/response/CourseTeamResponse.java` (new - internal)
- `src/main/java/.../dto/response/TeamStatsResponse.java` (new - internal)
- `src/main/java/.../client/IdentityServiceClient.java` (new Feign - optional)
- Modify `TaskService.java` - change `getTasksByCourse` return type
- Modify `CourseService.java` - change `getMaterials` return type, add `getCourseEvents`
- Modify `CourseController.java` - update endpoints, add `getCourseEvents`
- Modify `EventService.java` - add `getCourseEvents`

### api-gateway
- Update `src/main/resources/application.yml` - add teams route

---

## 16. Migration Path for Existing Endpoints

| Existing Endpoint | Change | Migration |
|-------------------|--------|-----------|
| `GET /courses/{courseId}/tasks` | Return `CourseTasksResponse` instead of `List<TaskResponse>` | Breaking - coordinate with frontend |
| `GET /courses/{courseId}/materials` | Return `CourseMaterialResponse` with `path` | Breaking - coordinate with frontend |
| `GET /courses/{courseId}/events` | New endpoint | Non-breaking |

---

## 17. Build Commands

```bash
# Compile all modules
mvn clean compile

# Run tests
mvn test

# Package single module
mvn clean package -pl :identity-service -am -DskipTests
mvn clean package -pl :planning-service -am -DskipTests
```

---

## Next Steps
1. Resolve open questions (Section 13)
2. Confirm team ID generation strategy
3. Confirm preferred study minutes source
4. Begin Phase 1 implementation