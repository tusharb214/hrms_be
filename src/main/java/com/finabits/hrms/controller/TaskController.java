package com.sitegenius.hrms.controller;

import com.sitegenius.hrms.dto.request.TaskRequest.*;
import com.sitegenius.hrms.dto.response.ApiResponse;
import com.sitegenius.hrms.dto.response.TaskResponseDTO.*;
import com.sitegenius.hrms.entity.TaskChatMessage;
import com.sitegenius.hrms.enums.TaskStatus;
import com.sitegenius.hrms.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * TaskController — handles REST and WebSocket endpoints for task management.
 *
 * WebSocket (STOMP):
 *   Connect:    ws://host/ws  (SockJS)
 *   Subscribe:  /topic/task/{taskId}/chat      — live messages
 *   Subscribe:  /topic/task/{taskId}/update    — member updates
 *   Send:       /app/task/{taskId}/chat        — send a text message
 *
 * REST (JWT auth required on all endpoints):
 *   POST   /api/tasks                                  — create task (admin)
 *   PUT    /api/tasks/{id}                             — update task (admin)
 *   DELETE /api/tasks/{id}                             — delete task (admin)
 *   POST   /api/tasks/{id}/members                     — add member (admin)
 *   DELETE /api/tasks/{id}/members/{userId}            — remove member (admin)
 *   GET    /api/tasks/{id}/chat                        — get full chat history
 *   POST   /api/tasks/{id}/chat/files                  — upload file to chat
 *   GET    /api/tasks/{id}/chat/files/{messageId}      — download file
 *   ... (other existing endpoints below)
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Task Management")
public class TaskController {

    private final TaskService taskService;

    // ══════════════════════════════════════════════════════════════════════════
    // WEBSOCKET — STOMP message handler
    // Client sends to: /app/task/{taskId}/chat
    // Broadcast to:    /topic/task/{taskId}/chat  (handled in service)
    // ══════════════════════════════════════════════════════════════════════════

    @MessageMapping("/task/{taskId}/chat")
    public void handleChatMessage(
            @DestinationVariable Long taskId,
            @Payload ChatMessageRequest request,
            org.springframework.messaging.simp.stomp.StompHeaderAccessor accessor) {
        // Extract user email from STOMP session attributes (set by JWT auth interceptor)
        String email = (String) accessor.getSessionAttributes().get("userEmail");
        if (email == null && accessor.getUser() != null) {
            email = accessor.getUser().getName();
        }
        if (email == null) return; // unauthenticated — ignore

        taskService.sendChatMessage(taskId, request.getMessage(), email);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — EMPLOYEE LISTS
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/present-employees")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @ResponseBody
    @Operation(summary = "Today's present employees")
    public ResponseEntity<List<Map<String, Object>>> getPresentEmployees() {
        return ResponseEntity.ok(taskService.getPresentEmployeesToday());
    }

    @GetMapping("/all-employees")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @Operation(summary = "All active employees (for member management)")
    public ResponseEntity<List<Map<String, Object>>> getAllEmployees() {
        return ResponseEntity.ok(taskService.getAllActiveEmployees());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — CREATE TASK (one task, multiple members)
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @Operation(
            summary = "Create one task and assign multiple members",
            description = "Creates a SINGLE shared task. All listed employees become members. " +
                    "One shared status for the whole task."
    )
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.createTask(request, authentication.getName()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — UPDATE TASK
    // ══════════════════════════════════════════════════════════════════════════

    @PutMapping("/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @Operation(summary = "Update task title, description, priority, deadline")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(taskService.updateTask(taskId, request, authentication.getName()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — MEMBER MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/{taskId}/members")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @Operation(
            summary = "Add a member to an existing task",
            description = "New member will immediately see full chat history."
    )
    public ResponseEntity<TaskResponse> addMember(
            @PathVariable Long taskId,
            @Valid @RequestBody AddMemberRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(taskService.addMember(taskId, request, authentication.getName()));
    }

    @DeleteMapping("/{taskId}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @Operation(summary = "Remove a member from a task")
    public ResponseEntity<TaskResponse> removeMember(
            @PathVariable Long taskId,
            @PathVariable Long userId,
            Authentication authentication) {
        return ResponseEntity.ok(taskService.removeMember(taskId, userId, authentication.getName()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — DELETE TASK
    // ══════════════════════════════════════════════════════════════════════════

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @Operation(summary = "Delete a task (not allowed if APPROVED)")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable Long taskId,
            Authentication authentication) {
        taskService.deleteTask(taskId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Task deleted successfully", null));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — READ
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @Operation(summary = "Get all tasks — filter by status, paginated")
    public ResponseEntity<PagedTaskResponse> getAllTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(taskService.getAllTasks(status, page, size));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @Operation(summary = "Get tasks by primary assignee")
    public ResponseEntity<PagedTaskResponse> getTasksByUser(
            @PathVariable Long userId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(taskService.getTasksByUser(userId, status, page, size));
    }

    @PutMapping("/{taskId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @Operation(summary = "Approve or reject a submitted task")
    public ResponseEntity<TaskResponse> reviewTask(
            @PathVariable Long taskId,
            @Valid @RequestBody ReviewTaskRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(taskService.reviewTask(taskId, request, authentication.getName()));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @Operation(summary = "Admin dashboard — task counts + overdue summary")
    public ResponseEntity<TaskDashboardResponse> getDashboard() {
        return ResponseEntity.ok(taskService.getDashboard());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EMPLOYEE
    // ══════════════════════════════════════════════════════════════════════════

    // ── EMPLOYEE — must be BEFORE /{taskId} ──────────────────────────────────────

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @ResponseBody
    @Operation(summary = "Get my tasks (member-based)")
    public ResponseEntity<PagedTaskResponse> getMyTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return ResponseEntity.ok(taskService.getMyTasks(authentication.getName(), status, page, size));
    }

// ── SHARED — must be AFTER all fixed-path endpoints ──────────────────────────

    @GetMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @ResponseBody
    @Operation(summary = "Get task details with members and comment history")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.getTaskById(taskId));
    }

    @PutMapping("/{taskId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @ResponseBody
    @Operation(summary = "Update task status")
    public ResponseEntity<TaskResponse> updateTaskStatus(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(taskService.updateTaskStatus(taskId, request, authentication.getName()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHAT — REST endpoints
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * REST fallback for sending a text chat message.
     * The service also broadcasts via WebSocket so all connected clients update.
     * Frontend uses this instead of STOMP send for simplicity with JWT auth.
     */
    @PostMapping("/{taskId}/chat/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @ResponseBody
    @Operation(summary = "Send a text message to task chat (REST + WS broadcast)")
    public ResponseEntity<ChatMessageResponse> sendChatMessage(
            @PathVariable Long taskId,
            @RequestBody ChatMessageRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.sendChatMessage(taskId, request.getMessage(), authentication.getName()));
    }

    @GetMapping("/{taskId}/chat")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @ResponseBody
    @Operation(
            summary = "Get full chat history for a task",
            description = "Returns all messages from the beginning — new members get full history."
    )
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
            @PathVariable Long taskId,
            Authentication authentication) {
        return ResponseEntity.ok(taskService.getChatHistory(taskId, authentication.getName()));
    }

    @PostMapping("/{taskId}/chat/files")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @ResponseBody
    @Operation(
            summary = "Upload a file or screenshot to task chat (max 10 MB)",
            description = "Saves file to C:/hrms-uploads/task-chat/{taskId}/ and broadcasts via WebSocket."
    )
    public ResponseEntity<ChatMessageResponse> uploadChatFile(
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            Authentication authentication) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.uploadChatFile(taskId, file, caption, authentication.getName()));
    }

    /**
     * Serve a chat file / image / video.
     *
     * Key behaviour for VIDEO streaming:
     *  - Content-Type  set to the stored MIME type (video/mp4, video/webm, etc.)
     *  - Accept-Ranges: bytes  — required for the browser <video> player to seek
     *  - Content-Disposition: inline — browser plays/shows the file in-tab
     *
     * For non-video files (images, PDFs, docs) behaviour is unchanged.
     */
    @GetMapping("/{taskId}/chat/files/{messageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @Operation(summary = "Stream / download a file attached to a chat message")
    public ResponseEntity<Resource> downloadChatFile(
            @PathVariable Long taskId,
            @PathVariable Long messageId,
            Authentication authentication) {
        TaskChatMessage msg = taskService.getChatMessageForDownload(taskId, messageId, authentication.getName());

        File file = new File(msg.getFilePath());
        if (!file.exists())
            return ResponseEntity.notFound().build();

        Resource resource = new FileSystemResource(file);
        String encodedName = URLEncoder.encode(msg.getFileName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        String contentType = msg.getFileType() != null ? msg.getFileType() : "application/octet-stream";
        boolean isVideo    = contentType.startsWith("video/");

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName);

        // Accept-Ranges is essential for browser <video> seeking
        if (isVideo) {
            builder = builder.header(HttpHeaders.ACCEPT_RANGES, "bytes");
        }

        return builder.body(resource);
    }



    @PostMapping("/{taskId}/comments")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @ResponseBody
    @Operation(summary = "Add a legacy REST comment to a task")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long taskId,
            @Valid @RequestBody AddCommentRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.addComment(taskId, request, authentication.getName()));
    }
    @DeleteMapping("/{taskId}/chat/{messageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @ResponseBody
    @Operation(summary = "Delete a chat message (sender or admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteChatMessage(
            @PathVariable Long taskId,
            @PathVariable Long messageId,
            Authentication authentication) {
        taskService.deleteChatMessage(taskId, messageId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Message deleted", null));
    }
}