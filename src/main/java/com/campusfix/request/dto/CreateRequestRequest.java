package com.campusfix.request.dto;

import com.campusfix.request.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * There is no {@code studentId} field. The reporter is taken from the signed-in
 * user, so nobody can file a request in somebody else's name by editing the
 * body. The same goes for status and request number, which the server decides.
 */
public record CreateRequestRequest(

        @NotBlank(message = "Title is required")
        @Size(min = 5, max = 150, message = "Title must be between 5 and 150 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
        String description,

        @NotNull(message = "Please choose what kind of problem this is")
        Long categoryId,

        /** Optional: a campus-wide outage has no single place. */
        Long locationId,

        @NotNull(message = "Please choose a priority")
        Priority priority) {
}
