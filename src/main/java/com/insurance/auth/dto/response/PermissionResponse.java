package com.insurance.auth.dto.response;

import com.insurance.auth.entity.Permission;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PermissionResponse {
    private UUID id;
    private String code;
    private String module;
    private String description;
    private boolean systemPermission;

    public static PermissionResponse from(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .module(permission.getModule())
                .description(permission.getDescription())
                .systemPermission(permission.isSystemPermission())
                .build();
    }
}
