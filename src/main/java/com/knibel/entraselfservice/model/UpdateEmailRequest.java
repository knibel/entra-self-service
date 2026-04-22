package com.knibel.entraselfservice.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateEmailRequest(
    @NotBlank @Email String currentEmail,
    @NotBlank @Email String newEmail
) {
}
