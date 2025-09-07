package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder  // ADD THIS
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDTO<T> {
    private List<T> content;
    private int page;
    private int size;  // ADD THIS FIELD (was missing)
    private int totalPages;
    private long totalElements;
    private boolean last;

    public static <T> PageResponseDTO<T> of(List<T> content, int page, int size,
                                            int totalPages, long totalElements, boolean last) {
        return new PageResponseDTO<>(content, page, size, totalPages, totalElements, last);
    }
}