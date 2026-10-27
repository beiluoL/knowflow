package com.knowflow.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.dto.RegisterDTO;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Explicit development launcher opt-in; never provisions a known-password production account. */
@Component @Order(100) @RequiredArgsConstructor
public class DevelopmentInterviewAccount implements ApplicationRunner {
    private static final String USERNAME="seed-personal-v1";
    private static final String PASSWORD="SeedInterview!2026";
    private final Environment environment;
    private final JdbcTemplate jdbc;
    private final SysUserMapper users;
    private final UserService registration;
    private final PasswordEncoder passwords;
    private final InterviewSeedService seed;

    @Override public void run(ApplicationArguments args) {
        if(!environment.getProperty("knowflow.seed.development-account.enabled",Boolean.class,false)
                || !environment.acceptsProfiles(Profiles.of("local"))
                || environment.acceptsProfiles(Profiles.of("prod","production")))return;
        String url=jdbc.execute((ConnectionCallback<String>)c->c.getMetaData().getURL());
        // A local profile alone is insufficient: refuse external/persistent databases.
        if(url==null || !url.startsWith("jdbc:h2:mem:"))throw new IllegalStateException("Development interview account requires an isolated H2 memory database");
        SysUser existing=users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername,USERNAME));
        Long actor;
        if(existing==null) {
            RegisterDTO dto=new RegisterDTO();dto.setUsername(USERNAME);dto.setPassword(PASSWORD);
            dto.setNickname("Java + AI 面试测试用户");
            actor=registration.register(dto).getUser().getId();
        } else {
            if(!"USER".equals(existing.getRole()) || !passwords.matches(PASSWORD,existing.getPassword()))
                throw new BusinessException(409,"内置面试测试账号已存在但身份或密码不同，未覆盖该账号");
            actor=existing.getId();
        }
        seed.activate(actor);
    }
}
