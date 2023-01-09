package cn.wildfirechat.org.shiro;


import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.org.jpa.EmployeeEntity;
import cn.wildfirechat.org.jpa.EmployeeEntityRepository;
import cn.wildfirechat.pojos.OutputApplicationUserInfo;
import cn.wildfirechat.sdk.UserAdmin;
import cn.wildfirechat.sdk.model.IMResult;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthCodeRealm extends AuthorizingRealm {

    @Autowired
    private AuthCodeMatcher authCodeMatcher;

    @Autowired
    private EmployeeEntityRepository employeeEntityRepository;

    @Value("${organization.allow_external_staff_access}")
    private boolean mAllowExternalStaffAccess;

    @PostConstruct
    private void initMatcher() {
        setCredentialsMatcher(authCodeMatcher);
    }

    @Override
    public Class getAuthenticationTokenClass() {
        return AuthCodeToken.class;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principalCollection) {
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        Set<String> stringSet = new HashSet<>();
        stringSet.add("user:view");
        info.setStringPermissions(stringSet);

        return info;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) throws AuthenticationException {
        if(authenticationToken instanceof AuthCodeToken) {
            AuthCodeToken authCodeToken = (AuthCodeToken) authenticationToken;
            try {
                IMResult<OutputApplicationUserInfo> applicationUserInfoIMResult = UserAdmin.applicationGetUserInfo(authCodeToken.authCode);
                if(applicationUserInfoIMResult != null && applicationUserInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    if(!mAllowExternalStaffAccess) {
                        Optional<EmployeeEntity> optional = employeeEntityRepository.findByEmployeeId(applicationUserInfoIMResult.result.getUserId());
                        if(!optional.isPresent()) {
                            return null;
                        }
                    }
                    SimpleAuthenticationInfo simpleAuthenticationInfo = new SimpleAuthenticationInfo(applicationUserInfoIMResult.getResult().getUserId(), null, getName());
                    return simpleAuthenticationInfo;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }
}