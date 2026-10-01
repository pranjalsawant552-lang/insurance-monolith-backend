package com.insurance.auth.dto.response;

import com.insurance.auth.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RoleResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private boolean systemRole;
    private boolean active;
    private List<String> permissionCodes;

    public static RoleResponse from(Role role, List<String> permissionCodes) {
        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .systemRole(role.isSystemRole())
                .active(role.isActive())
                .permissionCodes(permissionCodes)
                .build();
    }
}
