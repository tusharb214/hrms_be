package com.finabits.hrms.service;

import com.finabits.hrms.dto.response.DashboardResponse;
import com.finabits.hrms.entity.Attendance;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Role;
import com.finabits.hrms.repository.AttendanceRepository;
import com.finabits.hrms.repository.LeaveRepository;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository       userRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRepository      leaveRepository;


    // ── Admin stats ───────────────────────────────────────────────────────────
    public DashboardResponse getStats() {
        LocalDate today         = LocalDate.now();
        long totalEmployees     = userRepository.countActiveByRole(Role.EMPLOYEE);
        long presentToday       = attendanceRepository.countPresentToday(today);
        long onLeaveToday       = leaveRepository.countEmployeesOnLeaveToday(today);
        long wfhToday           = attendanceRepository.countWfhToday(today);
        long absentToday        = Math.max(0, totalEmployees - presentToday - onLeaveToday);
        return DashboardResponse.builder()
                .totalEmployees(totalEmployees).presentToday(presentToday)
                .absentToday(absentToday).onLeaveToday(onLeaveToday)
                .workFromHomeToday(wfhToday).build();
    }

    // ── Employee stats ────────────────────────────────────────────────────────
    public DashboardResponse getEmployeeDashboard() {
        LocalDate today     = LocalDate.now();
        long total          = userRepository.countActiveByRole(Role.EMPLOYEE);
        long present        = attendanceRepository.countPresentToday(today);
        long onLeave        = leaveRepository.countEmployeesOnLeaveToday(today);
        long wfh            = attendanceRepository.countWfhToday(today);
        return DashboardResponse.builder()
                .totalEmployees(total).presentToday(present)
                .absentToday(Math.max(0, total - present - onLeave))
                .onLeaveToday(onLeave).workFromHomeToday(wfh).build();
    }

    // ── Team today — one call, returns all 4 buckets ──────────────────────────
    public Map<String, Object> getTeamToday() {
        LocalDate today = LocalDate.now();

        // All active employees
        List<User> activeEmps = userRepository.findByRole(Role.EMPLOYEE)
                .stream().filter(User::isActive).toList();

        // Today's check-ins
        List<Attendance> todayAtts = attendanceRepository.findByDate(today);
        Set<Long> presentIds = todayAtts.stream()
                .map(a -> a.getUser().getId()).collect(Collectors.toSet());
        Set<Long> wfhIds = todayAtts.stream()
                .filter(Attendance::isWorkFromHome)
                .map(a -> a.getUser().getId()).collect(Collectors.toSet());

        // Today's approved leaves
        Set<Long> leaveIds = leaveRepository.findAllApprovedLeavesToday(today)
                .stream().map(l -> l.getUser().getId()).collect(Collectors.toSet());

        // Categorize into 4 buckets
        List<Map<String, String>> present      = new ArrayList<>();
        List<Map<String, String>> wfh          = new ArrayList<>();
        List<Map<String, String>> onLeave      = new ArrayList<>();
        List<Map<String, String>> notCheckedIn = new ArrayList<>();

        for (User emp : activeEmps) {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("id",          String.valueOf(emp.getId()));
            info.put("fullName",    emp.getFullName());
            info.put("designation", emp.getDesignation() != null ? emp.getDesignation() : "");
            info.put("department",  emp.getDepartment()  != null ? emp.getDepartment()  : "");

            if (leaveIds.contains(emp.getId()))        onLeave.add(info);
            else if (wfhIds.contains(emp.getId()))     { info.put("wfh","true"); wfh.add(info); }
            else if (presentIds.contains(emp.getId())) present.add(info);
            else                                       notCheckedIn.add(info);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("present",      present);
        result.put("wfh",          wfh);
        result.put("onLeave",      onLeave);
        result.put("notCheckedIn", notCheckedIn);
        return result;
    }

    // ── Not checked out today ─────────────────────────────────────────────────
    public List<Map<String, Object>> getNotCheckedOut() {
        LocalDate today = LocalDate.now();

        // Find attendances where employee checked in but has NOT checked out yet
        // Uses the 'checkedOut' boolean flag from the Attendance entity
        List<Attendance> notCheckedOut = attendanceRepository.findByDate(today)
                .stream()
                .filter(a -> a.getCheckInTime() != null && !a.isCheckedOut())
                .toList();

        // Deduplicate by userId — one entry per employee (they may have multiple slots)
        Map<Long, Map<String, Object>> byUser = new LinkedHashMap<>();
        for (Attendance a : notCheckedOut) {
            byUser.computeIfAbsent(a.getUser().getId(), id -> {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("userId",       a.getUser().getId());
                info.put("employeeName", a.getUser().getFullName());
                info.put("checkInTime",  a.getCheckInTime().toString());
                return info;
            });
        }
        return new ArrayList<>(byUser.values());
    }
}