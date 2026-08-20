package com.yangkangling.library.config;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 注册拦截器和静态资源缓存策略。
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // 登录拦截器，用于保护需要登录才能访问的接口。
    private final LoginInterceptor loginInterceptor;

    // 构造方法注入登录拦截器。
    public WebConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    // 注册接口拦截规则。
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 默认拦截所有路径。
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                // 登录、注册和静态资源不需要登录即可访问。
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/auth/**",
                        "/readers/register",
                        "/error",
                        "/favicon.ico",
                        "/assets/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/webjars/**"
                );
    }

    // 给前端静态资源添加不缓存响应头，避免浏览器一直使用旧页面。
    @Bean
    public FilterRegistrationBean<Filter> staticResourceNoCacheFilter() {
        // 创建过滤器注册对象。
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        // 注册一个内联过滤器，在静态资源响应前后都设置不缓存头。
        registration.setFilter((request, response, chain) -> {
            // 只有 HTTP 响应才支持设置缓存响应头。
            if (response instanceof HttpServletResponse) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                // 请求进入后先设置一次。
                applyStaticNoCacheHeaders(httpResponse);
                // 继续执行后续过滤器和资源处理。
                chain.doFilter(request, response);
                // 响应返回前再设置一次，避免中途被覆盖。
                applyStaticNoCacheHeaders(httpResponse);
                return;
            }
            // 非 HTTP 响应直接放行。
            chain.doFilter(request, response);
        });
        // 只对首页和打包资源生效。
        registration.addUrlPatterns("/", "/index.html", "/assets/*");
        // 优先执行，尽早写入缓存策略。
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    // 实际设置不缓存响应头。
    private static void applyStaticNoCacheHeaders(HttpServletResponse response) {
        // 禁止浏览器和代理缓存。
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        // 兼容旧浏览器的禁用缓存头。
        response.setHeader("Pragma", "no-cache");
        // 设置过期时间为 0。
        response.setDateHeader("Expires", 0);
    }
}
