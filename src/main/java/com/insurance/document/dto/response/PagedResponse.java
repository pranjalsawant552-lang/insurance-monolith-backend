package com.insurance.document.dto.response;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;
import java.util.List;

@Data @Builder
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <T> PagedResponse<T> of(Page<T> p) {
        return PagedResponse.<T>builder()
                .content(p.getContent()).page(p.getNumber()).pageSize(p.getSize())
                .totalElements(p.getTotalElements()).totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast()).build();
    }
}
