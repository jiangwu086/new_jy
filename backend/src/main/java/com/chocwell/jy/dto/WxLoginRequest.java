package com.chocwell.jy.dto;

import lombok.Data;

/**
 * 微信登录请求体
 */
@Data
public class WxLoginRequest {
    /** wx.login() 返回的 code，后端用它换 openid + session_key */
    private String loginCode;
    /** open-type="getPhoneNumber" 返回的 code，后端用它换手机号 */
    private String phoneCode;
}
