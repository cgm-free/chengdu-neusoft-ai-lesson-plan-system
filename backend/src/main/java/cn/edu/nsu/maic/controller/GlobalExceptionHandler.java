package cn.edu.nsu.maic.controller;

import cn.edu.nsu.maic.dto.ApiResponse;
import cn.edu.nsu.maic.dto.CoursePlanDtos;
import cn.edu.nsu.maic.service.CoursePlanGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("参数校验失败");
        return ApiResponse.fail(message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequest(IllegalArgumentException exception) {
        log.warn("Bad request: {}", exception.getMessage());
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleState(IllegalStateException exception) {
        log.warn("Illegal state: {}", exception.getMessage());
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(CoursePlanGenerationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<CoursePlanDtos.GenerationError> handleCoursePlanGeneration(CoursePlanGenerationException exception) {
        log.warn("Course plan generation failed: {}", exception.getMessage());
        return ApiResponse.fail(exception.getMessage(), exception.error());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        log.warn("Uploaded file is too large", exception);
        return ApiResponse.fail("上传文件超过系统上限：单个文件不能超过 50MB，单次请求不能超过 100MB");
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMultipart(MultipartException exception) {
        log.warn("Multipart upload failed", exception);
        return ApiResponse.fail("文件上传失败，请确认文件未损坏，且单个文件不超过 50MB、单次请求不超过 100MB");
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIOException(IOException exception) {
        log.warn("File parsing failed", exception);
        return ApiResponse.fail("文件解析失败：" + readableMessage(exception));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        log.warn("Request rejected: {}", exception.getReason());
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? exception.getStatusCode().toString()
                : exception.getReason();
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode()).body(ApiResponse.fail(message));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("Unhandled server error", exception);
        return ApiResponse.fail("服务内部错误：" + readableMessage(exception));
    }

    private String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
