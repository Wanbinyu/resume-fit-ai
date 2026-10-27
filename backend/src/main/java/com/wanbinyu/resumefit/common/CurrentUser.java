package com.wanbinyu.resumefit.common;

import org.springframework.security.core.Authentication;

/** 从过滤器建立的 Authentication 中提取用户 ID，供 Controller 传入业务 Service。 */
public final class CurrentUser {

    private CurrentUser() {}

    public static Long id(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw new BusinessException("未登录");
        }
        return id;
    }
}
