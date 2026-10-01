package com.insurance.customer.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateCustomerRequest {

    @NotBlank @Size(min=2, max=100) private String firstName;
    @NotBlank @Size(min=2, max=100) private String lastName;
    @NotNull @Past(message="Date of birth must be in the past") private LocalDate dateOfBirth;
    @NotBlank private String gender;
    private String maritalStatus;
    private String occupation;
    private BigDecimal annualIncome;

    @NotBlank @Email private String email;
    @NotBlank @Pattern(regexp="^\\+?[1-9]\\d{9,14}$") private String mobileNumber;
    private String alternateMobile;

    private String panNumber;
    private String aadhaarNumber;
    private String passportNumber;

    private String bloodGroup;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private Boolean smoker;
    private String preExistingConditions;

    @NotEmpty(message="At least one address is required")
    private List<AddressRequest> addresses;

    @Data
    public static class AddressRequest {
        @NotBlank private String addressType;
        @NotBlank private String addressLine1;
        private String addressLine2;
        @NotBlank private String city;
        @NotBlank private String state;
        @NotBlank @Pattern(regexp="\\d{6}") private String pincode;
        private String country = "India";
        private boolean primary;
    }
}
