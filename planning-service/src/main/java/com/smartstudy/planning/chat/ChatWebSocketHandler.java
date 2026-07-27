package com.smartstudy.planning.chat;

import com.smartstudy.planning.dto.request.ChatMessageRequest;
import com.smartstudy.planning.dto.response.ChatResponse;
import com.smartstudy.planning.dto.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket (STOMP) handler for chat messages.
 * <p>
 * Client sends to: /app/tasks/{taskId}/chat
 * Client subscribes to: /topic/tasks/{taskId}/chat
 * </p>
 */
@Controller
public class ChatWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final ChatService chatService;

    public ChatWebSocketHandler(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/tasks/{taskId}/chat")
    @SendTo("/topic/tasks/{taskId}/chat")
    public ChatResponse handleChatMessage(
            @DestinationVariable String taskId,
            @Payload ChatMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        // Auth check
        String userId = (String) headerAccessor.getSessionAttributes().get("userId");
        if (userId == null) {
            throw new MessagingException("Unauthorized: missing user context");
        }

        // Validate taskId
        UUID taskUuid;
        try {
            taskUuid = UUID.fromString(taskId);
        } catch (IllegalArgumentException e) {
            throw new ChatException("INVALID_TASK_ID", "Invalid task ID format: " + taskId);
        }

        // Validate message — @NotBlank on record is not enforced by STOMP
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ChatException("CHAT_MESSAGE_EMPTY", "Message must not be empty");
        }

        // Default language to "en" if not provided
        String language = headerAccessor.getFirstNativeHeader("Accept-Language");
        if (language == null || language.isBlank()) {
            language = "en";
        }

        log.info("Chat message received | taskId={} | userId={}", taskId, userId);
        return chatService.processMessage(userId, taskUuid, request.message(), language);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public ErrorResponse handleException(Exception ex) {
        log.error("WebSocket error: {}", ex.getMessage(), ex);

        String errorCode = "CHAT_ERROR";
        if (ex instanceof ChatException chatEx) {
            errorCode = chatEx.getErrorCode();
        }

        return new ErrorResponse(
                errorCode,
                ex.getMessage(),
                Instant.now().toString()
        );
    }
}

