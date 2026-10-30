package cn.wildfirechat.org.shiro;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
public class CorsFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(CorsFilter.class);

    // 允许跨域访问的来源，逗号分隔。为空时不允许任何跨域请求（管理后台与 API 同源部署，不需要跨域）。
    // 不能回显任意 Origin：带 Allow-Credentials 时，任意网站都能带着管理员的 Cookie 调接口并读到响应。
    @Value("${wfc.cors.allowed_origins:}")
    private String allowedOrigins;

    private Set<String> allowedOriginSet;

    @PostConstruct
    private void init() {
        allowedOriginSet = new HashSet<>();
        for (String origin : allowedOrigins.split(",")) {
            origin = origin.trim();
            if (!origin.isEmpty()) {
                allowedOriginSet.add(origin);
            }
        }
        LOG.info("CORS allowed origins: {}", allowedOriginSet);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && allowedOriginSet.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Access-Control-Allow-Headers, Cookie, X-Requested-With, authToken");
            response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, OPTIONS, PATCH");
            // refer to https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Access-Control-Expose-Headers
            response.setHeader("Access-Control-Expose-Headers", "authToken");
            LOG.debug("CORS allowed: origin={}, method={}, uri={}", origin, request.getMethod(), request.getRequestURI());
        } else if (origin != null && !origin.isEmpty()) {
            // 预检请求被拒绝时浏览器不会再发真正的 POST，服务端也就看不到 Controller 日志。
            // 这里把拒绝原因打印出来，便于排查“客户端没反应/没有 Request: xxx 日志”的问题。
            boolean preflight = "OPTIONS".equalsIgnoreCase(request.getMethod())
                    && request.getHeader("Access-Control-Request-Method") != null;
            if (preflight || !allowedOriginSet.isEmpty()) {
                LOG.warn("CORS rejected: origin={}, method={}, uri={}, allowedOrigins={}",
                        origin, request.getMethod(), request.getRequestURI(), allowedOriginSet);
            }
        }

        filterChain.doFilter(request, response);
    }
}
