package com.chocwell.jy.interceptor;

import com.chocwell.jy.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理后台身份拦截器
 * 解析 Authorization: Bearer <admin-token>，提取 adminId / role 放入 request attribute
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public static final String ADMIN_ID_KEY   = "currentAdminId";
    public static final String ADMIN_ROLE_KEY = "currentAdminRole";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            write401(response, "未登录");
            return false;
        }
        try {
            String token = auth.substring(7);
            Long adminId = jwtUtil.parseAdminId(token);
            String role  = jwtUtil.parseAdminRole(token);
            request.setAttribute(ADMIN_ID_KEY,   adminId);
            request.setAttribute(ADMIN_ROLE_KEY, role);
            return true;
        } catch (Exception e) {
            write401(response, "token 无效或已过期");
            return false;
        }
    }

    private void write401(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"" + msg + "\"}");
    }
}
