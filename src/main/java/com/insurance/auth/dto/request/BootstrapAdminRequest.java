package com.insurance.auth.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

/**
 * Closes the gap flagged in Phase 9: there was no way to create the
 * platform's first admin user without a direct SQL UPDATE. This endpoint
 * is a self-disabling one-time bootstrap — see AuthService.bootstrapAdmin
 * for the two guards that keep it from becoming a permanent backdoor.
 */
@Data
public class BootstrapAdminRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{9,14}$", message = "Invalid mobile number format")
    private String mobileNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 10, max = 128, message = "Password must be at least 10 characters")
    private String password;

    /** Optional — assigns the new admin to a specific company/tenant. Left null for a
     *  single-tenant deployment. */
    private UUID companyId;

    /** Must match the BOOTSTRAP_ADMIN_SECRET environment variable. This is the first
     *  guard: without it configured and matching, the endpoint refuses to run at all. */
    @NotBlank(message = "Bootstrap secret is required")
    private String secret;
}
