package com.aiplatform.files.mapper;

import com.aiplatform.files.entity.FileObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for {@link FileObject}. Inherits CRUD operations
 * from {@link BaseMapper}; add custom SQL here when needed.
 */
@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {
}
