package com.aiplatform.starter.common.errorcode;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * CommonStarter 提供的通用全局异常处理器。与 web-starter 提供的
 * GlobalExceptionHandler 互补，bean 名不同（{@code commonExceptionHandler}），不冲突。</p>
 *
 * <h2>职责</h2>
 *
 * <h2>映射策略</h2>
 * <ul>
 *   <li>{@link BusinessException} → 业务错误码，HTTP 200（业务方按 code 自行处理）</li>
 *   <li>{@link MethodArgumentNotValidException} / {@link BindException} → 400 + 字段错误</li>
 *   <li>{@link NoHandlerFoundException} → 404</li>
 *   <li>其它 → 500 + traceId</li>
 * </ul>
 *
 * <p>返回的 {@code data} 字段是 {@link ErrorCode#toMap(ErrorCode)}，包含
 * {@code clientAction}，前端不需要解析中文。</p>
 */
@Slf4j
@RestControllerAdvice
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type =
        org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class CommonExceptionHandler {

    /**
     * 业务异常 — 业务方已指定错误码与 message。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusiness(BusinessException ex, HttpServletRequest req) {
        int code = ex.getCode() == null ? ResultCode.FAIL.getCode() : ex.getCode();
        log.warn("[BIZ] {} {} -> {}: {}",
                req.getMethod(), req.getRequestURI(), code, ex.getMessage());
        return ResponseEntity.ok(Result.fail(code, ex.getMessage()));
    }

    /**
     * @Valid 校验失败 — 聚合字段错误。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[VALID] {}", detail);
        return ResponseEntity.ok(Result.fail(ErrorCode.BAD_REQUEST.code, detail));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Object>> handleBind(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.ok(Result.fail(ErrorCode.BAD_REQUEST.code, detail));
    }

    /**
     * 路由不存在。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Object>> handleNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(ErrorCode.NOT_FOUND.code, ErrorCode.NOT_FOUND.message));
    }

    /**
     * 兜底 — 任何未识别异常都打 ERROR 日志，客户端只看到脱敏后的 message + traceId。
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Object>> handleAll(Throwable th, HttpServletRequest req) {
        log.error("[UNCAUGHT] {} {}", req.getMethod(), req.getRequestURI(), th);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.INTERNAL.code, th.getClass().getSimpleName() + ": " + th.getMessage()));
    }
}
