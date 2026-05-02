package com.sitegenius.hrms.repository;

import com.sitegenius.hrms.entity.User;
import com.sitegenius.hrms.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRole(Role role);
    Page<User> findByRole(Role role, Pageable pageable);
    Page<User> findByRoleAndActiveTrue(Role role, Pageable pageable);

    List<User> findByActiveTrue();
    List<User> findByRoleAndActiveTrue(String role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.active = true")
    long countActiveByRole(Role role);

    @Query("SELECT u.id FROM User u WHERE u.active = true")
    List<Long> findAllActiveUserIds();

    @Query("SELECT u.id FROM User u WHERE u.role = 'ADMIN' AND u.active = true")
    List<Long> findAllActiveAdminIds();

}
