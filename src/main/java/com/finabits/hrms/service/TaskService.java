package com.sitegenius.hrms.service;

import com.sitegenius.hrms.dto.request.TaskRequest.*;
import com.sitegenius.hrms.dto.response.TaskResponseDTO.*;
import com.sitegenius.hrms.entity.*;
import com.sitegenius.hrms.enums.Priority;
import com.sitegenius.hrms.enums.TaskStatus;
import com.sitegenius.hrms.exception.BadRequestException;
import com.sitegenius.hrms.exception.InvalidStatusTransitionException;
import com.sitegenius.hrms.exception.ResourceNotFoundException;
import com.sitegenius.hrms.exception.UnauthorizedException;
import com.sitegenius.hrms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository            taskRepository;
    private final TaskCommentRepository     commentRepository;
    private final TaskMemberRepository      memberRepository;
    private final TaskChatMessageRepository chatRepository;
    private final UserRepository            userRepository;
    private final AttendanceRepository      attendanceRepository;
    private final EmailService              emailService;
    private final NotificationService       notificationService;
    private final SimpMessagingTemplate     messagingTemplate;

    private static final String UPLOAD_ROOT       = "C:/hrms-uploads/task-chat/";
    private static final long   MAX_FILE_SIZE       = 10L * 1024 * 1024;
    private static final long   MAX_VIDEO_FILE_SIZE = 50L * 1024 * 1024;

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — EMPLOYEE LISTS
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPresentEmployeesToday() {
        LocalDate today = LocalDate.now();
        List<Long> presentUserIds = attendanceRepository.findPresentUserIdsByDate(today);
        if (presentUserIds.isEmpty()) return List.of();
        return userRepository.findAllById(presentUserIds).stream()
                .filter(User::isActive)
                .map(this::buildEmpMap)
                .sorted(Comparator.comparing(m -> ((String) m.get("fullName"))))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllActiveEmployees() {
        return userRepository.findAll().stream()
                .filter(u -> u.isActive() && "EMPLOYEE".equals(u.getRole().name()))
                .map(this::buildEmpMap)
                .sorted(Comparator.comparing(m -> ((String) m.get("fullName"))))
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildEmpMap(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",           u.getId());
        m.put("fullName",     u.getFullName());
        m.put("employeeCode", u.getEmployeeCode());
        m.put("email",        u.getEmail());
        m.put("designation",  u.getDesignation());
        m.put("department",   u.getDepartment());
        return m;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — CREATE TASK (ONE task, multiple members)
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, String adminEmail) {
        User admin = findUserByEmail(adminEmail);

        if (request.getAssignedToIds() == null || request.getAssignedToIds().isEmpty())
            throw new BadRequestException("At least one employee must be selected.");

        List<Long> distinctIds = request.getAssignedToIds().stream()
                .distinct().collect(Collectors.toList());

        if (distinctIds.contains(admin.getId()))
            throw new BadRequestException("Admin cannot assign a task to themselves.");

        validateDeadline(request.getPriority(), request.getDeadline());

        // Fetch all members
        List<User> members = new ArrayList<>();
        for (Long uid : distinctIds) {
            members.add(findUserById(uid));
        }

        // Primary assignee = first in list
        User primaryAssignee = members.get(0);

        // Create ONE task
        Task task = Task.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .priority(request.getPriority())
                .status(TaskStatus.PENDING)
                .deadline(request.getDeadline())
                .assignedTo(primaryAssignee)
                .createdBy(admin)
                .build();
        task = taskRepository.save(task);

        // System chat message
        sendSystemChatMessage(task, "📋 Task created by " + admin.getFullName()
                + " — " + members.size() + " member(s) assigned");

        // Add all members + notify
        for (User member : members) {
            TaskMember tm = TaskMember.builder()
                    .task(task)
                    .user(member)
                    .addedBy(admin)
                    .build();
            memberRepository.save(tm);

            final Task savedTask = task;
            try {
                notificationService.taskAssigned(member.getId(), savedTask.getTitle(), savedTask.getId());
            } catch (Exception e) {
                log.error("Task assignment notification failed for user {}: {}", member.getId(), e.getMessage());
            }
            try {
                sendTaskEmail(member, savedTask, "New Task Assigned: ");
            } catch (Exception e) {
                log.error("Task assignment email failed for user {}: {}", member.getId(), e.getMessage());
            }
        }

        // ── WebSocket broadcast AFTER transaction commits ──────────────────────
        // Captures member IDs now (inside transaction), broadcasts after commit
        // so employees' GET /api/tasks/my will already see the new task in DB.
        // ── WebSocket broadcast AFTER transaction commits ──────────────────────
        // ── WebSocket broadcast AFTER transaction commits ──────────────────────
        final Task finalTask = task;   // ← fixes "needs to be final" compiler error
        final List<Long> memberIds = members.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messagingTemplate.convertAndSend(
                                "/topic/tasks/all",
                                Map.of("event", "TASK_CREATED")
                        );
                        for (Long userId : memberIds) {
                            messagingTemplate.convertAndSend(
                                    "/topic/tasks/user/" + userId,
                                    Map.of("event", "TASK_CREATED")
                            );
                        }
                        log.info("WebSocket broadcast sent for new task id={} to {} member(s)",
                                finalTask.getId(), memberIds.size());  // ← use finalTask here
                    }
                }
        );
// ── END WebSocket block ───────────────────────────────────────────────
        // ── END WebSocket block ───────────────────────────────────────────────

        log.info("Task created: id={}, title='{}', members={}", task.getId(), task.getTitle(), distinctIds);
        return mapToTaskResponse(task);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — UPDATE TASK
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public TaskResponse updateTask(Long taskId, UpdateTaskRequest request, String adminEmail) {
        Task task = findTaskById(taskId);
        findUserByEmail(adminEmail);

        if (task.getStatus() == TaskStatus.APPROVED)
            throw new BadRequestException("Cannot edit an APPROVED task.");

        validateDeadline(request.getPriority(), request.getDeadline());

        boolean changed = !task.getTitle().equals(request.getTitle().trim())
                || !task.getDeadline().equals(request.getDeadline())
                || !task.getPriority().equals(request.getPriority());

        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        task.setPriority(request.getPriority());
        task.setDeadline(request.getDeadline());
        taskRepository.save(task);

        if (changed) {
            sendSystemChatMessage(task, "✏️ Task updated by admin — new deadline: " + request.getDeadline());
            List<TaskMember> members = memberRepository.findByTaskIdOrderByAddedAtAsc(taskId);
            for (TaskMember tm : members) {
                final User member = tm.getUser();
                final Task t      = task;
                try {
                    notificationService.create(member.getId(), "TASK_UPDATED", "✏️ Task Updated",
                            "Task \"" + t.getTitle() + "\" was updated by admin", t.getId(), "TASK");
                } catch (Exception e) {
                    log.error("Update notification failed for user {}: {}", member.getId(), e.getMessage());
                }
            }
        }

        return mapToTaskResponse(task);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — ADD MEMBER TO TASK
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public TaskResponse addMember(Long taskId, AddMemberRequest request, String adminEmail) {
        Task task  = findTaskById(taskId);
        User admin = findUserByEmail(adminEmail);
        User user  = findUserById(request.getUserId());

        if (task.getStatus() == TaskStatus.APPROVED)
            throw new BadRequestException("Cannot add members to an APPROVED task.");

        if (memberRepository.existsByTaskIdAndUserId(taskId, request.getUserId()))
            throw new BadRequestException(user.getFullName() + " is already a member of this task.");

        TaskMember tm = TaskMember.builder()
                .task(task)
                .user(user)
                .addedBy(admin)
                .build();
        memberRepository.save(tm);

        sendSystemChatMessage(task, "👤 " + user.getFullName() + " was added to this task by admin");
        broadcastTaskUpdate(taskId);

        try {
            notificationService.taskAssigned(user.getId(), task.getTitle(), task.getId());
        } catch (Exception e) {
            log.error("Add member notification failed: {}", e.getMessage());
        }
        try {
            sendTaskEmail(user, task, "Added to Task: ");
        } catch (Exception e) {
            log.error("Add member email failed: {}", e.getMessage());
        }

        log.info("Member {} added to task {} by admin {}", user.getId(), taskId, adminEmail);
        return mapToTaskResponse(task);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — REMOVE MEMBER FROM TASK
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public TaskResponse removeMember(Long taskId, Long userId, String adminEmail) {
        Task task = findTaskById(taskId);
        User user = findUserById(userId);
        findUserByEmail(adminEmail);

        if (task.getStatus() == TaskStatus.APPROVED)
            throw new BadRequestException("Cannot remove members from an APPROVED task.");

        if (!memberRepository.existsByTaskIdAndUserId(taskId, userId))
            throw new BadRequestException(user.getFullName() + " is not a member of this task.");

        long memberCount = memberRepository.countByTaskId(taskId);
        if (memberCount <= 1)
            throw new BadRequestException("Cannot remove the last member. Delete the task instead.");

        memberRepository.deleteByTaskIdAndUserId(taskId, userId);

        // If removed user was primary assignee, reassign to next member
        if (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(userId)) {
            List<TaskMember> remaining = memberRepository.findByTaskIdOrderByAddedAtAsc(taskId);
            if (!remaining.isEmpty()) {
                task.setAssignedTo(remaining.get(0).getUser());
                taskRepository.save(task);
            }
        }

        sendSystemChatMessage(task, "🚫 " + user.getFullName() + " was removed from this task by admin");
        broadcastTaskUpdate(taskId);

        try {
            notificationService.create(userId, "TASK_REMOVED", "🚫 Removed from Task",
                    "You were removed from task \"" + task.getTitle() + "\" by admin", task.getId(), "TASK");
        } catch (Exception e) {
            log.error("Remove member notification failed: {}", e.getMessage());
        }

        log.info("Member {} removed from task {} by admin {}", userId, taskId, adminEmail);
        return mapToTaskResponse(task);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — DELETE TASK
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void deleteTask(Long taskId, String adminEmail) {
        Task task = findTaskById(taskId);
        if (task.getStatus() == TaskStatus.APPROVED)
            throw new BadRequestException("Cannot delete an APPROVED task.");

        String taskTitle = task.getTitle();
        List<TaskMember> members = memberRepository.findByTaskIdOrderByAddedAtAsc(taskId);

        taskRepository.delete(task);
        log.info("Task {} '{}' deleted by admin {}", taskId, taskTitle, adminEmail);

        for (TaskMember tm : members) {
            final User member = tm.getUser();
            try {
                notificationService.create(member.getId(), "TASK_DELETED", "🗑️ Task Removed",
                        "Task \"" + taskTitle + "\" has been removed by admin", null, "TASK");
            } catch (Exception e) {
                log.error("Delete task notification failed for user {}: {}", member.getId(), e.getMessage());
            }
            try {
                emailService.sendAnnouncementEmail(member.getEmail(), member.getFullName(),
                        "Task Removed: " + taskTitle,
                        "Task <b>" + taskTitle + "</b> has been removed by the admin.");
            } catch (Exception e) {
                log.error("Delete task email failed for user {}: {}", member.getId(), e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — GET ALL TASKS
    // Replace the existing getAllTasks method in TaskService with this one
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public PagedTaskResponse getAllTasks(TaskStatus status, int page, int size) {
        validatePaginationParams(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Task> taskPage = (status == null)
                ? taskRepository.findAllTasksNoFilter(pageable)
                : taskRepository.findAllTasks(status, pageable);
        return buildPagedResponse(taskPage, getGlobalStatusCounts());
    }

    @Transactional(readOnly = true)
    public PagedTaskResponse getTasksByUser(Long userId, TaskStatus status, int page, int size) {
        validatePaginationParams(page, size);
        findUserById(userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("deadline").ascending());
        Page<Task> taskPage = (status == null)
                ? taskRepository.findTasksByUserNoFilter(userId, pageable)
                : taskRepository.findTasksByUser(userId, status, pageable);
        return buildPagedResponse(taskPage, getStatusCountsForUser(userId));
    }


    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — REVIEW TASK (APPROVE / REJECT)
    // Tracks: approvedBy, approvedAt, reviewNote
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public TaskResponse reviewTask(Long taskId, ReviewTaskRequest request, String adminEmail) {
        Task task  = findTaskById(taskId);
        User admin = findUserByEmail(adminEmail);

        if (task.getStatus() != TaskStatus.SUBMITTED)
            throw new BadRequestException("Only SUBMITTED tasks can be reviewed. Current: " + task.getStatus());
        if (request.getStatus() != TaskStatus.APPROVED && request.getStatus() != TaskStatus.REJECTED)
            throw new BadRequestException("Review status must be APPROVED or REJECTED.");
        if (request.getStatus() == TaskStatus.REJECTED
                && (request.getComment() == null || request.getComment().isBlank()))
            throw new BadRequestException("A rejection reason is required.");

        task.setStatus(request.getStatus());

        // ── Track who approved/rejected and when ──────────────────────────────
        task.setApprovedBy(admin);
        task.setApprovedAt(LocalDateTime.now());
        if (request.getComment() != null && !request.getComment().isBlank()) {
            task.setReviewNote(request.getComment().trim());
        }

        taskRepository.save(task);

        if (request.getComment() != null && !request.getComment().isBlank())
            saveComment(task, admin, request.getComment());

        String icon = request.getStatus() == TaskStatus.APPROVED ? "✅" : "❌";
        sendSystemChatMessage(task, icon + " Task " + request.getStatus().name().toLowerCase()
                + " by " + admin.getFullName()
                + (request.getComment() != null && !request.getComment().isBlank()
                ? " — " + request.getComment() : ""));

        List<TaskMember> members = memberRepository.findByTaskIdOrderByAddedAtAsc(taskId);
        for (TaskMember tm : members) {
            final User member = tm.getUser();
            try {
                notificationService.taskStatusChanged(member.getId(), task.getTitle(),
                        request.getStatus().name(), taskId);
            } catch (Exception e) {
                log.error("Review notification failed for user {}: {}", member.getId(), e.getMessage());
            }
            try {
                boolean approved = request.getStatus() == TaskStatus.APPROVED;
                String emailBody = "Task <b>" + task.getTitle() + "</b> has been <b>"
                        + request.getStatus().name().toLowerCase() + "</b>."
                        + (request.getComment() != null && !request.getComment().isBlank()
                        ? "<br/><br/><b>Admin Note:</b> " + request.getComment() : "");
                emailService.sendAnnouncementEmail(member.getEmail(), member.getFullName(),
                        (approved ? "✅ Task Approved" : "❌ Task Rejected"), emailBody);
            } catch (Exception e) {
                log.error("Review email failed for user {}: {}", member.getId(), e.getMessage());
            }
        }

        return mapToTaskResponse(task);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — DASHBOARD
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public TaskDashboardResponse getDashboard() {
        Map<String, Long> statusCounts = getGlobalStatusCounts();
        long total        = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long overdueCount = statusCounts.getOrDefault("OVERDUE", 0L);
        Pageable top5     = PageRequest.of(0, 5, Sort.by("deadline").ascending());
        List<TaskResponse> recentOverdue = taskRepository.findOverdueTasks(top5)
                .getContent().stream().map(this::mapToTaskResponse).collect(Collectors.toList());
        TaskDashboardResponse dashboard = new TaskDashboardResponse();
        dashboard.setTotalTasks(total);
        dashboard.setCountByStatus(statusCounts);
        dashboard.setOverdueCount(overdueCount);
        dashboard.setRecentOverdueTasks(recentOverdue);
        return dashboard;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EMPLOYEE — GET MY TASKS (member-based)
    // ══════════════════════════════════════════════════════════════════════════


    @Transactional(readOnly = true)
    public PagedTaskResponse getMyTasks(String email, TaskStatus status, int page, int size) {
        validatePaginationParams(page, size);
        User user = findUserByEmail(email);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending()); // ← changed
        Page<Task> taskPage = (status == null)
                ? taskRepository.findMyTasksAll(user.getId(), pageable)
                : taskRepository.findMyTasksByStatus(user.getId(), status, pageable);
        return buildPagedResponse(taskPage, getStatusCountsForUser(user.getId()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EMPLOYEE — UPDATE TASK STATUS
    // Tracks: resolvedBy, resolvedAt when status → SUBMITTED
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public TaskResponse updateTaskStatus(Long taskId, UpdateStatusRequest request, String email) {
        Task task = findTaskById(taskId);
        User user = findUserByEmail(email);

        boolean isMember = memberRepository.existsByTaskIdAndUserId(taskId, user.getId());
        boolean isAdmin  = "ADMIN".equals(user.getRole().name());
        if (!isMember && !isAdmin)
            throw new UnauthorizedException("You are not a member of task #" + taskId);

        validateStatusTransition(task.getStatus(), request.getStatus());
        task.setStatus(request.getStatus());

        // ── Track who resolved (submitted) the task and when ─────────────────
        if (request.getStatus() == TaskStatus.SUBMITTED) {
            task.setResolvedBy(user);
            task.setResolvedAt(LocalDateTime.now());
        }

        // If task is restarted (rejected/overdue → in_progress), clear resolution info
        if (request.getStatus() == TaskStatus.IN_PROGRESS) {
            task.setResolvedBy(null);
            task.setResolvedAt(null);
            // Also clear review info in case admin previously rejected
            task.setApprovedBy(null);
            task.setApprovedAt(null);
            task.setReviewNote(null);
        }

        taskRepository.save(task);

        if (request.getComment() != null && !request.getComment().isBlank())
            saveComment(task, user, request.getComment());

        sendSystemChatMessage(task, "🔄 Status changed to " + request.getStatus().name()
                + " by " + user.getFullName());
        broadcastTaskUpdate(taskId);

        if (request.getStatus() == TaskStatus.SUBMITTED) {
            try {
                notificationService.taskStatusChanged(task.getCreatedBy().getId(),
                        task.getTitle(), "SUBMITTED", taskId);
            } catch (Exception e) {
                log.error("Submit notification failed: {}", e.getMessage());
            }
        }

        return mapToTaskResponse(task);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHAT — GET ALL MESSAGES
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatHistory(Long taskId, String email) {
        Task task = findTaskById(taskId);
        User user = findUserByEmail(email);
        assertChatAccess(task, user);

        return chatRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(m -> mapToChatMessage(m, user.getId()))
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHAT — SEND TEXT MESSAGE (REST + WS broadcast)
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public ChatMessageResponse sendChatMessage(Long taskId, String text, String email) {
        Task task = findTaskById(taskId);
        User user = findUserByEmail(email);
        assertChatAccess(task, user);

        if (text == null || text.isBlank())
            throw new BadRequestException("Message cannot be empty.");

        TaskChatMessage msg = TaskChatMessage.builder()
                .task(task)
                .sender(user)
                .message(text.trim())
                .messageType("TEXT")
                .build();
        msg = chatRepository.save(msg);

        ChatMessageResponse response = mapToChatMessage(msg, user.getId());
        messagingTemplate.convertAndSend("/topic/task/" + taskId + "/chat", response);
        notifyChatMembers(task, user);

        return response;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHAT — UPLOAD FILE
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public ChatMessageResponse uploadChatFile(Long taskId, MultipartFile file,
                                              String caption, String email) throws IOException {
        Task task = findTaskById(taskId);
        User user = findUserByEmail(email);
        assertChatAccess(task, user);

        if (file == null || file.isEmpty())
            throw new BadRequestException("File cannot be empty.");

        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        boolean isImage = mimeType.startsWith("image/");
        boolean isVideo = mimeType.startsWith("video/");

        long effectiveLimit = isVideo ? MAX_VIDEO_FILE_SIZE : MAX_FILE_SIZE;
        if (file.getSize() > effectiveLimit)
            throw new BadRequestException("File size exceeds " + (effectiveLimit / (1024 * 1024))
                    + " MB limit. Actual: " + (file.getSize() / (1024 * 1024)) + " MB");

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String extension    = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx >= 0) extension = originalName.substring(dotIdx);

        String dirPath = UPLOAD_ROOT + taskId + "/";
        Path dir = Paths.get(dirPath);
        if (!Files.exists(dir)) Files.createDirectories(dir);

        String savedName = UUID.randomUUID() + extension;
        Path   filePath  = dir.resolve(savedName);
        file.transferTo(filePath.toFile());

        String msgType = isImage ? "IMAGE" : isVideo ? "VIDEO" : "FILE";

        TaskChatMessage msg = TaskChatMessage.builder()
                .task(task)
                .sender(user)
                .message(caption != null && !caption.isBlank() ? caption.trim() : null)
                .messageType(msgType)
                .fileName(originalName)
                .filePath(filePath.toAbsolutePath().toString())
                .fileSize(file.getSize())
                .fileType(mimeType)
                .build();
        msg = chatRepository.save(msg);

        log.info("File uploaded for task {} by user {}: {} ({} bytes)",
                taskId, user.getId(), originalName, file.getSize());

        ChatMessageResponse response = mapToChatMessage(msg, user.getId());
        messagingTemplate.convertAndSend("/topic/task/" + taskId + "/chat", response);
        notifyChatMembers(task, user);

        return response;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHAT — DOWNLOAD FILE
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public TaskChatMessage getChatMessageForDownload(Long taskId, Long messageId, String email) {
        Task task = findTaskById(taskId);
        User user = findUserByEmail(email);
        assertChatAccess(task, user);

        TaskChatMessage msg = chatRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat message not found: " + messageId));

        if (!msg.getTask().getId().equals(taskId))
            throw new BadRequestException("Message does not belong to task " + taskId);
        if (msg.getFilePath() == null)
            throw new BadRequestException("No file attached to this message.");

        return msg;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHAT — DELETE MESSAGE
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void deleteChatMessage(Long taskId, Long messageId, String email) {
        Task task = findTaskById(taskId);
        User user = findUserByEmail(email);
        assertChatAccess(task, user);

        TaskChatMessage msg = chatRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat message not found: " + messageId));

        if (!msg.getTask().getId().equals(taskId))
            throw new BadRequestException("Message does not belong to task " + taskId);

        boolean isAdmin  = "ADMIN".equals(user.getRole().name());
        boolean isSender = msg.getSender().getId().equals(user.getId());

        if (!isAdmin && !isSender)
            throw new UnauthorizedException("You can only delete your own messages.");

        if (msg.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(msg.getFilePath()));
            } catch (Exception e) {
                log.warn("Could not delete file for message {}: {}", messageId, e.getMessage());
            }
        }

        chatRepository.delete(msg);

        messagingTemplate.convertAndSend("/topic/task/" + taskId + "/chat",
                Map.of("id", messageId, "deleted", true, "taskId", taskId));

        log.info("Chat message {} deleted from task {} by user {}", messageId, taskId, user.getId());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SHARED — GET TASK BY ID
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long taskId) {
        return mapToTaskResponse(findTaskById(taskId));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SHARED — ADD COMMENT (legacy REST)
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public CommentResponse addComment(Long taskId, AddCommentRequest request, String email) {
        Task task = findTaskById(taskId);
        User user = findUserByEmail(email);
        assertChatAccess(task, user);

        TaskComment saved = saveComment(task, user, request.getComment().trim());
        log.info("Comment added on task {} by user {}", taskId, email);

        Long recipientId = "ADMIN".equals(user.getRole().name())
                ? (task.getAssignedTo() != null ? task.getAssignedTo().getId() : null)
                : task.getCreatedBy().getId();
        if (recipientId != null) {
            try {
                notificationService.taskComment(recipientId, user.getFullName(), task.getTitle(), taskId);
            } catch (Exception e) {
                log.error("Comment notification failed: {}", e.getMessage());
            }
        }

        return mapToCommentResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCHEDULER — MARK OVERDUE
    // ══════════════════════════════════════════════════════════════════════════

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markOverdueTasks() {
        List<Task> toMark = taskRepository.findTasksToMarkOverdue(LocalDate.now());
        if (toMark.isEmpty()) return;
        for (Task task : toMark) {
            task.setStatus(TaskStatus.OVERDUE);
            taskRepository.save(task);
            log.warn("Task #{} '{}' marked OVERDUE", task.getId(), task.getTitle());

            List<TaskMember> members = memberRepository.findByTaskIdOrderByAddedAtAsc(task.getId());
            for (TaskMember tm : members) {
                final User member = tm.getUser();
                try {
                    emailService.sendAnnouncementEmail(member.getEmail(), member.getFullName(),
                            "⚠️ Task Overdue: " + task.getTitle(),
                            "Task <b>" + task.getTitle() + "</b> is now marked <b>OVERDUE</b>.");
                } catch (Exception e) {
                    log.error("Overdue email failed for user {}: {}", member.getId(), e.getMessage());
                }
            }
        }
        log.info("Overdue check complete — {} tasks marked.", toMark.size());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private void assertChatAccess(Task task, User user) {
        boolean isAdmin  = "ADMIN".equals(user.getRole().name());
        boolean isMember = memberRepository.existsByTaskIdAndUserId(task.getId(), user.getId());
        if (!isAdmin && !isMember)
            throw new UnauthorizedException("You do not have access to task #" + task.getId());
    }

    private void sendSystemChatMessage(Task task, String text) {
        try {
            TaskChatMessage msg = TaskChatMessage.builder()
                    .task(task)
                    .sender(task.getCreatedBy())
                    .message(text)
                    .messageType("SYSTEM")
                    .build();
            msg = chatRepository.save(msg);
            ChatMessageResponse response = mapToChatMessage(msg, null);
            messagingTemplate.convertAndSend("/topic/task/" + task.getId() + "/chat", response);
        } catch (Exception e) {
            log.error("System chat message failed for task {}: {}", task.getId(), e.getMessage());
        }
    }

    private void broadcastTaskUpdate(Long taskId) {
        try {
            messagingTemplate.convertAndSend("/topic/task/" + taskId + "/update",
                    Map.of("taskId", taskId, "type", "MEMBER_UPDATE"));
        } catch (Exception e) {
            log.error("broadcastTaskUpdate failed for task {}: {}", taskId, e.getMessage());
        }
    }

    private void validateDeadline(Priority priority, LocalDate deadline) {
        if (deadline.isBefore(LocalDate.now()))
            throw new BadRequestException("Deadline cannot be in the past.");
    }

    private void sendTaskEmail(User assignedTo, Task task, String subjectPrefix) {
        String body = "You have been assigned a task.<br/><br/>"
                + "<b>Task:</b> " + task.getTitle() + "<br/>"
                + "<b>Priority:</b> " + task.getPriority().name() + "<br/>"
                + "<b>Deadline:</b> " + task.getDeadline() + "<br/><br/>"
                + "Please log in to HRMS to view the full details.";
        emailService.sendAnnouncementEmail(assignedTo.getEmail(), assignedTo.getFullName(),
                subjectPrefix + task.getTitle(), body);
    }

    private void notifyChatMembers(Task task, User sender) {
        try {
            List<TaskMember> members = memberRepository.findByTaskIdOrderByAddedAtAsc(task.getId());
            List<Long> memberIds = members.stream()
                    .map(tm -> tm.getUser().getId())
                    .collect(Collectors.toList());

            if (task.getCreatedBy() != null && !memberIds.contains(task.getCreatedBy().getId())) {
                memberIds.add(task.getCreatedBy().getId());
            }

            notificationService.taskChatMessage(
                    task.getId(),
                    task.getTitle(),
                    sender.getFullName(),
                    sender.getId(),
                    memberIds
            );
        } catch (Exception e) {
            log.error("Chat notification failed for task {}: {}", task.getId(), e.getMessage());
        }
    }

    private Task findTaskById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private TaskComment saveComment(Task task, User user, String text) {
        return commentRepository.save(
                TaskComment.builder().task(task).commentedBy(user).comment(text).build());
    }

    private void validateStatusTransition(TaskStatus current, TaskStatus requested) {
        boolean valid = switch (current) {
            case PENDING     -> requested == TaskStatus.IN_PROGRESS;
            case IN_PROGRESS -> requested == TaskStatus.SUBMITTED;
            case RETURNED    -> requested == TaskStatus.SUBMITTED || requested == TaskStatus.IN_PROGRESS;
            case REJECTED    -> requested == TaskStatus.IN_PROGRESS;
            case OVERDUE     -> requested == TaskStatus.IN_PROGRESS;
            case SUBMITTED   -> throw new BadRequestException("Task is already submitted and awaiting review.");
            case APPROVED    -> throw new BadRequestException("Task is already approved.");
            case DELEGATED   -> requested == TaskStatus.IN_PROGRESS;
        };
        if (!valid) throw new InvalidStatusTransitionException(current.name(), requested.name());
    }

    private void validatePaginationParams(int page, int size) {
        if (page < 0) throw new BadRequestException("Page number cannot be negative.");
        if (size < 1 || size > 100) throw new BadRequestException("Page size must be between 1 and 100.");
    }

    private Map<String, Long> getGlobalStatusCounts() {
        Map<String, Long> counts = new HashMap<>();
        try {
            for (Object[] row : taskRepository.countGroupedByStatus())
                counts.put(row[0].toString(), (Long) row[1]);
        } catch (Exception ex) {
            log.error("Failed to load status counts: {}", ex.getMessage());
        }
        return counts;
    }

    private Map<String, Long> getStatusCountsForUser(Long userId) {
        Map<String, Long> counts = new HashMap<>();
        try {
            for (Object[] row : taskRepository.countGroupedByStatusForUser(userId))
                counts.put(row[0].toString(), (Long) row[1]);
        } catch (Exception ex) {
            log.error("Failed to load user status counts: {}", ex.getMessage());
        }
        return counts;
    }

    private PagedTaskResponse buildPagedResponse(Page<Task> taskPage, Map<String, Long> statusCounts) {
        PagedTaskResponse response = new PagedTaskResponse();
        response.setTasks(taskPage.getContent().stream().map(this::mapToTaskResponse).collect(Collectors.toList()));
        response.setCurrentPage(taskPage.getNumber());
        response.setTotalPages(taskPage.getTotalPages());
        response.setTotalItems(taskPage.getTotalElements());
        response.setPageSize(taskPage.getSize());
        response.setFirst(taskPage.isFirst());
        response.setLast(taskPage.isLast());
        response.setHasNext(taskPage.hasNext());
        response.setHasPrevious(taskPage.hasPrevious());
        response.setStatusCounts(statusCounts);
        return response;
    }

    TaskResponse mapToTaskResponse(Task task) {
        TaskResponse r = new TaskResponse();
        r.setId(task.getId());
        r.setTitle(task.getTitle());
        r.setDescription(task.getDescription());
        r.setPriority(task.getPriority());
        r.setStatus(task.getStatus());
        r.setDeadline(task.getDeadline());
        r.setOverdue(task.getDeadline() != null
                && task.getDeadline().isBefore(LocalDate.now())
                && task.getStatus() != TaskStatus.APPROVED);

        if (task.getAssignedTo() != null) {
            r.setAssignedToId(task.getAssignedTo().getId());
            r.setAssignedToName(task.getAssignedTo().getFullName());
        }
        if (task.getCreatedBy() != null) {
            r.setCreatedById(task.getCreatedBy().getId());
            r.setCreatedByName(task.getCreatedBy().getFullName());
        }

        // ── Resolution info ───────────────────────────────────────────────────
        if (task.getResolvedBy() != null) {
            User rb = task.getResolvedBy();
            r.setResolvedById(rb.getId());
            r.setResolvedByName(rb.getFullName());
            r.setResolvedByDesignation(rb.getDesignation());
            r.setResolvedByDepartment(rb.getDepartment());
            r.setResolvedAt(task.getResolvedAt());
        }

        // ── Approval info ─────────────────────────────────────────────────────
        if (task.getApprovedBy() != null) {
            r.setApprovedById(task.getApprovedBy().getId());
            r.setApprovedByName(task.getApprovedBy().getFullName());
            r.setApprovedAt(task.getApprovedAt());
            r.setReviewNote(task.getReviewNote());
        }

        r.setCreatedAt(task.getCreatedAt());
        r.setUpdatedAt(task.getUpdatedAt());

        // Members
        try {
            List<TaskMember> members = memberRepository.findByTaskIdOrderByAddedAtAsc(task.getId());
            r.setMembers(members.stream()
                    .map(tm -> mapToMemberResponse(tm, task.getAssignedTo()))
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("Failed to load members for task {}: {}", task.getId(), e.getMessage());
            r.setMembers(List.of());
        }

        // Comments (legacy)
        try {
            r.setComments(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())
                    .stream().map(this::mapToCommentResponse).collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("Failed to load comments for task {}: {}", task.getId(), e.getMessage());
            r.setComments(List.of());
        }

        // Chat message count
        try {
            r.setChatMessageCount(chatRepository.countByTaskId(task.getId()));
        } catch (Exception e) {
            r.setChatMessageCount(0);
        }

        return r;
    }

    private TaskMemberResponse mapToMemberResponse(TaskMember tm, User primaryAssignee) {
        TaskMemberResponse r = new TaskMemberResponse();
        User u = tm.getUser();
        r.setUserId(u.getId());
        r.setFullName(u.getFullName());
        r.setEmployeeCode(u.getEmployeeCode());
        r.setDesignation(u.getDesignation());
        r.setDepartment(u.getDepartment());
        r.setEmail(u.getEmail());
        r.setAddedAt(tm.getAddedAt());
        r.setPrimary(primaryAssignee != null && primaryAssignee.getId().equals(u.getId()));
        if (tm.getAddedBy() != null) {
            r.setAddedById(tm.getAddedBy().getId());
            r.setAddedByName(tm.getAddedBy().getFullName());
        }
        return r;
    }

    private ChatMessageResponse mapToChatMessage(TaskChatMessage msg, Long requestingUserId) {
        ChatMessageResponse r = new ChatMessageResponse();
        r.setId(msg.getId());
        r.setTaskId(msg.getTask().getId());
        r.setMessage(msg.getMessage());
        r.setMessageType(msg.getMessageType());
        r.setEdited(msg.isEdited());
        r.setCreatedAt(msg.getCreatedAt());
        r.setEditedAt(msg.getEditedAt());

        if (msg.getSender() != null) {
            User s = msg.getSender();
            r.setSenderId(s.getId());
            r.setSenderName("SYSTEM".equals(msg.getMessageType()) ? "System" : s.getFullName());
            r.setSenderRole(s.getRole() != null ? s.getRole().name() : "EMPLOYEE");
            String ini = Arrays.stream(s.getFullName().split(" "))
                    .filter(p -> !p.isEmpty())
                    .map(p -> String.valueOf(p.charAt(0)))
                    .limit(2)
                    .collect(Collectors.joining())
                    .toUpperCase();
            r.setSenderInitials(ini);
            r.setMine(requestingUserId != null && requestingUserId.equals(s.getId()));
        }

        if (msg.getFileName() != null) {
            r.setFileName(msg.getFileName());
            r.setFileSize(msg.getFileSize());
            r.setFileType(msg.getFileType());
            r.setFileDownloadUrl("/api/tasks/" + msg.getTask().getId()
                    + "/chat/files/" + msg.getId());
        }

        return r;
    }

    private CommentResponse mapToCommentResponse(TaskComment comment) {
        CommentResponse r = new CommentResponse();
        r.setId(comment.getId());
        r.setComment(comment.getComment());
        r.setCommentedById(comment.getCommentedBy().getId());
        r.setCommentedByName(comment.getCommentedBy().getFullName());
        r.setRole(comment.getCommentedBy().getRole().name());
        r.setCreatedAt(comment.getCreatedAt());
        return r;
    }

}