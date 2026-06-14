package com.aiplatform.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Standard result codes.
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    VALIDATE_FAILED(1001, "参数校验失败"),
    TENANT_INVALID(1002, "租户信息无效"),
    DATA_EXISTS(1003, "数据已存在"),
    DATA_NOT_FOUND(1004, "数据不存在"),

    USER_NOT_FOUND(2001, "用户不存在"),
    USER_PASSWORD_ERROR(2002, "用户名或密码错误"),
    USER_DISABLED(2003, "账号已被禁用"),
    TOKEN_EXPIRED(2004, "Token 已过期"),
    TOKEN_INVALID(2005, "Token 无效"),

    MODEL_NOT_FOUND(3001, "模型不存在"),
    MODEL_TRAINING(3002, "模型正在训练中"),
    MODEL_INFERENCE_ERROR(3003, "模型推理失败"),

    AGENT_EXECUTE_ERROR(4001, "智能体执行失败"),
    TOOL_NOT_FOUND(4002, "工具不存在"),
    KNOWLEDGE_INDEX_ERROR(5001, "知识库索引失败");

    private final Integer code;
    private final String message;
}
