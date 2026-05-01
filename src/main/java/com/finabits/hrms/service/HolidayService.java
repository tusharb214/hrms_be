package com.finabits.hrms.service;

import com.finabits.hrms.dto.request.HolidayRequest;
import com.finabits.hrms.entity.Holiday;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Role;
import com.finabits.hrms.exception.BadRequestException;
import com.finabits.hrms.exception.DuplicateResourceException;
import com.finabits.hrms.exception.ResourceNotFoundException;
import com.finabits.hrms.repository.HolidayRepository;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class HolidayService {

    private final HolidayRepository   holidayRepository;
    private final UserRepository      userRepository;
    private final EmailService        emailService;
    private final NotificationService notificationService;   // ← NEW

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");

    // ── Single holiday ────────────────────────────────────────────────────────
    public Holiday create(HolidayRequest request) {
        if (request.getDate() == null)
            throw new BadRequestException("Date is required for single holiday");
        if (holidayRepository.existsByDate(request.getDate()))
            throw new DuplicateResourceException("Holiday already exists on: " + request.getDate());

        Holiday holiday = Holiday.builder()
                .name(request.getName())
                .date(request.getDate())
                .description(request.getDescription())
                .optional(request.isOptional())
                .build();
        holidayRepository.save(holiday);

        // Email all active employees
        String dateLabel = request.getDate().format(DATE_FMT);
        blastHolidayEmail(request.getName(), dateLabel, request.getDescription(),
                request.isOptional(), false);

        // ── In-app notification → all users ──────────────────────────────────
        notificationService.holidayAdded(request.getName(), request.getDate().toString());

        log.info("Holiday created: {} on {}", request.getName(), request.getDate());
        return holiday;
    }

    // ── Range holiday ─────────────────────────────────────────────────────────
    public List<Holiday> createRange(HolidayRequest request) {
        LocalDate from = request.getFromDate();
        LocalDate to   = request.getToDate();

        if (from == null || to == null)
            throw new BadRequestException("Both fromDate and toDate are required for range holiday");
        if (from.isAfter(to))
            throw new BadRequestException("fromDate must be before or equal to toDate");
        if (from.plusDays(90).isBefore(to))
            throw new BadRequestException("Holiday range cannot exceed 90 days");

        List<Holiday> saved = new ArrayList<>();
        LocalDate cur = from;
        while (!cur.isAfter(to)) {
            java.time.DayOfWeek dow = cur.getDayOfWeek();
            if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                if (!holidayRepository.existsByDate(cur)) {
                    saved.add(holidayRepository.save(Holiday.builder()
                            .name(request.getName()).date(cur)
                            .description(request.getDescription())
                            .optional(request.isOptional()).build()));
                } else {
                    log.warn("Skipping duplicate holiday on {}", cur);
                }
            }
            cur = cur.plusDays(1);
        }

        // Send one email for the whole range (not per day)
        if (!saved.isEmpty()) {
            String rangeLabel = from.format(DATE_FMT) + " to " + to.format(DATE_FMT);
            blastHolidayEmail(request.getName(), rangeLabel,
                    request.getDescription(), request.isOptional(), false);

            // ── In-app notification (one for the range) ───────────────────────
            notificationService.holidayAdded(
                    request.getName() + " (" + saved.size() + " days)",
                    from + " → " + to);

        }

        log.info("Holiday range created: '{}' {} to {} — {} days",
                request.getName(), from, to, saved.size());
        return saved;
    }

    // ── Delete single ─────────────────────────────────────────────────────────
    public void delete(Long id) {
        Holiday h = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found: " + id));
        String name      = h.getName();
        String dateLabel = h.getDate().format(DATE_FMT);

        holidayRepository.delete(h);

        // Email cancellation
        blastHolidayCancelEmail(name, dateLabel);

        // ── In-app notification ───────────────────────────────────────────────
        notificationService.holidayRemoved(name);

        log.info("Holiday deleted: {} on {}", name, h.getDate());
    }

    // ── Delete range ──────────────────────────────────────────────────────────
    public int deleteByNameAndRange(String name, LocalDate from, LocalDate to) {
        List<Holiday> toDelete = holidayRepository.findByDateBetweenOrderByDate(from, to)
                .stream().filter(h -> h.getName().equalsIgnoreCase(name)).toList();
        holidayRepository.deleteAll(toDelete);

        if (!toDelete.isEmpty()) {
            String rangeLabel = from.format(DATE_FMT) + " to " + to.format(DATE_FMT);
            blastHolidayCancelEmail(name, rangeLabel);

            // ── In-app notification ───────────────────────────────────────────
            notificationService.holidayRemoved(name + " (" + toDelete.size() + " days)");
        }

        log.info("Deleted {} holidays named '{}' between {} and {}", toDelete.size(), name, from, to);
        return toDelete.size();
    }

    public List<Holiday> getUpcoming() {
        return holidayRepository.findByDateGreaterThanEqualOrderByDate(LocalDate.now());
    }

    public List<Holiday> getByRange(LocalDate from, LocalDate to) {
        return holidayRepository.findByDateBetweenOrderByDate(from, to);
    }

    // ── Email helpers ─────────────────────────────────────────────────────────
    private void blastHolidayEmail(String name, String dateLabel,
                                   String description, boolean optional, boolean cancelled) {
        List<User> employees = userRepository.findByRole(Role.EMPLOYEE)
                .stream().filter(User::isActive).toList();
        for (User emp : employees) {
            emailService.sendHolidayNotification(
                    emp.getEmail(), emp.getFullName(), name, dateLabel, optional);
        }
        log.info("Holiday email sent to {} employees for '{}'", employees.size(), name);
    }

    private void blastHolidayCancelEmail(String name, String dateLabel) {
        List<User> employees = userRepository.findByRole(Role.EMPLOYEE)
                .stream().filter(User::isActive).toList();
        for (User emp : employees) {
            emailService.sendHolidayCancelledEmail(emp.getEmail(), emp.getFullName(), name, dateLabel);
        }
        log.info("Holiday cancellation email sent to {} employees for '{}'", employees.size(), name);
    }
}