package com.li.community.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    @Builder.Default
    private List<T> records = Collections.emptyList();
    @Builder.Default
    private Long total = 0L;
    @Builder.Default
    private Long size = 10L;
    @Builder.Default
    private Long current = 1L;
    @Builder.Default
    private Long pages = 0L;

    public static <T> PageResult<T> of(IPage<T> page) {
        return PageResult.<T>builder()
                .records(page.getRecords())
                .total(page.getTotal())
                .size(page.getSize())
                .current(page.getCurrent())
                .pages(page.getPages())
                .build();
    }

    public static <T> PageResult<T> of(List<T> records, Long total, Long size, Long current) {
        long pages = (total + size - 1) / size;
        return PageResult.<T>builder()
                .records(records)
                .total(total)
                .size(size)
                .current(current)
                .pages(pages)
                .build();
    }
}
