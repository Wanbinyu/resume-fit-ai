package com.wanbinyu.resumefit.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanbinyu.resumefit.user.UserEntity;
import com.wanbinyu.resumefit.user.UserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private final AdminProperties properties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AdminProperties properties, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!properties.enabled()) return;
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, properties.bootstrapUsername()).last("LIMIT 1"));
        if (user == null) {
            user = new UserEntity();
            user.setUsername(properties.bootstrapUsername());
            user.setDisplayName("系统管理员");
            user.setPasswordHash(passwordEncoder.encode(properties.bootstrapPassword()));
            user.setRole("ADMIN");
            user.setEnabled(true);
            userMapper.insert(user);
            return;
        }
        if (!"ADMIN".equals(user.getRole()) || !Boolean.TRUE.equals(user.getEnabled())) {
            user.setRole("ADMIN");
            user.setEnabled(true);
            userMapper.updateById(user);
        }
    }
}
