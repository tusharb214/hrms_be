package com.finabits.hrms.service;

import com.finabits.hrms.dto.request.LoginRequest;
import com.finabits.hrms.dto.request.RegisterRequest;
import com.finabits.hrms.dto.response.AuthResponse;
import com.finabits.hrms.dto.response.UserResponse;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.exception.DuplicateResourceException;
import com.finabits.hrms.repository.UserRepository;
import com.finabits.hrms.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService    userDetailsService;
    private final JwtUtil               jwtUtil;
    private final EmployeeCodeService   employeeCodeService;   // NEW

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtUtil.generateToken(userDetails);

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        log.info("User logged in: {}", user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .phone(request.getPhone())
                .department(request.getDepartment())
                .designation(request.getDesignation())
                .dateOfJoining(request.getDateOfJoining())
                .dateOfBirth(request.getDateOfBirth())
                .address(request.getAddress())
                .monthlySalary(request.getMonthlySalary())
                .active(true)
                .build();

        // ── Assign FIN-XXXX code before persisting ────────────────────────────
        employeeCodeService.assignCode(user);

        userRepository.save(user);
        log.info("New user registered: {} [{}] code={}", user.getEmail(), user.getRole(), user.getEmployeeCode());
        return mapToResponse(user);
    }

    public UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .employeeCode(user.getEmployeeCode())          // NEW
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .phone(user.getPhone())
                .department(user.getDepartment())
                .designation(user.getDesignation())
                .dateOfJoining(user.getDateOfJoining())
                .dateOfBirth(user.getDateOfBirth())
                .address(user.getAddress())
                .monthlySalary(user.getMonthlySalary())
                .active(user.isActive())
                .laptopAssigned(user.isLaptopAssigned())
                .createdAt(user.getCreatedAt())
                .build();
    }
}