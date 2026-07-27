package com.smartstudy.planning.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstudy.planning.chat.model.GeminiPrompt;
import com.smartstudy.planning.chat.model.RetrievedChunk;
import com.smartstudy.planning.config.ChatProperties;
import com.smartstudy.planning.dto.response.ChatHistoryResponse;
import com.smartstudy.planning.dto.response.ChatMessageResponse;
import com.smartstudy.planning.dto.response.ChatResponse;
import com.smartstudy.planning.dto.response.LlmStructuredResponse;
import com.smartstudy.planning.dto.response.PaginationMeta;
import com.smartstudy.planning.model.ChatMessage;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.MessageRole;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.processing.EmbeddingService;
import com.smartstudy.planning.processing.QdrantIndexingService;
import com.smartstudy.planning.repository.ChatMessageRepository;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.TaskRepository;
import io.qdrant.client.grpc.Points.ScoredPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central orchestrator for the entire chat flow.
 * Single entry point called by both the WebSocket handler and the REST controller.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final TaskRepository taskRepository;
    private final CourseRepository courseRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final EmbeddingService embeddingService;
    private final QdrantIndexingService qdrantIndexingService;
    private final PromptBuilder promptBuilder;
    private final GeminiChatClient geminiChatClient;
    private final ChatProperties chatProperties;
    private final ObjectMapper objectMapper;

    public ChatService(TaskRepository taskRepository,
                       CourseRepository courseRepository,
                       ChatMessageRepository chatMessageRepository,
                       EmbeddingService embeddingService,
                       QdrantIndexingService qdrantIndexingService,
                       PromptBuilder promptBuilder,
                       GeminiChatClient geminiChatClient,
                       ChatProperties chatProperties,
                       ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.courseRepository = courseRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.embeddingService = embeddingService;
        this.qdrantIndexingService = qdrantIndexingService;
        this.promptBuilder = promptBuilder;
        this.geminiChatClient = geminiChatClient;
        this.chatProperties = chatProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Process a user chat message: validate ownership, load context, call LLM,
     * persist messages, and return the response.
     */
    @Transactional
    public ChatResponse processMessage(String userId, UUID taskId,
                                       String message, String language) {
        // Step 1: Validate task ownership
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "TASK_NOT_FOUND"));

        // Step 2: Load chat history (last N messages)
        List<ChatMessage> history = loadRecentHistory(taskId);

        // Step 3: Load task context (title, course name)
        String courseName = resolveCourseName(task.getCourseId());

        // Step 4: Retrieve similar chunks from Qdrant
        List<RetrievedChunk> chunks = retrieveContext(userId, task.getCourseId(), message);

        // Step 5: Build prompt
        GeminiPrompt prompt = promptBuilder.buildPrompt(
                history, task.getTitle(), courseName, chunks, message, language);

        // Step 6: Call LLM
        LlmStructuredResponse llmResponse = geminiChatClient.generate(prompt);

        // Step 7: Persist user message
        ChatMessage userMsg = persistMessage(taskId, userId, MessageRole.USER,
                message, null);

        // Step 8: Persist assistant message
        ChatMessage assistantMsg = persistMessage(taskId, userId, MessageRole.ASSISTANT,
                llmResponse.answer(), llmResponse);

        // Step 9: Build and return response
        return new ChatResponse(List.of(
                toMessageResponse(userMsg),
                toMessageResponse(assistantMsg)
        ));
    }

    /**
     * Retrieve paginated chat history for a task.
     */
    @Transactional(readOnly = true)
    public ChatHistoryResponse getHistory(String userId, UUID taskId,
                                          int page, int size) {
        validateTaskOwnership(userId, taskId);

        Page<ChatMessage> messagePage = chatMessageRepository
                .findByTaskIdOrderByCreatedAtAsc(taskId, PageRequest.of(page, size));

        List<ChatMessageResponse> messages = messagePage.getContent().stream()
                .map(this::toMessageResponse)
                .toList();

        return new ChatHistoryResponse(
                new PaginationMeta(page, size, messagePage.hasNext()),
                messages
        );
    }

    /**
     * Delete all chat messages for a task.
     */
    @Transactional
    public void deleteChat(String userId, UUID taskId) {
        validateTaskOwnership(userId, taskId);

        if (!chatMessageRepository.existsByTaskId(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CHAT_NOT_FOUND");
        }

        chatMessageRepository.deleteAllByTaskId(taskId);
    }

    // ---- Private helpers ----

    private List<ChatMessage> loadRecentHistory(UUID taskId) {
        List<ChatMessage> recent = chatMessageRepository
                .findByTaskIdOrderByCreatedAtDesc(taskId,
                        PageRequest.of(0, chatProperties.historyContextSize()));
        // Reverse to chronological order for prompt assembly
        List<ChatMessage> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);
        return chronological;
    }

    private String resolveCourseName(UUID courseId) {
        if (courseId == null) {
            return null;
        }
        return courseRepository.findById(courseId)
                .map(Course::getName)
                .orElse(null);
    }

    private List<RetrievedChunk> retrieveContext(String userId, UUID courseId,
                                                  String userMessage) {
        if (courseId == null) {
            log.debug("Task has no courseId, skipping RAG context retrieval");
            return List.of();
        }

        try {
            // Embed the user query
            float[] queryVector = embeddingService.embedQuery(userMessage);

            // Search Qdrant
            List<ScoredPoint> results = qdrantIndexingService.search(
                    userId, courseId, queryVector, chatProperties.qdrantSearchLimit());

            // Convert to RetrievedChunk, filtering by score threshold
            return results.stream()
                    .filter(point -> point.getScore() >= chatProperties.qdrantScoreThreshold())
                    .map(this::toRetrievedChunk)
                    .toList();

        } catch (Exception e) {
            log.warn("RAG context retrieval failed, continuing without context: {}",
                    e.getMessage());
            return List.of();
        }
    }

    private RetrievedChunk toRetrievedChunk(ScoredPoint point) {
        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = point.getPayloadMap();
        return new RetrievedChunk(
                getStringPayload(payload, "text", ""),
                getStringPayload(payload, "section_title", ""),
                (int) getLongPayload(payload, "page_start", 0),
                (int) getLongPayload(payload, "page_end", 0),
                point.getScore()
        );
    }

    private String getStringPayload(
            Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload,
            String key, String defaultValue) {
        if (payload.containsKey(key)) {
            return payload.get(key).getStringValue();
        }
        return defaultValue;
    }

    private long getLongPayload(
            Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload,
            String key, long defaultValue) {
        if (payload.containsKey(key)) {
            return payload.get(key).getIntegerValue();
        }
        return defaultValue;
    }

    private ChatMessage persistMessage(UUID taskId, String userId,
                                       MessageRole role, String content,
                                       LlmStructuredResponse llmResponse) {
        ChatMessage msg = ChatMessage.builder()
                .taskId(taskId)
                .userId(userId)
                .role(role)
                .content(content)
                .build();

        if (llmResponse != null) {
            msg.setUsedContext(llmResponse.usedContext());
            msg.setConfidence(llmResponse.confidence());
            msg.setSuggestedFollowUp(llmResponse.suggestedFollowUp());
            if (llmResponse.sources() != null && !llmResponse.sources().isEmpty()) {
                try {
                    msg.setSources(objectMapper.writeValueAsString(llmResponse.sources()));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize sources: {}", e.getMessage());
                }
            }
        }

        return chatMessageRepository.save(msg);
    }

    private void validateTaskOwnership(String userId, UUID taskId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "TASK_NOT_FOUND"));
    }

    private ChatMessageResponse toMessageResponse(ChatMessage msg) {
        return new ChatMessageResponse(
                msg.getId().toString(),
                msg.getRole().name(),
                msg.getContent(),
                msg.getCreatedAt()
        );
    }
}
