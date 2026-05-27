package com.wontlost.ckeditor.config;

import com.vaadin.flow.spring.security.NavigationAccessControlConfigurer;
import com.wontlost.ckeditor.security.TurnstileFilter;
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
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.UrlUtils;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   TurnstileFilter turnstileFilter) throws Exception {
        // API 端点认证：token 和 AI 代理均需认证（ai-token 匿名可访问，仅用于预览）
        // Actuator 健康探针对 Kubernetes kubelet 开放，便于 liveness/readiness 探测
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            .requestMatchers("/api/ckeditor/token", "/api/ai/proxy").fullyAuthenticated()
            .requestMatchers("/api/ai/preview-proxy", "/api/ckeditor/ai-token").permitAll()
            .anyRequest().permitAll()
        );

        // Turnstile 验证在认证之前执行，仅拦截 POST /login
        http.addFilterBefore(turnstileFilter, UsernamePasswordAuthenticationFilter.class);

        // 配置表单登录 — redirect 参数白名单校验，防止开放重定向
        SimpleUrlAuthenticationSuccessHandler successHandler = new SimpleUrlAuthenticationSuccessHandler() {
            private static final java.util.Set<String> ALLOWED_REDIRECTS = java.util.Set.of(
                "/collaborative-document-editor",
                "/ai-document-editor",
                "/notion-document-editor",
                "/admin"
            );

            @Override
            protected String determineTargetUrl(jakarta.servlet.http.HttpServletRequest request,
                                                jakarta.servlet.http.HttpServletResponse response) {
                String redirect = request.getParameter("redirect");
                if (redirect != null && ALLOWED_REDIRECTS.contains(redirect)) {
                    return redirect;
                }
                return getDefaultTargetUrl();
            }
        };
        successHandler.setDefaultTargetUrl("/collaborative-document-editor");
        http.formLogin(form -> form
            .loginPage("/login")
            .successHandler(successHandler)
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

    @Bean
    public TurnstileFilter turnstileFilter(TurnstileProperties turnstileProperties) {
        return new TurnstileFilter(turnstileProperties);
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
