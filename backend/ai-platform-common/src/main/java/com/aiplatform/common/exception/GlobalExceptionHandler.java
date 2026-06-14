package com.aiplatform.common.exception;

import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * Global REST exception handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("[BUSINESS] {} -> {}", request.getRequestURI(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.VALIDATE_FAILED, msg);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException ex) {
        String msg = ex.getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.VALIDATE_FAILED, msg);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleParam(MissingServletRequestParameterException ex) {
        return Result.fail(ResultCode.BAD_REQUEST, "缺少参数: " + ex.getParameterName());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleBody(HttpMessageNotReadableException ex) {
        return Result.fail(ResultCode.BAD_REQUEST, "请求体格式错误");
    }

    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return Result.fail(ResultCode.METHOD_NOT_ALLOWED);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handle404(NoHandlerFoundException ex) {
        return Result.fail(ResultCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleAll(Exception ex, HttpServletRequest request) {
        log.error("[SYSTEM] {} -> {}", request.getRequestURI(), ex.getMessage(), ex);
        return Result.fail(ResultCode.FAIL.getCode(), "系统异常: " + ex.getMessage());
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + " " + fe.getDefaultMessage();
    }
}
