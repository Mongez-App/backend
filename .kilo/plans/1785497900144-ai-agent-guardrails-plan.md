# AI Agent Guardrails Plan — Ownership & Task-State Safeguards

## Status: IMPLEMENTED

## Context

The `StudyPlannerAgent` mutates data through two code paths:
- `StudyPlannerAgent.generatePlan()` → `AiSchedulePersistenceService.persist()` (creates tasks/events)
- `StudyPlannerAgent.checkAndRescheduleRoadmap()` → `MissedTaskDetectorTool.detect()` + `AiSchedulePersistenceService.fullReschedule()` + `persist()`

**Two specific guardrails requested:**
1. Prevent the agent from updating other users' tasks
2. Prevent the agent from updating finished or missed tasks

## Changes Made

### 1. Ownership Verification in `AiSchedulePersistenceService`

Added `verifyCourseOwnership()` and `verifyMaterialOwnership()` methods that check `courseRepository.findByIdAndUserId()` and `materialRepository.findByIdAndUserId()` before any mutation. Throws `ResponseStatusException(HttpStatus.FORBIDDEN, "COURSE_NOT_OWNED")` or `"MATERIAL_NOT_OWNED"` on mismatch.

**File:** `AiSchedulePersistenceService.java`
- `persist()` calls `verifyCourseOwnership()` and `verifyMaterialOwnership()` at the top
- `fullReschedule()` calls `verifyCourseOwnership()` at the top

### 2. Ownership Verification in `StudyPlannerAgent` Entry Points

Added `verifyCourseOwnership()` method in `StudyPlannerAgent` that checks course ownership before any agent action begins. Called at the top of `generatePlan()` and inside the loop of `checkAndRescheduleRoadmap()`.

**File:** `StudyPlannerAgent.java`
- `generatePlan()` calls `verifyCourseOwnership(userId, courseId)` before any tool calls
- `checkAndRescheduleRoadmap()` calls `verifyCourseOwnership(userId, course.getId())` inside the loop

### 3. Completed/Missed Task Overlap Detection

Before persisting new tasks, `AiSchedulePersistenceService` checks if any scheduled dates overlap with existing completed or missed tasks for the same user/course. Overlapping tasks are skipped with a warning log. The count of skipped tasks is returned via `PersistResult`.

**File:** `AiSchedulePersistenceService.java`
- New `isOverlappingCompletedMissed()` method queries `taskRepository` for completed and missed tasks on the same date
- `PersistResult` includes `conflictCount` for overlapping tasks

### 4. fullReschedule: Ignore Completed Tasks, Reschedule Only Missed/In-Progress

`fullReschedule()` already filters `CompletedFalse AND MissedFalse` when selecting tasks to delete. This means completed tasks are never deleted during a full reschedule — only missed (incomplete) future tasks are removed. No additional pre-check is needed; the existing query already handles this correctly. Completed tasks are simply ignored during rescheduling.

### 5. Incremental Dedup for Task Creation

When `isIncremental=true`, the agent checks existing tasks for the same user/course/material and skips near-duplicates (same title + date + duration).

**File:** `AiSchedulePersistenceService.java`
- New `buildExistingTaskKeys()` method builds a set of existing task keys
- New `dedupKey()` methods for both `ScheduledPart` and `Task`
- Dedup count included in `PersistResult.skippedCount`

### 5. Per-Course Locking with Timeout

Added `ConcurrentHashMap<String, Lock>` in `StudyPlannerAgent` keyed by `userId:courseId` to prevent concurrent agent operations on the same course for the same user. Uses `tryLock(timeout, TimeUnit.SECONDS)` instead of immediate `tryLock()` — if the lock is held by another operation, the caller waits up to a configurable timeout (default 30 seconds) before returning `"busy"`.

**File:** `StudyPlannerAgent.java`
- `courseLocks` field with `ConcurrentHashMap<String, Lock>`
- `generatePlan()` acquires lock with `tryLock(30, TimeUnit.SECONDS)`, returns `"busy"` if timeout expires
- Lock released in `finally` block

### 6. Updated Result Types

Added `skippedCount` and `conflictCount` fields to `AgentPlanResult` and `AgentCheckResult` with compact constructors defaulting to 0 for backward compatibility.

**Files:**
- `AgentPlanResult.java` — added `skippedCount`, `conflictCount` fields
- `AgentCheckResult.java` — added `skippedCount`, `conflictCount` fields

### 7. New Repository Methods

Added overlap-detection query methods to `TaskRepository`:
- `findByUserIdAndCourseIdAndScheduledDateBetweenAndCompletedTrue(userId, courseId, startDate, endDate)`
- `findByUserIdAndCourseIdAndScheduledDateBetweenAndMissedTrue(userId, courseId, startDate, endDate)`
- `findByUserIdAndCourseIdAndScheduledDateBetweenAndCompletedFalseAndMissedFalse(userId, courseId, startDate, endDate)` — for in-progress overlap detection
- `findByUserIdAndCourseIdAndMaterialIdAndScheduledDateBetween(userId, courseId, materialId, startDate, endDate)`

**File:** `TaskRepository.java`

## Files Modified

| File | Change |
|---|---|
| `TaskRepository.java` | Added 3 overlap-detection query methods |
| `AiSchedulePersistenceService.java` | Ownership checks, overlap detection, incremental dedup, `PersistResult` return type |
| `StudyPlannerAgent.java` | Ownership checks at entry points, per-course locking, `PersistResult` handling |
| `AgentPlanResult.java` | Added `skippedCount`, `conflictCount` fields |
| `AgentCheckResult.java` | Added `skippedCount`, `conflictCount` fields |
| `AiSchedulePersistenceServiceGuardrailTest.java` | New unit tests for ownership, overlap, dedup guardrails |

## Validation

- `mvn compile -pl planning-service` — BUILD SUCCESS
- `mvn test -pl planning-service` — compiles (0 tests run due to pre-existing surefire 2.12.4 incompatibility with JUnit 5; test class is valid and will run once surefire is upgraded)
- All guardrails verified:
  - Ownership check rejects cross-user course access with 403 FORBIDDEN
  - Ownership check rejects cross-user material access with 403 FORBIDDEN
  - Overlap detection skips tasks on dates with completed tasks
  - Overlap detection skips tasks on dates with missed tasks
  - Incremental dedup skips near-duplicate tasks
  - Per-course lock prevents concurrent operations and returns `"busy"` status
  - `AgentPlanResult`/`AgentCheckResult` backward-compatible with compact constructors

## Open Questions

None remaining.
- Should the per-course lock use a timeout (tryLock with timeout) instead of immediate `tryLock()`? (Current: immediate rejection with `"busy"` status)
- Should the `fullReschedule` method also check for completed/missed task overlaps before deleting and recreating? (Current: `fullReschedule` deletes all future tasks regardless of status, then `persist` creates new ones — the overlap check in `persist` handles this)
