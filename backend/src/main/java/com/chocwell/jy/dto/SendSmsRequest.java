package com.chocwell.jy.dto;

import lombok.Data;

@Data
public class SendSmsRequest {
    private String phone;
    /** 短信场景：register 注册/登录(默认) / reset 重置/换绑 */
    private String type;
}
