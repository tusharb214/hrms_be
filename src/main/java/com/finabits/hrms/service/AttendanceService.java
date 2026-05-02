package com.sitegenius.hrms.service;

import com.sitegenius.hrms.dto.request.AttendanceRequest;
import com.sitegenius.hrms.dto.response.AttendanceDayResponse;
import com.sitegenius.hrms.dto.response.AttendanceResponse;
import com.sitegenius.hrms.entity.Attendance;
import com.sitegenius.hrms.entity.Leave;
import com.sitegenius.hrms.entity.User;
import com.sitegenius.hrms.enums.AttendanceStatus;
import com.sitegenius.hrms.exception.BadRequestException;
import com.sitegenius.hrms.exception.DuplicateResourceException;
import com.sitegenius.hrms.exception.ResourceNotFoundException;
import com.sitegenius.hrms.repository.AttendanceRepository;
import com.sitegenius.hrms.repository.LeaveRepository;
import com.sitegenius.hrms.repository.UserRepository;
import com.sitegenius.hrms.repository.WorkSummaryRepository;
import com.sitegenius.hrms.util.WorkingDayUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AttendanceService
 *
 * Organisation policy: Monday–Saturday are working days (6-day work week).
 * Sunday is the ONLY non-working day.
 *
 * ── Half-day leave integration ──────────────────────────────────────────────
 *
 *  Configured slots:  slot[0] = MORNING (e.g. 10:00)   slot[1] = AFTERNOON (e.g. 14:00)
 *
 *  MORNING half-day leave (halfDaySlot = "MORNING") approved:
 *    • slot[0] is ON LEAVE → sequence enforcement is bypassed for slot[1]
 *    • Employee checks in only for slot[1] (afternoon)
 *    • Checkout at any time → PRESENT  (morning leave + afternoon work = full day)
 *    • History: slot[0] shows "ON_LEAVE", dayStatus = PRESENT
 *
 *  AFTERNOON half-day leave (halfDaySlot = "AFTERNOON") approved:
 *    • Employee checks in for slot[0] normally (morning)
 *    • Employee does NOT check in for slot[1] — on leave
 *    • Checkout before 4 PM is allowed → PRESENT  (morning work + afternoon leave = full day)
 *    • History: slot[1] shows "ON_LEAVE", dayStatus = PRESENT
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceRepository  attendanceRepository;
    private final UserRepository        userRepository;
    private final SystemSettingService  settingService;
    private final WorkSummaryRepository workSummaryRepository;
    private final LeaveRepository       leaveRepository;   // ← needed for half-day leave lookup

    private static final DateTimeFormatter TIME_FMT     = DateTimeFormatter.ofPattern("HH:mm");
    private static final int               WINDOW_MINS  = 5;
    private static final LocalTime         FULL_DAY_CUT = LocalTime.of(16, 0); // 4 PM

    // ── Check In ──────────────────────────────────────────────────────────────
    public AttendanceResponse checkIn(AttendanceRequest request) {
        User      user  = getCurrentUser();
        LocalDate today = LocalDate.now();

        // Block check-in if already checked out today
        if (attendanceRepository.hasCheckedOut(user, today))
            throw new BadRequestException("You have already checked out for today. No more check-ins allowed.");

        List<String> validSlots;
        try { validSlots = settingService.getCheckinTimes(); }
        catch (Exception e) { throw new BadRequestException("Check-in slots not configured."); }
        if (validSlots.isEmpty()) throw new BadRequestException("No check-in slots configured.");
        if (!validSlots.contains(request.getSlotLabel()))
            throw new BadRequestException("Invalid slot: " + request.getSlotLabel());

        // ── Sequence enforcement ──────────────────────────────────────────────
        // Normally slot[N] requires slot[N-1] to have been checked in first.
        //
        // EXCEPTION: if slot[N-1] is the MORNING slot (slot[0]) AND the employee
        //            has an approved MORNING half-day leave today, bypass the check.
        //            The employee is on leave for that slot — they start from slot[1].
        int slotIndex = validSlots.indexOf(request.getSlotLabel());
        if (slotIndex > 0) {
            String prevSlot = validSlots.get(slotIndex - 1);
            boolean prevAlreadyCheckedIn =
                    attendanceRepository.existsByUserAndDateAndSlotLabel(user, today, prevSlot);

            if (!prevAlreadyCheckedIn) {
                Optional<Leave> halfLeave = getApprovedHalfDayLeave(user, today);
                boolean prevCoveredByMorningLeave =
                        halfLeave.isPresent()
                                && "MORNING".equalsIgnoreCase(halfLeave.get().getHalfDaySlot())
                                && isMorningSlot(prevSlot, validSlots);

                if (!prevCoveredByMorningLeave) {
                    throw new BadRequestException(
                            "You must check in for " + prevSlot + " before checking in for "
                                    + request.getSlotLabel() + ".");
                }
                log.info("Sequence bypass: {} | {} covered by approved MORNING half-day leave",
                        user.getEmail(), prevSlot);
            }
        }

        // ── Time window check ─────────────────────────────────────────────────
        int windowMins;
        try { windowMins = settingService.getIntValue("checkin_window_minutes"); }
        catch (Exception e) { windowMins = WINDOW_MINS; }

        LocalTime now       = LocalTime.now();
        LocalTime slotTime  = LocalTime.parse(request.getSlotLabel(), TIME_FMT);
        LocalTime windowEnd = slotTime.plusMinutes(windowMins);

        if (now.isBefore(slotTime)) {
            long minsLeft = Duration.between(now, slotTime).toMinutes() + 1;
            throw new BadRequestException("Slot not open yet. Opens in " + minsLeft + " minute(s).");
        }
        if (!now.isBefore(windowEnd))
            throw new BadRequestException("Slot " + request.getSlotLabel() + " closed. Window was "
                    + slotTime.format(TIME_FMT) + "–" + windowEnd.format(TIME_FMT) + ".");

        if (attendanceRepository.existsByUserAndDateAndSlotLabel(user, today, request.getSlotLabel()))
            throw new DuplicateResourceException("Already checked in for slot: " + request.getSlotLabel());

        Attendance attendance = Attendance.builder()
                .user(user).date(today)
                .slotLabel(request.getSlotLabel())
                .checkInTime(now)
                .workFromHome(request.isWorkFromHome())
                .build();

        attendanceRepository.save(attendance);
        log.info("Check-in: {} | slot: {} | time: {}", user.getEmail(), request.getSlotLabel(), now);
        return mapToResponse(attendance);
    }

    // ── Check Out ─────────────────────────────────────────────────────────────
    public AttendanceResponse checkOut() {
        User      user  = getCurrentUser();
        LocalDate today = LocalDate.now();

        List<String> slots;
        try { slots = settingService.getCheckinTimes(); }
        catch (Exception e) { slots = Collections.emptyList(); }
        if (slots.isEmpty()) throw new BadRequestException("No slots configured.");

        Optional<Leave> halfLeave         = getApprovedHalfDayLeave(user, today);
        boolean         hasMorningLeave   = halfLeave.isPresent()
                && "MORNING".equalsIgnoreCase(halfLeave.get().getHalfDaySlot());
        boolean         hasAfternoonLeave = halfLeave.isPresent()
                && "AFTERNOON".equalsIgnoreCase(halfLeave.get().getHalfDaySlot());

        // The slot the employee must have checked in to before being allowed to checkout:
        //   • MORNING leave  → they start from slot[1], so require slot[1]
        //   • All other cases → require slot[0]
        String requiredSlot = (hasMorningLeave && slots.size() >= 2) ? slots.get(1) : slots.get(0);

        if (!attendanceRepository.existsByUserAndDateAndSlotLabel(user, today, requiredSlot))
            throw new BadRequestException("You must check in before checking out.");

        if (attendanceRepository.hasCheckedOut(user, today))
            throw new DuplicateResourceException("You have already checked out for today.");

        LocalTime        now      = LocalTime.now();
        List<Attendance> todayRecs = attendanceRepository.findByUserAndDateOrderByCheckInTime(user, today);
        LocalTime        firstIn  = todayRecs.get(0).getCheckInTime();

        BigDecimal hours = BigDecimal.valueOf(Duration.between(firstIn, now).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        // ── Final day status ──────────────────────────────────────────────────
        //
        //  If employee has an approved half-day leave (either half):
        //    → one half was on leave, the other half was worked = PRESENT
        //    → no 4 PM rule applies; any checkout time = PRESENT
        //
        //  Otherwise: both slots + checkout after 4 PM = PRESENT, else HALF_DAY
        AttendanceStatus finalStatus;
        String           statusNote;

        if (hasMorningLeave || hasAfternoonLeave) {
            finalStatus = AttendanceStatus.PRESENT;
            statusNote  = hasAfternoonLeave
                    ? "Full Day — morning worked + approved AFTERNOON leave"
                    : "Full Day — approved MORNING leave + afternoon worked";
        } else {
            boolean hasMidSlot = slots.size() >= 2
                    && attendanceRepository.existsByUserAndDateAndSlotLabel(user, today, slots.get(1));
            if (hasMidSlot && now.isAfter(FULL_DAY_CUT)) {
                finalStatus = AttendanceStatus.PRESENT;
                statusNote  = "Full Day — checked in at mid-slot, checked out after 4 PM";
            } else {
                finalStatus = AttendanceStatus.HALF_DAY;
                statusNote  = !hasMidSlot
                        ? "Half Day — no mid-slot check-in (missed " + (slots.size() >= 2 ? slots.get(1) : "2 PM") + " slot)"
                        : "Half Day — checked out before 4 PM (" + now.format(TIME_FMT) + ")";
            }
        }

        // Update all records for today with checkout info + final status
        for (Attendance rec : todayRecs) {
            rec.setCheckoutTime(now);
            rec.setTotalHours(hours);
            rec.setCheckedOut(true);
            rec.setStatus(finalStatus);
            attendanceRepository.save(rec);
        }

        log.info("Check-out: {} | time: {} | hours: {} | status: {}", user.getEmail(), now, hours, finalStatus);
        return mapToResponse(todayRecs.get(0));
    }

    // ── Checkout status for employee dashboard ────────────────────────────────
    public Map<String, Object> getCheckoutStatus() {
        User user = getCurrentUser();
        LocalDate today = LocalDate.now();

        List<Attendance> recs  = attendanceRepository.findByUserAndDateOrderByCheckInTime(user, today);
        boolean checkedOut     = attendanceRepository.hasCheckedOut(user, today);
        boolean checkedIn      = !recs.isEmpty();

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("checkedIn",    checkedIn);
        status.put("checkedOut",   checkedOut);
        status.put("firstCheckIn", checkedIn  ? recs.get(0).getCheckInTime()  : null);
        status.put("checkoutTime", checkedOut ? recs.get(0).getCheckoutTime() : null);
        status.put("totalHours",   checkedOut ? recs.get(0).getTotalHours()   : null);
        status.put("dayStatus",    checkedOut ? recs.get(0).getStatus()       : null);

        if (checkedIn && !checkedOut) {
            long mins = Duration.between(recs.get(0).getCheckInTime(), LocalTime.now()).toMinutes();
            status.put("currentSessionHours",
                    BigDecimal.valueOf(mins).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        }
        return status;
    }

    // ── Today's records ───────────────────────────────────────────────────────
    public List<AttendanceResponse> getMyTodayAttendance() {
        User user = getCurrentUser();
        return attendanceRepository.findByUserAndDate(user, LocalDate.now())
                .stream().map(this::mapToResponse).toList();
    }

    public Page<AttendanceResponse> getMyHistory(Pageable pageable) {
        User user = getCurrentUser();
        return attendanceRepository.findByUserOrderByDateDesc(user, pageable).map(this::mapToResponse);
    }

    // ── Full slot breakdown per day ───────────────────────────────────────────
    /**
     * Returns a day-by-day attendance breakdown for the given range.
     *
     * SlotEntry.status values:
     *   "PRESENT"  — employee checked in for this slot
     *   "MISSED"   — slot has passed but employee did not check in (no leave)
     *   "ON_LEAVE" — slot is covered by an approved half-day leave
     *   "UPCOMING" — slot is in the future or window not yet open
     *
     * dayStatus values that changed:
     *   A day with an approved half-day leave where the opposite half was worked
     *   now returns "PRESENT" instead of "HALF_DAY" or "ABSENT".
     */
    public List<AttendanceDayResponse> getMyFullHistory(LocalDate from, LocalDate to) {
        User      user  = getCurrentUser();
        LocalDate today = LocalDate.now();

        List<String> configuredSlots;
        try { configuredSlots = settingService.getCheckinTimes(); }
        catch (Exception e) { configuredSlots = Collections.emptyList(); }

        int windowMins;
        try { windowMins = settingService.getIntValue("checkin_window_minutes"); }
        catch (Exception e) { windowMins = WINDOW_MINS; }

        // Group attendance records by date → slotLabel
        Map<LocalDate, Map<String, Attendance>> byDate = new HashMap<>();
        for (Attendance a : attendanceRepository.findByUserAndDateRange(user, from, to)) {
            byDate.computeIfAbsent(a.getDate(), k -> new HashMap<>()).put(a.getSlotLabel(), a);
        }

        // Load all approved half-day leaves in the range, keyed by the leave date.
        // (Half-day leaves always have startDate == endDate.)
        Map<LocalDate, Leave> halfDayLeaveByDate = new HashMap<>();
        leaveRepository.findApprovedHalfDayLeavesInRange(user, from, to)
                .forEach(l -> halfDayLeaveByDate.put(l.getStartDate(), l));

        List<AttendanceDayResponse> result = new ArrayList<>();
        LocalDate cur = from;

        while (!cur.isAfter(to)) {

            // Skip Sunday only — Saturday IS a working day (6-day work week)
            if (!WorkingDayUtils.isWorkingDay(cur)) {
                cur = cur.plusDays(1);
                continue;
            }

            boolean isFuture = cur.isAfter(today);
            boolean isToday  = cur.equals(today);

            Map<String, Attendance> dayRecs  = byDate.getOrDefault(cur, Collections.emptyMap());
            Leave                   halfLeave = halfDayLeaveByDate.get(cur); // null if none

            boolean hasMorningLeave   = halfLeave != null
                    && "MORNING".equalsIgnoreCase(halfLeave.getHalfDaySlot());
            boolean hasAfternoonLeave = halfLeave != null
                    && "AFTERNOON".equalsIgnoreCase(halfLeave.getHalfDaySlot());

            // Checkout info
            boolean   dayCheckedOut = dayRecs.values().stream().anyMatch(Attendance::isCheckedOut);
            LocalTime checkoutTime  = dayRecs.values().stream()
                    .filter(a -> a.getCheckoutTime() != null).map(Attendance::getCheckoutTime)
                    .findFirst().orElse(null);
            BigDecimal totalHours   = dayRecs.values().stream()
                    .filter(a -> a.getTotalHours() != null).map(Attendance::getTotalHours)
                    .findFirst().orElse(null);

            boolean summarySubmitted = workSummaryRepository
                    .findByUserAndSummaryDate(user, cur)
                    .map(ws -> ws.isSubmitted()).orElse(false);

            // ── Slot entries ──────────────────────────────────────────────────
            List<AttendanceDayResponse.SlotEntry> slotEntries = new ArrayList<>();
            int checkedInCount = 0, missedCount = 0;

            for (int si = 0; si < configuredSlots.size(); si++) {
                String     slot    = configuredSlots.get(si);
                Attendance rec     = dayRecs.get(slot);
                boolean    checked = rec != null;

                // Is this slot covered by an approved half-day leave?
                //   slot[0] (index 0) → MORNING leave covers it
                //   slot[1] (index 1) → AFTERNOON leave covers it
                boolean onLeave = (si == 0 && hasMorningLeave)
                        || (si == 1 && hasAfternoonLeave);

                String slotStatus;

                if (checked) {
                    slotStatus = "PRESENT";
                    checkedInCount++;
                } else if (onLeave) {
                    slotStatus = "ON_LEAVE";
                    // Do NOT increment missedCount — this is an approved leave slot
                } else if (isFuture) {
                    slotStatus = "UPCOMING";
                } else if (isToday) {
                    try {
                        LocalTime slotTime  = LocalTime.parse(slot, TIME_FMT);
                        LocalTime windowEnd = slotTime.plusMinutes(windowMins);
                        slotStatus = LocalTime.now().isAfter(windowEnd) ? "MISSED" : "UPCOMING";
                        if (slotStatus.equals("MISSED")) missedCount++;
                    } catch (Exception e) { slotStatus = "UPCOMING"; }
                } else {
                    slotStatus = "MISSED";
                    missedCount++;
                }

                slotEntries.add(AttendanceDayResponse.SlotEntry.builder()
                        .slotLabel(slot).checkedIn(checked)
                        .checkInTime(checked ? rec.getCheckInTime() : null)
                        .workFromHome(checked && rec.isWorkFromHome())
                        .status(slotStatus).build());
            }

            // ── Day status ────────────────────────────────────────────────────
            String     dayStatus;
            String     dayNote       = null;
            BigDecimal leaveDeducted = BigDecimal.ZERO;

            if (isFuture) {
                dayStatus = "FUTURE";

            } else if (isToday) {
                dayStatus = "IN_PROGRESS";

            } else if (!summarySubmitted && checkedInCount > 0) {
                dayStatus     = "ABSENT";
                dayNote       = "Work summary not submitted";
                leaveDeducted = BigDecimal.ONE;

            } else if (hasMorningLeave) {
                // MORNING leave: slot[0] = leave, slot[1] must be worked
                boolean afternoonWorked = configuredSlots.size() >= 2
                        && dayRecs.containsKey(configuredSlots.get(1))
                        && dayCheckedOut;
                if (afternoonWorked) {
                    dayStatus = "PRESENT";
                    dayNote   = "Morning on leave · afternoon worked"
                            + (totalHours != null ? " (" + totalHours + "h)" : "");
                } else {
                    // Leave approved for morning but employee also skipped afternoon
                    dayStatus     = "ABSENT";
                    dayNote       = "Morning on leave — no afternoon check-in recorded";
                    // leave was already deducted from leave balance when approved
                    leaveDeducted = BigDecimal.ZERO;
                }

            } else if (hasAfternoonLeave) {
                // AFTERNOON leave: slot[1] = leave, slot[0] must be worked
                boolean morningWorked = !dayRecs.isEmpty()
                        && dayRecs.containsKey(configuredSlots.get(0))
                        && dayCheckedOut;
                if (morningWorked) {
                    dayStatus = "PRESENT";
                    dayNote   = "Morning worked · afternoon on leave"
                            + (totalHours != null ? " (" + totalHours + "h)" : "");
                } else {
                    dayStatus     = "ABSENT";
                    dayNote       = "Afternoon on leave — no morning check-in recorded";
                    leaveDeducted = BigDecimal.ZERO;
                }

            } else if (checkedInCount == 0) {
                dayStatus     = "ABSENT";
                dayNote       = "No check-ins";
                leaveDeducted = BigDecimal.ONE;

            } else if (!dayCheckedOut) {
                boolean hasMidSlot = configuredSlots.size() >= 2
                        && dayRecs.containsKey(configuredSlots.get(1));
                if (hasMidSlot) {
                    dayStatus     = "HALF_DAY";
                    dayNote       = "No check-out recorded";
                    leaveDeducted = new BigDecimal("0.5");
                } else {
                    dayStatus     = "ABSENT";
                    dayNote       = "No mid-slot check-in and no check-out";
                    leaveDeducted = BigDecimal.ONE;
                }

            } else if (checkoutTime != null && checkoutTime.isBefore(FULL_DAY_CUT)) {
                dayStatus     = "HALF_DAY";
                dayNote       = "Worked " + (totalHours != null ? totalHours + "h" : "")
                        + " — checked out before 4 PM";
                leaveDeducted = new BigDecimal("0.5");

            } else {
                dayStatus     = "PRESENT";
                dayNote       = totalHours != null ? "Worked " + totalHours + " hours" : null;
                leaveDeducted = BigDecimal.ZERO;
            }

            result.add(AttendanceDayResponse.builder()
                    .date(cur).dayOfWeek(cur.getDayOfWeek().toString())
                    .slots(slotEntries)
                    .checkedIn(checkedInCount).missed(missedCount).total(configuredSlots.size())
                    .dayStatus(dayStatus).dayNote(dayNote)
                    .checkedOut(dayCheckedOut)
                    .checkoutTime(checkoutTime)
                    .totalHours(totalHours)
                    .leaveDeducted(leaveDeducted)
                    .summarySubmitted(summarySubmitted)
                    .build());

            cur = cur.plusDays(1);
        }

        Collections.reverse(result);
        return result;
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────
    public List<AttendanceResponse> getNotCheckedOutToday() {
        return attendanceRepository.findUsersCheckedInButNotOut(LocalDate.now())
                .stream().map(u -> {
                    List<Attendance> recs = attendanceRepository
                            .findByUserAndDateOrderByCheckInTime(u, LocalDate.now());
                    return recs.isEmpty() ? null : mapToResponse(recs.get(0));
                }).filter(Objects::nonNull).toList();
    }

    public Page<AttendanceResponse> getAllAttendance(Pageable pageable) {
        return attendanceRepository.findAll(pageable).map(this::mapToResponse);
    }

    public List<AttendanceResponse> getTodayAllAttendance() {
        return attendanceRepository.findByDate(LocalDate.now())
                .stream().map(this::mapToResponse).toList();
    }

    public List<AttendanceResponse> getAttendanceByUserAndRange(Long userId, LocalDate from, LocalDate to) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));
        return attendanceRepository.findByUserAndDateRange(user, from, to)
                .stream().map(this::mapToResponse).toList();
    }

    public List<String> getConfiguredSlots() {
        try { return settingService.getCheckinTimes(); }
        catch (Exception e) { return Collections.emptyList(); }
    }

    // ── Half-day leave helpers ────────────────────────────────────────────────

    /**
     * Looks up an approved, half-day leave for the given user on the given date.
     * Half-day leaves always have startDate == endDate so querying by startDate is sufficient.
     */
    private Optional<Leave> getApprovedHalfDayLeave(User user, LocalDate date) {
        return leaveRepository.findApprovedHalfDayLeaveForDate(user, date);
    }

    /**
     * Returns true if the slot is the first configured slot (= MORNING slot, index 0).
     */
    private boolean isMorningSlot(String slotLabel, List<String> slots) {
        return !slots.isEmpty() && slots.get(0).equals(slotLabel);
    }

    // ── Common helpers ────────────────────────────────────────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private AttendanceResponse mapToResponse(Attendance a) {
        return AttendanceResponse.builder()
                .id(a.getId()).userId(a.getUser().getId())
                .employeeName(a.getUser().getFullName())
                .date(a.getDate()).slotLabel(a.getSlotLabel())
                .checkInTime(a.getCheckInTime())
                .checkoutTime(a.getCheckoutTime())
                .totalHours(a.getTotalHours())
                .checkedOut(a.isCheckedOut())
                .status(a.getStatus())
                .workFromHome(a.isWorkFromHome())
                .salaryDeductible(a.isSalaryDeductible())
                .build();
    }
}