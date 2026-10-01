package com.insurance.risk.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    @Builder.Default private ZonedDateTime timestamp = ZonedDateTime.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().success(true).data(data).message("Success").build();
    }
    public static <T> ApiResponse<T> success(T data, String msg) {
        return ApiResponse.<T>builder().success(true).data(data).message(msg).build();
    }
    public static <T> ApiResponse<T> error(String msg, String code) {
        return ApiResponse.<T>builder().success(false).message(msg).errorCode(code).build();
    }
}
