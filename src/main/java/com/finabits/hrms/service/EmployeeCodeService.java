package com.finabits.hrms.service;

import com.finabits.hrms.entity.User;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Manages the FIN-XXXX employee code system.
 *
 * On startup  → assigns codes to any existing users that don't have one yet.
 * On register → call {@link #assignCode(User)} before saving the new user.
 *
 * Format : FIN-1001, FIN-1002, FIN-1003 …
 * Base   : 1000  (so first employee gets FIN-1001)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeCodeService implements ApplicationRunner {

    private static final String PREFIX  = "FIN-";
    private static final int    BASE    = 1000;

    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Called once at application startup — backfills any users without a code
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> unassigned = userRepository.findAll().stream()
                .filter(u -> u.getEmployeeCode() == null || u.getEmployeeCode().isBlank())
                .sorted(Comparator.comparing(User::getId))   // oldest first → lowest code
                .toList();

        if (unassigned.isEmpty()) {
            log.info("EmployeeCodeService: all users already have employee codes.");
            return;
        }

        log.info("EmployeeCodeService: assigning codes to {} existing user(s)…", unassigned.size());

        for (User u : unassigned) {
            String code = generateNext();
            u.setEmployeeCode(code);
            userRepository.save(u);
            log.info("  Assigned {} → {}", u.getEmail(), code);
        }

        log.info("EmployeeCodeService: migration complete.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Call this just before saving a brand-new user in AuthService
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public void assignCode(User user) {
        if (user.getEmployeeCode() != null && !user.getEmployeeCode().isBlank()) return;
        user.setEmployeeCode(generateNext());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Determines the next code by finding the highest existing numeric suffix
    // ─────────────────────────────────────────────────────────────────────────
    private synchronized String generateNext() {
        int max = userRepository.findAll().stream()
                .map(User::getEmployeeCode)
                .filter(c -> c != null && c.startsWith(PREFIX))
                .mapToInt(c -> {
                    try { return Integer.parseInt(c.substring(PREFIX.length())); }
                    catch (NumberFormatException e) { return 0; }
                })
                .max()
                .orElse(BASE);          // if no codes exist yet, start at BASE so first = BASE+1

        return PREFIX + (max + 1);
    }
}