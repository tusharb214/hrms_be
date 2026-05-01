package com.finabits.hrms.service;

import com.finabits.hrms.dto.request.SettingRequest;
import com.finabits.hrms.entity.SystemSetting;
import com.finabits.hrms.exception.ResourceNotFoundException;
import com.finabits.hrms.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingService {

    private final SystemSettingRepository settingRepository;

    // ── Setting Keys (constants so nothing is hardcoded elsewhere) ──────────
    public static final String LEAVES_PER_YEAR            = "leaves_per_year";
    public static final String WORKING_DAYS_PER_MONTH     = "working_days_per_month";
    public static final String CHECKIN_SLOTS_COUNT        = "checkin_slots_count";
    public static final String CHECKIN_TIMES              = "checkin_times";
    public static final String CHECKIN_REMINDER_MINUTES   = "checkin_reminder_minutes_before";
    public static final String SALARY_DEDUCTION_RULE      = "salary_deduction_rule";

    // ── Public Getters ────────────────────────────────────────────────────────

    public String getValue(String key) {
        return settingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .orElseThrow(() -> new ResourceNotFoundException("Setting not found: " + key));
    }

    public int getIntValue(String key) {
        return Integer.parseInt(getValue(key).trim());
    }

    public double getDoubleValue(String key) {
        return Double.parseDouble(getValue(key).trim());
    }

    /** Returns check-in times as a list, e.g. ["10:00","12:00","14:00"] */
    public List<String> getCheckinTimes() {
        String raw = getValue(CHECKIN_TIMES);
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public int getLeavesPerYear() {
        return getIntValue(LEAVES_PER_YEAR);
    }

    public int getWorkingDaysPerMonth() {
        return getIntValue(WORKING_DAYS_PER_MONTH);
    }

    public int getReminderMinutesBefore() {
        return getIntValue(CHECKIN_REMINDER_MINUTES);
    }

    // ── Admin CRUD ────────────────────────────────────────────────────────────

    public List<SystemSetting> getAllSettings() {
        return settingRepository.findAll();
    }

    public SystemSetting upsert(SettingRequest request) {
        SystemSetting setting = settingRepository.findBySettingKey(request.getSettingKey())
                .orElse(SystemSetting.builder().settingKey(request.getSettingKey()).build());
        setting.setSettingValue(request.getSettingValue());
        if (request.getDescription() != null) {
            setting.setDescription(request.getDescription());
        }
        log.info("Setting updated: {} = {}", request.getSettingKey(), request.getSettingValue());
        return settingRepository.save(setting);
    }

    public void delete(Long id) {
        SystemSetting setting = settingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Setting not found with id: " + id));
        settingRepository.delete(setting);
    }
}
