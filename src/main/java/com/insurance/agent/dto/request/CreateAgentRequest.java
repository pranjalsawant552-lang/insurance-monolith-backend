package com.insurance.agent.dto.request;

import com.insurance.agent.entity.AgentType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateAgentRequest {

    @NotNull(message = "Agent type is required")
    private AgentType agentType;

    @NotBlank @Size(min=2, max=100) private String firstName;
    @NotBlank @Size(min=2, max=100) private String lastName;

    @NotBlank @Email private String email;

    @NotBlank @Pattern(regexp="^\\+?[1-9]\\d{9,14}$")
    private String mobileNumber;

    private String panNumber;
    private String licenseNumber;
    private LocalDate licenseExpiry;
    private String qualification;
    private String region;

    // Optional commission rate overrides
    private BigDecimal commissionRateLife;
    private BigDecimal commissionRateHealth;
    private BigDecimal commissionRateMotor;

    // Bank details
    private String bankName;
    private String accountNumber;
    private String ifscCode;
    private String accountHolder;
}
