package com.finabits.hrms.repository;

import com.finabits.hrms.entity.SalaryRequest;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Salary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalaryRequestRepository extends JpaRepository<SalaryRequest, Long> {

    /** Employee: check if request already exists for this month/year */
    boolean existsByUserAndMonthAndYear(User user, int month, int year);

    /** Employee: own request history */
    Page<SalaryRequest> findByUserOrderByRequestedAtDesc(User user, Pageable pageable);

    /** Admin: all pending requests, newest first */
    Page<SalaryRequest> findByStatusOrderByRequestedAtAsc(Salary status, Pageable pageable);

    /** Admin: all requests across all employees */
    Page<SalaryRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Optional<SalaryRequest> findByUserAndMonthAndYear(User user, int month, int year);
}