package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDTO<T> {
    private List<T> content;
    private int page;
    private int totalPages;
    private long totalElements;
    private boolean last;

    // Optional: Add builder pattern
    public static <T> PageResponseDTO<T> of(List<T> content, int page, int totalPages, long totalElements, boolean last) {
        return new PageResponseDTO<>(content, page, totalPages, totalElements, last);
    }
}