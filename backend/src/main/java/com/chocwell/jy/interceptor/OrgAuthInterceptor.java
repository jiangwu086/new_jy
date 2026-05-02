package com.chocwell.jy.interceptor;

import com.chocwell.jy.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 机构端身份拦截器
 * 解析 Authorization: Bearer <org-token>，提取 adminId / orgId 放入 request attribute
 */
@Component
@RequiredArgsConstructor
public class OrgAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public static final String ORG_ADMIN_ID_KEY = "currentOrgAdminId";
    public static final String ORG_ID_KEY       = "currentOrgId";

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
            String token   = auth.substring(7);
            Long adminId   = jwtUtil.parseOrgAdminId(token);
            Long orgId     = jwtUtil.parseOrgId(token);
            request.setAttribute(ORG_ADMIN_ID_KEY, adminId);
            request.setAttribute(ORG_ID_KEY,       orgId);
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
