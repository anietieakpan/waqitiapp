package com.waqiti.common.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utilities for working with DTOs and entities
 */
public class DtoUtils {

    /**
     * Maps a page of entities to a page of DTOs
     */
    public static <E, D> Page<D> mapPage(Page<E> entityPage, Function<E, D> mapper) {
        List<D> dtos = entityPage.getContent().stream()
                .map(mapper)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, entityPage.getPageable(), entityPage.getTotalElements());
    }

    /**
     * Maps a list of entities to a page of DTOs
     */
    public static <E, D> Page<D> mapListToPage(List<E> entities, Pageable pageable, Function<E, D> mapper) {
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), entities.size());

        List<E> pageContent = entities.subList(start, end);
        List<D> dtos = pageContent.stream()
                .map(mapper)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, entities.size());
    }
}