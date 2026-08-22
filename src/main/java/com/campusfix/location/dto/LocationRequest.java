package com.campusfix.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationRequest(

        @NotBlank(message = "Campus is required")
        @Size(max = 80, message = "Campus cannot be longer than 80 characters")
        String campus,

        @NotBlank(message = "Building is required")
        @Size(max = 80, message = "Building cannot be longer than 80 characters")
        String building,

        @Size(max = 40, message = "Floor cannot be longer than 40 characters")
        String floor,

        @Size(max = 40, message = "Room cannot be longer than 40 characters")
        String room) {
}
