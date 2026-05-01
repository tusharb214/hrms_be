package com.finabits.hrms.controller;

import com.finabits.hrms.dto.response.ApiResponse;
import com.finabits.hrms.entity.SystemSetting;
import com.finabits.hrms.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Settings")
public class PublicSettingsController {

    private final SystemSettingService settingService;

    // Any logged-in user can read individual settings needed for UI
    @GetMapping("/{key}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get a single setting value by key")
    public ResponseEntity<ApiResponse<SystemSetting>> getSetting(@PathVariable String key) {
        try {
            String value = settingService.getValue(key);
            SystemSetting s = new SystemSetting();
            s.setSettingKey(key);
            s.setSettingValue(value);
            return ResponseEntity.ok(ApiResponse.success("Setting", s));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success("Setting not found",
                    null));
        }
    }
}