package com.example.common.log;

/**
 * 接口调用日志落库接口。
 * 业务服务引入 MyBatis-Plus 后由 DefaultOperationLogRecorder 实现，
 * 未引入时过滤器只输出到应用日志，不影响主流程。
 */
public interface OperationLogRecorder {

    void record(OperationLog operationLog);
}
