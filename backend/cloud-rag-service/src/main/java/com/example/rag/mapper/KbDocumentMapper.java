package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper
 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {
}
