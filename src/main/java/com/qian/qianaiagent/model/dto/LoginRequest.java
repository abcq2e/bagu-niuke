package com.qian.qianaiagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求体
 */
@Data
public class LoginRequest {

    /** 用户名（必填） */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需在 4-20 之间")
    private String username;

    /** 密码（必填） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度至少 6 位")
    private String password;
}
