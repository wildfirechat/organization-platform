package cn.wildfirechat.org.shiro;

import cn.wildfirechat.org.RestResult;
import com.google.gson.Gson;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authz.PermissionsAuthorizationFilter;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class WfPermissionsAuthorizationFilter extends PermissionsAuthorizationFilter {
    private static final Logger LOG = LoggerFactory.getLogger(WfPermissionsAuthorizationFilter.class);

    @Override
    public boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) throws IOException {
        if (WebUtils.toHttp(request).getMethod().equalsIgnoreCase("OPTIONS")){
            return true;
        }
        return super.isAccessAllowed(request, response, mappedValue);
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws IOException {
        // 打印权限不足的上下文，便于区分“没登录/没带 authToken”和“登录了但权限不够”
        Subject subject = SecurityUtils.getSubject();
        Object principal = subject == null ? null : subject.getPrincipal();
        String uri = WebUtils.toHttp(request).getRequestURI();
        LOG.warn("Permission denied: uri={}, principal={}, authenticated={}",
                uri, principal, subject != null && subject.isAuthenticated());

        PrintWriter out = null;
        try {
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json; charset=utf-8");
            out = response.getWriter();

            out.write(new Gson().toJson(RestResult.error(RestResult.RestCode.ERROR_NO_RIGHT)));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return Boolean.FALSE ;
    }
}
