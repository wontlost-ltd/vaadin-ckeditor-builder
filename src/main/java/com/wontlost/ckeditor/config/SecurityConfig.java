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
 * 使用 NavigationAccessControl 保护管理员页面和协作编辑器。
 * 视图级访问控制由 @AnonymousAllowed / @PermitAll / @RolesAllowed 注解驱动。
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
        // token 端点需要认证（协作编辑器通过 beforeEnter 检查认证状态）
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/ckeditor/token").fullyAuthenticated()
            .anyRequest().permitAll()
        );

        // 配置表单登录 — 登录成功后始终跳转到协作编辑器
        // alwaysUse=true 避免 saved request 指向 /api/ckeditor/token（该端点触发认证）
        http.formLogin(form -> form
            .loginPage("/login")
            .defaultSuccessUrl("/collaborative-document-editor", true)
            .permitAll()
        );

        // 配置登出 — 退出后重定向到协作编辑器（显示登录表单）
        http.logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/collaborative-document-editor")
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
     * 根据视图类上的 @AnonymousAllowed, @PermitAll, @RolesAllowed 注解控制访问
     * @PermitAll 要求用户已认证，未认证时重定向到登录页面
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

        // 协作编辑器测试用户
        UserDetails alice = User.builder()
            .username("alice")
            .password(passwordEncoder.encode("alice"))
            .roles("USER")
            .build();
        UserDetails bob = User.builder()
            .username("bob")
            .password(passwordEncoder.encode("bob"))
            .roles("USER")
            .build();

        return new InMemoryUserDetailsManager(admin, alice, bob);
    }
}
