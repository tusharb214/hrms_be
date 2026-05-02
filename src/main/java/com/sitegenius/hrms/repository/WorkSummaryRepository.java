package com.sitegenius.hrms.repository;

import com.sitegenius.hrms.entity.User;
import com.sitegenius.hrms.entity.WorkSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkSummaryRepository extends JpaRepository<WorkSummary, Long> {

    Optional<WorkSummary> findByUserAndSummaryDate(User user, LocalDate date);
    boolean existsByUserAndSummaryDate(User user, LocalDate date);

    // Employee: own history only
    Page<WorkSummary> findByUserOrderBySummaryDateDesc(User user, Pageable pageable);

    // Employee: own history filtered by month+year
    @Query("SELECT ws FROM WorkSummary ws WHERE ws.user = :user " +
            "AND FUNCTION('MONTH', ws.summaryDate) = :month " +
            "AND FUNCTION('YEAR',  ws.summaryDate) = :year " +
            "ORDER BY ws.summaryDate DESC")
    Page<WorkSummary> findByUserAndMonthYear(User user, int month, int year, Pageable pageable);

    // Employee: own history filtered by exact date
    Page<WorkSummary> findByUserAndSummaryDateOrderBySummaryDateDesc(
            User user, LocalDate summaryDate, Pageable pageable);

    // Admin: specific employee history (no bulk)
    @Query("SELECT ws FROM WorkSummary ws WHERE ws.user.id = :userId " +
            "ORDER BY ws.summaryDate DESC")
    Page<WorkSummary> findByUserId(Long userId, Pageable pageable);

    // Admin: specific employee filtered by month+year
    @Query("SELECT ws FROM WorkSummary ws WHERE ws.user.id = :userId " +
            "AND FUNCTION('MONTH', ws.summaryDate) = :month " +
            "AND FUNCTION('YEAR',  ws.summaryDate) = :year " +
            "ORDER BY ws.summaryDate DESC")
    Page<WorkSummary> findByUserIdAndMonthYear(Long userId, int month, int year, Pageable pageable);

    // Admin: specific employee on exact date
    @Query("SELECT ws FROM WorkSummary ws WHERE ws.user.id = :userId " +
            "AND ws.summaryDate = :date ORDER BY ws.summaryDate DESC")
    Page<WorkSummary> findByUserIdAndDate(Long userId, LocalDate date, Pageable pageable);

    // Missing submissions
    @Query("SELECT u FROM User u WHERE u.role = 'EMPLOYEE' AND u.active = true " +
            "AND u.id NOT IN (SELECT ws.user.id FROM WorkSummary ws " +
            "WHERE ws.summaryDate = :date AND ws.submitted = true)")
    List<User> findEmployeesWithoutSummaryOnDate(LocalDate date);

    long countBySummaryDateAndSubmittedTrue(LocalDate date);
}