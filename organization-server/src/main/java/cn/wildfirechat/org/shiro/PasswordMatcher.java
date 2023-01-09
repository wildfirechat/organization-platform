package cn.wildfirechat.org.shiro;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class PasswordMatcher implements CredentialsMatcher {

    @Override
    public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
        if (token instanceof UsernamePasswordToken && info instanceof SimpleAuthenticationInfo) {
            UsernamePasswordToken passwordToken = (UsernamePasswordToken) token;
            String pwd = new String(passwordToken.getPassword());
            SimpleAuthenticationInfo authenticationInfo = (SimpleAuthenticationInfo)info;
            String salt = new String(authenticationInfo.getCredentialsSalt().getBytes());

            String md5 = new Base64().encodeToString(DigestUtils.getDigest("MD5").digest((pwd + salt).getBytes(StandardCharsets.UTF_8)));
            if(md5.equals(authenticationInfo.getCredentials())) {
                return true;
            }
        }
        return false;
    }
}
