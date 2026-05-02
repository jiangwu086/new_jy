package com.chocwell.jy.dto;

import lombok.Data;

/**
 * 手机号验证码登录请求体
 */
@Data
public class PhoneLoginRequest {
    /** 11 位手机号 */
    private String phone;
    /** 6 位短信验证码 */
    private String code;
}
