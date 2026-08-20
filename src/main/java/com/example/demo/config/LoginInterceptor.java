package com.example.demo.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// 统一校验接口登录状态。
@Component
public class LoginInterceptor implements HandlerInterceptor {
    // 每次请求进入控制器前都会先经过这里。
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取当前请求已有的 Session，不主动创建新 Session。
        HttpSession session = request.getSession(false);
        // 获取请求路径，用来区分管理端接口和读者自助端接口。
        String path = request.getRequestURI();
        // 读者端接口只允许已登录读者访问。
        if (session != null && path.startsWith("/self/") && session.getAttribute("readerId") != null) {
            return true;
        }
        // 非读者端接口默认按管理端处理，只允许已登录管理员访问。
        if (session != null && !path.startsWith("/self/") && session.getAttribute("adminId") != null) {
            return true;
        }

        // 没有匹配到合法登录身份时返回 401。
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // 设置响应编码，避免中文提示乱码。
        response.setCharacterEncoding("UTF-8");
        // 返回 JSON 格式错误信息。
        response.setContentType("application/json;charset=UTF-8");
        // 写出未登录提示。
        response.getWriter().write("{\"error\":\"请先登录\"}");
        // 返回 false 表示请求不再继续进入控制器。
        return false;
    }
}
