package com.aiplatform.starter.mybatis.support;

import com.aiplatform.common.result.PageResult;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * Helper to adapt MyBatis-Plus IPage to the platform's common PageResult.
 */
public final class PageAdapter {

    private PageAdapter() {
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }
}
