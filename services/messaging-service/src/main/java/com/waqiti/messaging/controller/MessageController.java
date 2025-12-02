package com.waqiti.messaging.controller;

import com.waqiti.messaging.dto.*;
import com.waqiti.messaging.service.ConversationService;
import com.waqiti.messaging.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Message Management", description = "End-to-end encrypted messaging APIs")
@RequiredArgsConstructor
@Slf4j
public class MessageController {
    
    private final MessageService messageService;
    private final ConversationService conversationService;
    
    @PostMapping("/send")
    @Operation(summary = "Send encrypted message")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MessageDTO> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal String userId) {
        
        request.setSenderId(userId);
        MessageDTO message = messageService.sendMessage(request);
        return ResponseEntity.ok(message);
    }
    
    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Get conversation messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<MessageDTO>> getConversationMessages(
            @PathVariable String conversationId,
            @AuthenticationPrincipal String userId,
            Pageable pageable) {
        
        Page<MessageDTO> messages = messageService.getConversationMessages(conversationId, userId, pageable);
        return ResponseEntity.ok(messages);
    }
    
    @PostMapping("/{messageId}/read")
    @Operation(summary = "Mark message as read")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String messageId,
            @AuthenticationPrincipal String userId) {
        
        messageService.markAsRead(messageId, userId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{messageId}")
    @Operation(summary = "Delete message")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String messageId,
            @RequestParam(defaultValue = "false") boolean forEveryone,
            @AuthenticationPrincipal String userId) {
        
        messageService.deleteMessage(messageId, userId, forEveryone);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/conversations")
    @Operation(summary = "Create new conversation")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ConversationDTO> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal String userId) {
        
        request.setCreatorId(userId);
        ConversationDTO conversation = conversationService.createConversation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }
    
    @GetMapping("/conversations")
    @Operation(summary = "Get user conversations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ConversationDTO>> getConversations(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "false") boolean archived,
            Pageable pageable) {
        
        Page<ConversationDTO> conversations = conversationService.getUserConversations(userId, archived, pageable);
        return ResponseEntity.ok(conversations);
    }
    
    @PutMapping("/conversations/{conversationId}")
    @Operation(summary = "Update conversation")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ConversationDTO> updateConversation(
            @PathVariable String conversationId,
            @Valid @RequestBody UpdateConversationRequest request,
            @AuthenticationPrincipal String userId) {
        
        ConversationDTO conversation = conversationService.updateConversation(conversationId, request, userId);
        return ResponseEntity.ok(conversation);
    }
    
    @PostMapping("/conversations/{conversationId}/participants")
    @Operation(summary = "Add participants to conversation")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> addParticipants(
            @PathVariable String conversationId,
            @Valid @RequestBody AddParticipantsRequest request,
            @AuthenticationPrincipal String userId) {
        
        conversationService.addParticipants(conversationId, request.getUserIds(), userId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/conversations/{conversationId}/participants/{participantId}")
    @Operation(summary = "Remove participant from conversation")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable String conversationId,
            @PathVariable String participantId,
            @AuthenticationPrincipal String userId) {
        
        conversationService.removeParticipant(conversationId, participantId, userId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/conversations/{conversationId}/leave")
    @Operation(summary = "Leave conversation")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> leaveConversation(
            @PathVariable String conversationId,
            @AuthenticationPrincipal String userId) {
        
        conversationService.leaveConversation(conversationId, userId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/conversations/{conversationId}/archive")
    @Operation(summary = "Archive conversation")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> archiveConversation(
            @PathVariable String conversationId,
            @AuthenticationPrincipal String userId) {
        
        conversationService.archiveConversation(conversationId, userId, true);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/conversations/{conversationId}/unarchive")
    @Operation(summary = "Unarchive conversation")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> unarchiveConversation(
            @PathVariable String conversationId,
            @AuthenticationPrincipal String userId) {
        
        conversationService.archiveConversation(conversationId, userId, false);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/conversations/{conversationId}/mute")
    @Operation(summary = "Mute conversation notifications")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> muteConversation(
            @PathVariable String conversationId,
            @RequestParam(required = false) Integer durationMinutes,
            @AuthenticationPrincipal String userId) {
        
        conversationService.muteConversation(conversationId, userId, durationMinutes);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/conversations/{conversationId}/unmute")
    @Operation(summary = "Unmute conversation notifications")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> unmuteConversation(
            @PathVariable String conversationId,
            @AuthenticationPrincipal String userId) {
        
        conversationService.unmuteConversation(conversationId, userId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/messages/{messageId}/reactions")
    @Operation(summary = "Add reaction to message")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> addReaction(
            @PathVariable String messageId,
            @Valid @RequestBody AddReactionRequest request,
            @AuthenticationPrincipal String userId) {
        
        messageService.addReaction(messageId, userId, request.getEmoji());
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/messages/{messageId}/reactions")
    @Operation(summary = "Remove reaction from message")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> removeReaction(
            @PathVariable String messageId,
            @RequestParam String emoji,
            @AuthenticationPrincipal String userId) {
        
        messageService.removeReaction(messageId, userId, emoji);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<MessageDTO>> searchMessages(
            @RequestParam String query,
            @RequestParam(required = false) String conversationId,
            @AuthenticationPrincipal String userId,
            Pageable pageable) {
        
        Page<MessageDTO> results = messageService.searchMessages(query, conversationId, userId, pageable);
        return ResponseEntity.ok(results);
    }
}