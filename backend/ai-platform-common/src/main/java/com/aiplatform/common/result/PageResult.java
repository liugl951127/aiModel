package com.aiplatform.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Generic page wrapper. Holds the post-paginated records. Conversion from
 * MyBatis-Plus IPage happens in the mybatis-starter helper
 * {@code com.aiplatform.starter.mybatis.support.PageAdapter}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long total;
    private Long current;
    private Long size;
    private List<T> records;

    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L, 1L, 10L, Collections.emptyList());
    }
}
