package cn.wildfirechat.org.shiro;


import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.Cookie;
import org.apache.shiro.web.servlet.ShiroHttpSession;
import org.apache.shiro.web.servlet.SimpleCookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ShiroConfig {

    @Autowired
    DBSessionDao dbSessionDao;

    @Value("${wfc.all_client_support_ssl}")
    private boolean All_Client_Support_SSL;

    @Bean(name = "shiroFilter")
    public ShiroFilterFactoryBean shiroFilter(SecurityManager securityManager) {
        ShiroFilterFactoryBean shiroFilterFactoryBean = new ShiroFilterFactoryBean();
        shiroFilterFactoryBean.setSecurityManager(securityManager);
        shiroFilterFactoryBean.setLoginUrl("/login");
        shiroFilterFactoryBean.setUnauthorizedUrl("/notRole");
        Map<String, String> filterChainDefinitionMap = new LinkedHashMap<>();

        // <!-- authc:所有url都必须认证通过才可以访问; anon:所有url都都可以匿名访问-->
        filterChainDefinitionMap.put("/index.html", "anon");
        filterChainDefinitionMap.put("/js/*", "anon");
        filterChainDefinitionMap.put("/fonts/*", "anon");
        filterChainDefinitionMap.put("/img/*", "anon");
        filterChainDefinitionMap.put("/css/*", "anon");
        filterChainDefinitionMap.put("/", "anon");
        filterChainDefinitionMap.put("/work.html", "anon");

        filterChainDefinitionMap.put("/api/login", "anon");
        filterChainDefinitionMap.put("/api/user_login", "anon");

        filterChainDefinitionMap.put("/**", "login");

        filterChainDefinitionMap.put("/api/update_pwd", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/account", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/logs", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/reset_all", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/import", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/template", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/media/upload", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/organization/create", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/organization/update", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/organization/move", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/organization/query", "perms[user:view]");
        filterChainDefinitionMap.put("/api/organization/query_ex", "perms[user:view]");
        filterChainDefinitionMap.put("/api/organization/query_list", "perms[user:view]");
        filterChainDefinitionMap.put("/api/organization/root", "perms[user:view]");
        filterChainDefinitionMap.put("/api/organization/search", "perms[user:view]");
        filterChainDefinitionMap.put("/api/organization/delete", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/organization/create_group", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/organization/dismiss_group", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/organization/repair_group", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/employee/create", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/employee/update", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/employee/move", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/employee/query", "perms[user:view]");
        filterChainDefinitionMap.put("/api/employee/query_ex", "perms[user:view]");
        filterChainDefinitionMap.put("/api/employee/query_list", "perms[user:view]");
        filterChainDefinitionMap.put("/api/employee/delete", "perms[user:admin]");
        filterChainDefinitionMap.put("/api/employee/search", "perms[user:view]");
        filterChainDefinitionMap.put("/api/relationship/employee", "perms[user:view]");

        //主要这行代码必须放在所有权限设置的最后，不然会导致所有 url 都被拦截 剩余的都需要认证
        shiroFilterFactoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        shiroFilterFactoryBean.getFilters().put("login", new JsonAuthLoginFilter());
        shiroFilterFactoryBean.getFilters().put("perms", new WfPermissionsAuthorizationFilter());
        return shiroFilterFactoryBean;

    }

    @Bean
    public SecurityManager securityManager() {
        DefaultWebSecurityManager defaultSecurityManager = new DefaultWebSecurityManager();
        defaultSecurityManager.setRealms(Arrays.asList(passwordRealm, authCodeRealm));
        ShiroSessionManager sessionManager = new ShiroSessionManager();
        sessionManager.setGlobalSessionTimeout(Long.MAX_VALUE);
        sessionManager.setSessionDAO(dbSessionDao);

        Cookie cookie = new SimpleCookie(ShiroHttpSession.DEFAULT_SESSION_ID_NAME);
        if (All_Client_Support_SSL) {
            cookie.setSameSite(Cookie.SameSiteOptions.NONE);
            cookie.setSecure(true);
        } else {
            cookie.setSameSite(null);
        }
        cookie.setMaxAge(Integer.MAX_VALUE);
        sessionManager.setSessionIdCookie(cookie);
        sessionManager.setSessionIdCookieEnabled(true);
        sessionManager.setSessionIdUrlRewritingEnabled(true);

        defaultSecurityManager.setSessionManager(sessionManager);
        SecurityUtils.setSecurityManager(defaultSecurityManager);
        return defaultSecurityManager;
    }

    @Autowired
    private PasswordRealm passwordRealm;


    @Autowired
    private AuthCodeRealm authCodeRealm;
}