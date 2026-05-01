package com.finabits.hrms.repository;

import com.finabits.hrms.entity.EmployeeDocument;
import com.finabits.hrms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
    List<EmployeeDocument> findByUser(User user);
    List<EmployeeDocument> findByUserAndDocumentType(User user, String documentType);
    List<EmployeeDocument> findByUserId(Long userId);
}