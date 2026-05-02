package com.chocwell.jy.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录成功响应
 */
@Data
@Builder
public class LoginResponse {
    private String  token;
    private Long    userId;
    private String  phone;
    private String  openid;
    private String  nickName;
    private String  avatarUrl;
}
