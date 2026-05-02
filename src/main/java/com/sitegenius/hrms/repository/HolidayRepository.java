package com.sitegenius.hrms.repository;

import com.sitegenius.hrms.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findByDateBetweenOrderByDate(LocalDate from, LocalDate to);
    boolean existsByDate(LocalDate date);
    List<Holiday> findByDateGreaterThanEqualOrderByDate(LocalDate from);
}
