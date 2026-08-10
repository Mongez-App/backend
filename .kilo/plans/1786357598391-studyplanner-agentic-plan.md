# StudyPlanner Agentic Agent Plan

## Current State

`StudyPlannerAgent` is a Spring `@Service` that orchestrates 4 `@Tool`-annotated helper services in a **hardcoded pipeline**:

```
PdfExtractorTool.extract → LLM task extraction → assignPriorities(Java) → CalendarQuerierTool.query → SchedulerEngineTool.schedule → AiSchedulePersistenceService.persist
```

Tools are declared with `@Tool` but are invoked **directly by Java code**, not by the LLM. The only LLM call is the one-shot task extraction from PDF text.

Spring AI 1.1.8 `ChatClient` is available and supports multi-turn tool calling, but raw PDF text (potentially 50KB+) would pollute the context window if exposed to every round.

---

## Goal

Transform `StudyPlannerAgent` into an **autonomous agentic loop** where the LLM:
1. Reviews the current state
2. Decides which tool to call next
3. Receives a concise summary of the result
4. Repeats until the plan is complete

---

## Design Decisions

### 1. Agentic Loop Mechanism: Manual loop (recommended)

Use a **Java-controlled agentic loop** rather than Spring AI's automatic `.tools()` multi-turn calling.

**Why:** Spring AI's auto tool-calling feeds full tool results back into the conversation history. For tools like `extract_study_tasks` that return raw PDF text, this would cause severe token bloat. A manual loop lets us:
- Control exactly what summary is fed back to the LLM
- Maintain structured state across rounds
- Enforce max-iteration limits
- Handle malformed decisions gracefully

**How it works:**
- Each round, build a prompt from `AgentState`
- Ask the LLM to return a structured `AgentDecision` record
- Execute the requested tool in Java
- Update `AgentState` with a concise summary
- Repeat until `action="finish"` or max iterations reached

### 2. Tool Set

Keep existing tools but **rename/add** for agentic clarity:

| Tool Name | Description | Current Equivalent |
|-----------|-------------|-------------------|
| `extract_and_parse_tasks` | Extract PDF text + LLM-parse into structured task list. Returns compact summary. | `PdfExtractorTool.extract` + `extractTasksFromText` |
| `query_available_slots` | Query study slots. Unchanged. | `CalendarQuerierTool.query` |
| `assign_priorities` | Assign HIGH/MEDIUM/LOW based on position and days-to-exam. (Deterministic, Java logic) | `StudyPlannerAgent.assignPriorities` |
| `schedule_tasks` | Bin-pack tasks into slots. Returns compact summary. | `SchedulerEngineTool.schedule` |
| `persist_schedule` | Write tasks and events to DB, update material status. Unchanged. | `AiSchedulePersistenceService.persist` |
| `detect_missed_tasks` | Detect overdue tasks. Unchanged. | `MissedTaskDetectorTool.detect` |
| `full_reschedule` | Delete future tasks/events for a course. | `AiSchedulePersistenceService.fullReschedule` |

**Rationale:** Combining PDF extraction + parsing into one tool ensures the LLM never sees raw PDF text. Priority assignment stays deterministic for consistency. Persistence is its own step so the LLM can review the schedule before committing.

### 3. State Management: `AgentState` record

Introduce `AgentState` to track progress across rounds:

```java
record AgentState(
    String userId, UUID courseId, UUID materialId,
    int dailyStudyMinutes, String preferredDays,
    boolean isIncremental,
    List<ExtractedTask> tasks,           // populated after extraction
    LocalDate examDate,                  // computed early
    List<AvailableSlot> slots,           // populated after query
    ScheduleResult scheduleResult,       // populated after scheduling
    List<String> executionLog,           // concise summaries for LLM
    int iteration,
    String error                          // if any
) {}
```

**Key invariant:** The LLM only sees `executionLog` (human-readable summaries), never raw PDF text or huge JSON blobs. Large data objects (`tasks`, `slots`, `scheduleResult`) stay in Java memory and are passed directly to tools when invoked.

### 4. Structured Output: `AgentDecision`

The LLM returns a structured decision each round:

```java
record AgentDecision(
    String action,       // "call_tool" | "finish"
    String tool,         // tool name
    Map<String, String> arguments  // key-value args (may be empty; Java fills from state)
) {}
```

**How to implement:** Use Spring AI's `.entity(AgentDecision.class)` with a system prompt that specifies the exact JSON schema. Set `temperature: 0.1` for deterministic decisions.

### 5. System Prompt Design

The system prompt must:
- List available tools with signatures and descriptions
- Describe the current state (execution log + compact metadata)
- Specify the output format (AgentDecision JSON)
- Set clear termination criteria: finish when schedule is persisted, or when `overCapacity` is detected

Example prompt per round:
```
You are a study planner agent. Generate a study schedule for the user.

Current state:
- Course: CS101, exam date: 2026-09-15
- Tasks extracted: 15 tasks (45-90 min each)
- Available slots: 8 slots, 480 min total
- Schedule result: 15 tasks scheduled, 0 unscheduled
- Iteration: 4/10

Available tools:
1. extract_and_parse_tasks(materialId) - Extract and parse tasks from PDF.
2. query_available_slots(userId, courseId, dailyStudyMinutes, preferredDays) - Find study slots.
3. assign_priorities(tasks, examDate) - Assign priorities (call after extraction).
4. schedule_tasks(tasks, slots) - Bin-pack tasks into slots (call after priorities and slots are ready).
5. persist_schedule(parts, userId, courseId, materialId, isIncremental) - Save to DB.
6. finish(status, message) - Complete with result status.

Rules:
- Call extract_and_parse_tasks first if tasks are not yet extracted.
- Call query_available_slots after tasks are extracted.
- Call schedule_tasks after tasks and slots are both available.
- Call persist_schedule after successful scheduling.
- Call finish with status "scheduled" on success, "over_capacity" if unscheduled, or "error" on failure.
- Do not call tools that are not relevant to the current state.
- Maximum 10 iterations.

Respond ONLY with valid JSON: {"action":"call_tool"|"finish","tool":"...","arguments":{"key":"value"}}
```

### 6. Max Iterations and Safety

- **`generatePlan`**: max 10 iterations
- **`checkAndRescheduleRoadmap`**: max 15 iterations (per course × courses)
- If max iterations reached without finish → return `"error"` status with alert
- If LLM returns 3 consecutive malformed decisions → fail fast with `"error"`

### 7. Error Handling

- Wrap tool execution in try-catch
- On exception: add error to `AgentState.error`, increment error count
- If error count >= 3: break loop, return error
- Otherwise: feed error summary back to LLM and let it retry or finish

### 8. Backward Compatibility

- Keep `StudyPlannerAgent` as a `@Service` with the same public methods (`generatePlan`, `checkAndRescheduleRoadmap`)
- Return the same `AgentPlanResult` / `AgentCheckResult` records
- Update `CourseService` and `RoadmapService` zero changes needed
- `AgentContext` record (currently unused) can be removed or repurposed

---

## Implementation Task List

1. **Create `AgentDecision` record** in `planning-service/src/main/java/com/smartstudy/planning/ai/model/AgentDecision.java`
2. **Create `AgentState` record** in `planning-service/src/main/java/com/smartstudy/planning/ai/model/AgentState.java`
3. **Refactor `PdfExtractorTool`** into `extract_and_parse_tasks` that returns a compact summary string (e.g., `"Extracted 15 tasks: Ch1(45m), Ch2(60m), ..."`) and populates `AgentState.tasks` internally
4. **Add `persist_schedule` tool** by exposing `AiSchedulePersistenceService.persist` as a `@Tool` method (or move it into a dedicated tool service)
5. **Refactor `StudyPlannerAgent.generatePlan`** into the agentic loop:
   - Initialize `AgentState`
   - Loop: build prompt → call LLM for `AgentDecision` → execute tool → update state
   - Break on `finish` or max iterations
6. **Refactor `StudyPlannerAgent.checkAndRescheduleRoadmap`** similarly (iterate courses, per-course agentic loop)
7. **Write the system prompt** as a `private static final String` with clear tool descriptions and state placeholders
8. **Add unit tests** for the agentic loop with a mock `ChatClient` (verify tool selection, iteration limits, error handling)

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| LLM hallucinates tool names | Validate `AgentDecision.tool()` against known set; error on invalid |
| LLM calls tools out of order | State log + guard conditions in prompt; invalid transitions return error summary |
| PDF extraction + parsing combined is slow | Run `extract_and_parse_tasks` in a single LLM call; cache task list in `AgentState` |
| Context window overflow with many rounds | Execution log is concise; max 10 iterations; no raw PDF text in prompts |
| Non-deterministic tool selection | Temperature 0.1; deterministic prompt structure |
| Existing `@Tool` callers break | Keep existing tool services; only change `StudyPlannerAgent` orchestration |

---

## Validation

- Run existing tests: `./mvnw test -pl planning-service`
- Add integration test with mocked `ChatClient` that returns predefined `AgentDecision` sequences
- Verify `generatePlan` still returns `AgentPlanResult` with same structure
- Verify `checkAndRescheduleRoadmap` still returns `AgentCheckResult` with same structure
- Manual smoke test: upload PDF material, verify agent completes in < 10 LLM rounds

---

## Out of Scope

- Adding memory/conversation history persistence
- Streaming tool call results
- Parallel tool execution
- Human-in-the-loop approval for tool calls
