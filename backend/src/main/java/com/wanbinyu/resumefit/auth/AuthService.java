package com.wanbinyu.resumefit.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanbinyu.resumefit.common.BusinessException;
import com.wanbinyu.resumefit.security.JwtService;
import com.wanbinyu.resumefit.user.UserEntity;
import com.wanbinyu.resumefit.user.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (findByUsername(request.username()) != null) throw new BusinessException("用户名已存在");
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank() ? request.username() : request.displayName());
        user.setRole("USER");
        user.setEnabled(true);
        userMapper.insert(user);
        return toResponse(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        UserEntity user = findByUsername(request.username());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        return toResponse(user);
    }

    private UserEntity findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username).last("LIMIT 1"));
    }

    private AuthDtos.AuthResponse toResponse(UserEntity user) {
        return new AuthDtos.AuthResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), jwtService.create(user.getId(), user.getRole()));
    }
}
