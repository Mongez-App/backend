package com.smartstudy.planning.controller;

import com.smartstudy.planning.chat.ChatService;
import com.smartstudy.planning.dto.request.ChatMessageRequest;
import com.smartstudy.planning.dto.response.ChatHistoryResponse;
import com.smartstudy.planning.dto.response.ChatResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for AI Chat functionality (sending messages, retrieving history, deleting chat).
 */
@RestController
@RequestMapping("/tasks/{taskId}/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse sendMessage(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID taskId,
            @RequestHeader(value = "Accept-Language", defaultValue = "en") String language,
            @Valid @RequestBody ChatMessageRequest request) {
        log.info("POST /tasks/{}/chat | userId={} | lang={}", taskId, userId, language);
        return chatService.processMessage(userId, taskId, request.message(), language);
    }

    @GetMapping("/messages")
    public ChatHistoryResponse getMessages(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        log.info("GET /tasks/{}/chat/messages | userId={} | page={} size={}",
                taskId, userId, page, size);
        return chatService.getHistory(userId, taskId, page, size);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChat(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID taskId) {
        log.info("DELETE /tasks/{}/chat | userId={}", taskId, userId);
        chatService.deleteChat(userId, taskId);
    }
}
