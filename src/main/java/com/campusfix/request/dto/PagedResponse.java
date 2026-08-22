package com.campusfix.request.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A page of results in a shape this project controls.
 *
 * <p>Spring's own {@code Page} serialises with a large, unstable set of fields
 * and warns about exactly this use. Defining the four things the frontend
 * actually needs means a Spring upgrade cannot change the API by surprise.
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
