package com.finabits.hrms.seeder;

import com.finabits.hrms.entity.SystemSetting;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Role;
import com.finabits.hrms.repository.SystemSettingRepository;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final SystemSettingRepository settingRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default.email}")
    private String adminEmail;

    @Value("${app.admin.default.password}")
    private String adminPassword;

    @Value("${app.admin.default.name}")
    private String adminName;

    @Override
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedDefaultSettings();
    }

    // ── Default ADMIN ─────────────────────────────────────────────────────

    private void seedAdmin() {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Default admin already exists — skipping seed");
            return;
        }
        User admin = User.builder()
                .fullName(adminName)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .active(true)
                .build();
        userRepository.save(admin);
        log.info("✅ Default admin created: {}", adminEmail);
    }

    // ── Default System Settings ───────────────────────────────────────────

    private void seedDefaultSettings() {
        List<String[]> defaults = List.of(
                // key,  value,  description
                new String[]{ "leaves_per_year",               "18",
                        "Total paid leaves allowed per employee per year" },
                new String[]{ "working_days_per_month",        "26",
                        "Number of working days used for per-day salary calculation" },
                new String[]{ "checkin_slots_count",           "5",
                        "Number of check-in slots configured per day" },
                new String[]{ "checkin_times",                 "10:00,12:00,14:00,16:00,18:00",
                        "Comma-separated check-in slot times (HH:mm). Change anytime." },
                new String[]{ "checkin_reminder_minutes_before", "15",
                        "Minutes before a slot to send reminder email to employees" },
                new String[]{ "salary_deduction_rule",         "PER_DAY",
                        "Deduction rule: PER_DAY = monthlySalary / workingDays * unpaidLeaveDays" },
                new String[]{ "checkin_window_minutes",        "5",
                        "Minutes the check-in slot stays open e.g. 5 = slot open from 10:00 to 10:05" }
        );

        int seeded = 0;
        for (String[] entry : defaults) {
            String key = entry[0];
            if (!settingRepository.existsBySettingKey(key)) {
                settingRepository.save(SystemSetting.builder()
                        .settingKey(key)
                        .settingValue(entry[1])
                        .description(entry[2])
                        .build());
                seeded++;
            }
        }
        if (seeded > 0) {
            log.info("✅ Seeded {} default system settings", seeded);
        } else {
            log.info("System settings already present — skipping seed");
        }
    }
}