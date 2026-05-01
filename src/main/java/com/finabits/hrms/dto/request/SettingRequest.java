package com.finabits.hrms.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SettingRequest {

    @NotBlank(message = "Key is required")
    private String settingKey;

    @NotBlank(message = "Value is required")
    private String settingValue;

    private String description;
}
