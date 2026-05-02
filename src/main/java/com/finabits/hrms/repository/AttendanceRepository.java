package com.sitegenius.hrms.repository;

import com.sitegenius.hrms.entity.Attendance;
import com.sitegenius.hrms.entity.User;
import com.sitegenius.hrms.enums.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByUserAndDate(User user, LocalDate date);
    Optional<Attendance> findByUserAndDateAndSlotLabel(User user, LocalDate date, String slotLabel);
    Page<Attendance> findByUserOrderByDateDesc(User user, Pageable pageable);


    @Query("SELECT DISTINCT a.user FROM Attendance a WHERE a.date = :date AND a.status = :status")
    List<User> findDistinctUsersByDateAndStatus(LocalDate date, AttendanceStatus status);

    @Query("SELECT COUNT(DISTINCT a.user) FROM Attendance a WHERE a.date = :date")
    long countPresentToday(LocalDate date);

    @Query("SELECT COUNT(DISTINCT a.user) FROM Attendance a WHERE a.date = :date AND a.workFromHome = true")
    long countWfhToday(LocalDate date);

    @Query("SELECT a FROM Attendance a WHERE a.user = :user AND a.date BETWEEN :from AND :to ORDER BY a.date")
    List<Attendance> findByUserAndDateRange(User user, LocalDate from, LocalDate to);

    boolean existsByUserAndDateAndSlotLabel(User user, LocalDate date, String slotLabel);

    List<Attendance> findByDate(LocalDate date);

    // ── Salary calculation queries ────────────────────────────────────────────

    // Count distinct days where employee had at least one PRESENT check-in
    @Query("SELECT COUNT(DISTINCT a.date) FROM Attendance a " +
            "WHERE a.user = :user AND a.date BETWEEN :from AND :to " +
            "AND a.status = 'PRESENT'")
    int countPresentDates(User user, LocalDate from, LocalDate to);

    // Count distinct days where employee had HALF_DAY status
    @Query("SELECT COUNT(DISTINCT a.date) FROM Attendance a " +
            "WHERE a.user = :user AND a.date BETWEEN :from AND :to " +
            "AND a.status = 'HALF_DAY'")
    int countHalfDayDates(User user, LocalDate from, LocalDate to);

    // Count distinct days where employee was ABSENT and salary IS deductible
    @Query("SELECT COUNT(DISTINCT a.date) FROM Attendance a " +
            "WHERE a.user = :user AND a.date BETWEEN :from AND :to " +
            "AND a.status = 'ABSENT' AND a.salaryDeductible = true")
    int countSalaryDeductibleAbsentDays(User user, LocalDate from, LocalDate to);

    // Count distinct days where employee had HALF_DAY and salary IS deductible
    @Query("SELECT COUNT(DISTINCT a.date) FROM Attendance a " +
            "WHERE a.user = :user AND a.date BETWEEN :from AND :to " +
            "AND a.status = 'HALF_DAY' AND a.salaryDeductible = true")
    int countSalaryDeductibleHalfDays(User user, LocalDate from, LocalDate to);

    // ── Checkout queries ──────────────────────────────────────────────────────

    @Query("SELECT COUNT(a) > 0 FROM Attendance a WHERE a.user = :user AND a.date = :date AND a.checkedOut = true")
    boolean hasCheckedOut(User user, LocalDate date);

    @Query("SELECT a FROM Attendance a WHERE a.user = :user AND a.date = :date ORDER BY a.checkInTime ASC")
    List<Attendance> findByUserAndDateOrderByCheckInTime(User user, LocalDate date);

    @Query("""
        SELECT DISTINCT a.user FROM Attendance a
        WHERE a.date = :date
        AND a.user.id NOT IN (
            SELECT a2.user.id FROM Attendance a2
            WHERE a2.date = :date AND a2.checkedOut = true
        )
        """)
    List<User> findUsersCheckedInButNotOut(LocalDate date);

    @Query("""
        SELECT COUNT(DISTINCT a.user) FROM Attendance a
        WHERE a.date = :date
        AND a.user.id NOT IN (
            SELECT a2.user.id FROM Attendance a2
            WHERE a2.date = :date AND a2.checkedOut = true
        )
        """)
    long countNotCheckedOut(LocalDate date);

    @Query("SELECT a.user.id FROM Attendance a " +
            "WHERE a.date = :date " +
            "AND a.status IN ('PRESENT', 'HALF_DAY')")
    List<Long> findPresentUserIdsByDate(@Param("date") LocalDate date);
}