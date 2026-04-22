package com.knibel.entraselfservice.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
    @NotBlank @Email String email,
    @NotBlank String companyName,
    @NotBlank String department,
    @NotBlank String firstName,
    @NotBlank String lastName
) {
}
