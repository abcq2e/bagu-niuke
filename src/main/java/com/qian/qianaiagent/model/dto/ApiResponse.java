package com.qian.qianaiagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应格式
 * <p>
 * 所有接口返回的数据都用这个类包装，
 * 前端根据 code 判断成功与否，从 data 中取数据。
 * <p>
 * 示例：
 * <pre>
 * 成功: { "code": 0, "message": "ok", "data": {...} }
 * 失败: { "code": 1, "message": "用户名已存在", "data": null }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 状态码：0 = 成功，非 0 = 失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 返回数据（可为 null） */
    private T data;

    // ========== 静态工厂方法 ==========

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(0, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(1, message, null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
