package com.aiplatform.starter.common.errorcode;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务错误码中央注册表。
 *
 * <p>取代散落在各 service 的 magic number / 字符串。所有错误信息、建议
 * 客户端处理（retry / refresh / contact）都集中在这里。</p>
 *
 * <h2>使用方式</h2>
 * <pre>
 *   if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
 * </pre>
 *
 * <p>每个错误码自带的 {@code clientAction} 字段（retry / refresh / contact / ignore）
 * 是给前端 banner / toast 用的，前端不需要再解析中文 message。</p>
 */
public enum ErrorCode {

    // 通用 1xxx
    SUCCESS(200, "ok", "ignore"),
    BAD_REQUEST(400, "请求参数错误", "ignore"),
    UNAUTHORIZED(401, "未登录或登录已过期", "refresh"),
    FORBIDDEN(403, "无访问权限", "contact"),
    NOT_FOUND(404, "资源不存在", "ignore"),
    CONFLICT(409, "资源冲突", "retry"),
    RATE_LIMITED(429, "请求过于频繁", "retry"),
    INTERNAL(500, "服务内部错误", "contact"),

    // 训练 3xxx
    TRAIN_CORRUPT_BUNDLE(3001, "训练产物损坏", "retry"),
    TRAIN_OOM(3002, "训练显存不足", "ignore"),
    TRAIN_CONVERGED(3003, "训练已收敛", "ignore"),
    TRAIN_NOT_ENOUGH_DATA(3004, "训练数据不足", "ignore"),
    GUARD_REJECTED(3005, "防幻觉闸门拒绝", "ignore"),

    // 知识库 4xxx
    KB_NO_BASE(4001, "知识库不存在", "ignore"),
    KB_DOC_TOO_LARGE(4002, "文档超过大小限制", "ignore"),
    KB_INDEX_FAIL(4003, "索引失败", "retry"),
    KB_QUERY_EMPTY(4004, "查询无结果", "ignore"),

    // 文件 5xxx
    FILE_EMPTY(5001, "上传文件为空", "ignore"),
    FILE_TOO_LARGE(5002, "文件超过大小限制", "ignore"),
    FILE_VIRUS(5003, "文件安全检查未通过", "contact"),
    FILE_NOT_FOUND(5004, "文件不存在", "ignore"),

    // Agent 6xxx
    AGENT_TOOL_MISSING(6001, "工具未注册", "ignore"),
    AGENT_LOOP_LIMIT(6002, "智能体循环超限", "ignore"),
    AGENT_LLM_FAIL(6003, "LLM 调用失败", "retry");

    public final int code;
    public final String message;
    public final String clientAction;

    ErrorCode(int code, String message, String clientAction) {
        this.code = code;
        this.message = message;
        this.clientAction = clientAction;
    }

    /**
     * 给前端 banner 用的提示映射。
     */
    public static Map<String, Object> toMap(ErrorCode ec) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", ec.code);
        m.put("message", ec.message);
        m.put("clientAction", ec.clientAction);
        return m;
    }
}
