package com.finabits.hrms.service;

import com.finabits.hrms.dto.request.AnnouncementRequest;
import com.finabits.hrms.dto.response.AnnouncementResponse;
import com.finabits.hrms.entity.Announcement;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Role;
import com.finabits.hrms.exception.ResourceNotFoundException;
import com.finabits.hrms.repository.AnnouncementRepository;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository         userRepository;
    private final EmailService           emailService;
    private final NotificationService    notificationService;

    public AnnouncementResponse create(AnnouncementRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User   admin = userRepository.findByEmail(email).orElseThrow();

        Announcement announcement = Announcement.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .createdBy(admin)
                .active(true)
                .build();
        announcementRepository.save(announcement);

        // ── Blast email to all active employees ───────────────────────────────
        List<User> employees = userRepository.findByRole(Role.EMPLOYEE)
                .stream().filter(User::isActive).toList();
        for (User emp : employees) {
            emailService.sendAnnouncementEmail(
                    emp.getEmail(), emp.getFullName(),
                    request.getTitle(), request.getContent());
        }
        log.info("Announcement '{}' created + {} emails sent", request.getTitle(), employees.size());

        // ── In-app notification → all users (employees + admins) ─────────────
        notificationService.announcement(request.getTitle(), announcement.getId());

        return mapToResponse(announcement);
    }

    // ── Returns safe DTO page — no raw entity exposure ───────────────────────
    public Page<AnnouncementResponse> getAll(Pageable pageable) {
        return announcementRepository
                .findByActiveTrueOrderByCreatedAtDesc(pageable)
                .map(this::mapToResponse);
    }

    public void delete(Long id) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
        a.setActive(false);
        announcementRepository.save(a);
        log.info("Announcement '{}' deactivated", a.getTitle());
    }

    // ─────────────────────────────────────────────────────────────────────────
    private AnnouncementResponse mapToResponse(Announcement a) {
        return AnnouncementResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .content(a.getContent())
                .active(a.isActive())
                .createdAt(a.getCreatedAt())
                .createdById(a.getCreatedBy() != null ? a.getCreatedBy().getId() : null)
                .createdByName(a.getCreatedBy() != null ? a.getCreatedBy().getFullName() : null)
                .build();
    }
}