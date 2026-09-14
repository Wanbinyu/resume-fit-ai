package com.wanbinyu.resumefit.common;

import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() {}

    public static Long id(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw new BusinessException("未登录");
        }
        return id;
    }
}
