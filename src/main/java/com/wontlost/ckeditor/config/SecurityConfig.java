package com.wontlost.ckeditor.config;

import com.vaadin.flow.spring.security.NavigationAccessControlConfigurer;
import com.wontlost.ckeditor.views.admin.LoginView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置
 * 使用 NavigationAccessControl 保护管理员页面
 * HTTP 层允许所有请求，由 Vaadin 的视图注解控制访问
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:}")
    private String adminPassword;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // HTTP 层允许所有请求
        // 由 Vaadin 的 NavigationAccessControl 根据 @RolesAllowed/@AnonymousAllowed 注解控制访问
        http.authorizeHttpRequests(auth -> auth
            .anyRequest().permitAll()
        );

        // 配置表单登录
        http.formLogin(form -> form
            .loginPage("/login")
            .defaultSuccessUrl("/admin/subscribers", true)
            .permitAll()
        );

        // 配置登出
        http.logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/")
            .permitAll()
        );

        // 禁用 CSRF（简化开发）
        http.csrf(csrf -> csrf.disable());

        // 允许 H2 控制台使用 frame
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * 配置 NavigationAccessControl 使用 AnnotatedViewAccessChecker
     * 这将根据视图类上的 @AnonymousAllowed, @PermitAll, @RolesAllowed 注解控制访问
     * 当访问被拒绝时，重定向到登录页面
     */
    @Bean
    static NavigationAccessControlConfigurer navigationAccessControlConfigurer() {
        return new NavigationAccessControlConfigurer()
            .withAnnotatedViewAccessChecker()
            .withLoginView(LoginView.class);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsManager(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
            .username(adminUsername)
            .password(passwordEncoder.encode(adminPassword))
            .roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
