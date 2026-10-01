package com.insurance.policy.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    @Builder.Default private ZonedDateTime timestamp = ZonedDateTime.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().success(true).data(data).message("Success").build();
    }
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder().success(true).data(data).message(message).build();
    }
    public static <T> ApiResponse<T> error(String message, String code) {
        return ApiResponse.<T>builder().success(false).message(message).errorCode(code).build();
    }
}
