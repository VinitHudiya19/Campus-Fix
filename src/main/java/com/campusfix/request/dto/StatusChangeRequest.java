package com.campusfix.request.dto;

import jakarta.validation.constraints.Size;

/**
 * The note attached to a status change: what was done, why it was refused, or
 * why it is still broken.
 *
 * <p>There is no {@code @NotBlank} here, because whether a note is required
 * depends on the action — resolving needs one, starting work does not. That rule
 * lives on {@code StatusAction} where the rest of the workflow lives, rather
 * than being split between an annotation and a service check.
 */
public record StatusChangeRequest(

        @Size(max = 1000, message = "Note cannot be longer than 1000 characters")
        String note) {
}
