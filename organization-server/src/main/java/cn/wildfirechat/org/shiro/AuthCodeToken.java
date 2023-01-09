package cn.wildfirechat.org.shiro;

import org.apache.shiro.authc.AuthenticationToken;

public class AuthCodeToken implements AuthenticationToken {
    public String authCode;

    public AuthCodeToken() {
    }

    public AuthCodeToken(String authCode) {
        this.authCode = authCode;
    }

    @Override
    public Object getPrincipal() {
        return "admin";
    }

    @Override
    public Object getCredentials() {
        return authCode;
    }
}
