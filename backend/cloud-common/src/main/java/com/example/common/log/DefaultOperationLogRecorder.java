package com.example.common.log;

import com.example.common.log.mapper.OperationLogMapper;
import org.springframework.stereotype.Component;

/**
 * 基于 MyBatis-Plus 的接口调用日志记录器
 */
@Component
public class DefaultOperationLogRecorder implements OperationLogRecorder {

    private final OperationLogMapper operationLogMapper;

    public DefaultOperationLogRecorder(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void record(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }
}
