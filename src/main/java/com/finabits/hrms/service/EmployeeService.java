package com.finabits.hrms.service;

import com.finabits.hrms.dto.request.UpdateEmployeeRequest;
import com.finabits.hrms.dto.response.UserResponse;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Role;
import com.finabits.hrms.exception.DuplicateResourceException;
import com.finabits.hrms.exception.ResourceNotFoundException;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public Page<UserResponse> getAllEmployees(Pageable pageable) {
        return userRepository.findByRoleAndActiveTrue(Role.EMPLOYEE, pageable)
                .map(authService::mapToResponse);
    }

    public UserResponse getById(Long id) {
        User user = findUserById(id);
        return authService.mapToResponse(user);
    }

    public UserResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return authService.mapToResponse(user);
    }

    public UserResponse update(Long id, UpdateEmployeeRequest request) {
        User user = findUserById(id);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email already in use: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }
        if (request.getFullName() != null)      user.setFullName(request.getFullName());
        if (request.getPhone() != null)          user.setPhone(request.getPhone());
        if (request.getDepartment() != null)     user.setDepartment(request.getDepartment());
        if (request.getDesignation() != null)    user.setDesignation(request.getDesignation());
        if (request.getDateOfJoining() != null)  user.setDateOfJoining(request.getDateOfJoining());
        if (request.getDateOfBirth() != null)    user.setDateOfBirth(request.getDateOfBirth());
        if (request.getAddress() != null)        user.setAddress(request.getAddress());
        if (request.getMonthlySalary() != null)  user.setMonthlySalary(request.getMonthlySalary());
        if (request.getActive() != null)         user.setActive(request.getActive());
        if (request.getLaptopAssigned() != null)    user.setLaptopAssigned(request.getLaptopAssigned());

        userRepository.save(user);
        log.info("Employee updated: {}", user.getEmail());
        return authService.mapToResponse(user);
    }

    public void delete(Long id) {
        User user = findUserById(id);
        user.setActive(false);   // soft delete
        userRepository.save(user);
        log.info("Employee soft-deleted: {}", user.getEmail());
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
    }
}