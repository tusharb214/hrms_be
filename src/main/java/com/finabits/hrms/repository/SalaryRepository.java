package com.finabits.hrms.repository;

import com.finabits.hrms.entity.Salary;
import com.finabits.hrms.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalaryRepository extends JpaRepository<Salary, Long> {
    Optional<Salary> findByUserAndMonthAndYear(User user, int month, int year);
    Page<Salary> findByUserOrderByYearDescMonthDesc(User user, Pageable pageable);
    boolean existsByUserAndMonthAndYear(User user, int month, int year);
}
