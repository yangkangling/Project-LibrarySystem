package com.example.demo.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

// 把后端异常转换为前端可读响应。
@RestControllerAdvice
public class ApiExceptionHandler {
    // 业务代码主动抛出的 RuntimeException 统一按 400 返回。
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException exception) {
        // 优先使用业务异常中的提示，没有提示时使用默认文案。
        return error(HttpStatus.BAD_REQUEST, messageOrDefault(exception, "操作失败，请检查输入内容"));
    }

    // 请求参数缺失、类型错误或 JSON 格式错误，统一提示参数不正确。
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "请求参数不正确，请检查后再提交");
    }

    // 数据库唯一约束、外键约束等错误，统一转成用户能理解的提示。
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return error(HttpStatus.BAD_REQUEST, "数据不符合要求，可能存在重复或关联数据，请检查后再提交");
    }

    // 静态资源或接口地址不存在时返回 404。
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoHandlerFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "接口不存在，请重启后端或检查访问地址");
    }

    // 兜底异常处理，避免后端堆栈直接暴露给前端。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");
    }

    // 组装统一错误响应结构。
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        // LinkedHashMap 保持 code、message、error 的返回顺序。
        Map<String, Object> body = new LinkedHashMap<>();
        // HTTP 状态码数字。
        body.put("code", status.value());
        // 前端主要展示的错误信息。
        body.put("message", message);
        // 兼容旧前端读取 error 字段。
        body.put("error", message);
        // 按传入状态码返回响应。
        return ResponseEntity.status(status).body(body);
    }

    // 取异常信息；为空时使用默认文案。
    private String messageOrDefault(Exception exception, String defaultMessage) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? defaultMessage : message;
    }
}
