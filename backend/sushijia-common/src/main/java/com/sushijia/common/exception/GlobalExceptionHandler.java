package com.sushijia.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushijia.common.response.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 全局异常处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return json(HttpStatus.OK, R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public void handleMaxUploadSize(MaxUploadSizeExceededException e,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        String message = "上传文件过大，请压缩后再试，或选择 120MB 以内的文件";
        log.warn("上传文件超过限制: uri={}, message={}", request.getRequestURI(), e.getMessage());
        writeErrorResponse(request, response, HttpStatus.PAYLOAD_TOO_LARGE, 413, message);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e, HttpServletResponse response) {
        log.warn("异步请求超时");
        if (!response.isCommitted()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public void handleNotAcceptable(HttpMediaTypeNotAcceptableException e,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        log.warn("请求 Accept 头不兼容: {}", e.getMessage());
        writeErrorResponse(request, response, HttpStatus.NOT_ACCEPTABLE, 406, "请求返回类型不可接受");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
        log.warn("数据约束异常: {}", cause);

        String lower = cause.toLowerCase();
        String message;
        if (lower.contains("foreign key") || lower.contains("constraint fails") || lower.contains("cannot delete")) {
            message = "数据已被历史记录引用，不能直接删除";
        } else if (lower.contains("duplicate") || lower.contains("uk_") || lower.contains("unique")) {
            message = "数据已存在，请检查名称、手机号或唯一字段";
        } else if (lower.contains("cannot be null") || lower.contains("doesn't have a default value")) {
            message = "保存失败：必填字段缺失，请补充完整后重试";
        } else {
            message = "保存失败：数据不符合数据库约束，请检查填写内容";
        }
        return json(HttpStatus.OK, R.fail(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnknown(Exception e) {
        log.error("未知异常", e);
        return json(HttpStatus.INTERNAL_SERVER_ERROR, R.fail(500, "服务器内部错误"));
    }

    private ResponseEntity<R<Void>> json(HttpStatus status, R<Void> body) {
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
    }

    private void writeErrorResponse(HttpServletRequest request,
                                    HttpServletResponse response,
                                    HttpStatus status,
                                    int code,
                                    String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String accept = request.getHeader("Accept");
        boolean eventStream = accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (eventStream) {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.getWriter().write("event: error\n");
            response.getWriter().write("data: " + objectMapper.writeValueAsString(Map.of("message", message)) + "\n\n");
            response.getWriter().write("event: done\n");
            response.getWriter().write("data: " + objectMapper.writeValueAsString(Map.of("content", "", "error", message)) + "\n\n");
        } else {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(R.fail(code, message)));
        }
        response.flushBuffer();
    }
}
