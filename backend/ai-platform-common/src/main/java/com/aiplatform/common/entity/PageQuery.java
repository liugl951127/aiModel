package com.aiplatform.common.entity;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Pagination request.
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long current = 1L;
    private Long size = 10L;
    private String keyword;
    private String orderBy;
    private String orderDirection = "desc";

    public <T> Page<T> toPage() {
        return Page.of(this.current, this.size);
    }
}
