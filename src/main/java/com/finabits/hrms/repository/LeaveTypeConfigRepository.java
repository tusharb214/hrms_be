package com.sitegenius.hrms.repository;

import com.sitegenius.hrms.entity.LeaveTypeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LeaveTypeConfigRepository extends JpaRepository<LeaveTypeConfig, Long> {
    List<LeaveTypeConfig> findByActiveTrue();
    Optional<LeaveTypeConfig> findByTypeCode(String typeCode);
    boolean existsByTypeCode(String typeCode);
}