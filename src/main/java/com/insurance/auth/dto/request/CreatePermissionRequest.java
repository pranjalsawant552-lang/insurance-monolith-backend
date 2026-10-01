package com.insurance.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePermissionRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    private String code;

    @NotBlank
    @Size(min = 2, max = 50)
    private String module;

    @NotBlank
    @Size(min = 2, max = 255)
    private String description;
}
