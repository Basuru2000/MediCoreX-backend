package com.medicorex.util;

import com.medicorex.dto.PageResponseDTO;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for creating PageResponseDTO instances
 */
public class PageResponseUtil {

    /**
     * Create PageResponseDTO from Spring Page
     */
    public static <T> PageResponseDTO<T> from(Page<T> page) {
        PageResponseDTO<T> response = new PageResponseDTO<>();
        response.setContent(page.getContent());
        response.setPage(page.getNumber());
        response.setTotalPages(page.getTotalPages());
        response.setTotalElements(page.getTotalElements());
        response.setLast(page.isLast());
        return response;
    }

    /**
     * Create PageResponseDTO from Spring Page with DTO conversion
     */
    public static <T, D> PageResponseDTO<D> from(Page<T> page, Function<T, D> converter) {
        List<D> dtos = page.getContent().stream()
                .map(converter)
                .collect(Collectors.toList());

        PageResponseDTO<D> response = new PageResponseDTO<>();
        response.setContent(dtos);
        response.setPage(page.getNumber());
        response.setTotalPages(page.getTotalPages());
        response.setTotalElements(page.getTotalElements());
        response.setLast(page.isLast());
        return response;
    }
}

// Usage example in your services:
// return PageResponseUtil.from(productPage, this::convertToDTO);