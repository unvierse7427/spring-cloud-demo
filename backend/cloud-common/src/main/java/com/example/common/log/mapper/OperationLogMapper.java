package com.example.common.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.common.log.OperationLog;

/**
 * 接口调用日志 Mapper，需在各服务的 @MapperScan 中包含本包
 */
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
