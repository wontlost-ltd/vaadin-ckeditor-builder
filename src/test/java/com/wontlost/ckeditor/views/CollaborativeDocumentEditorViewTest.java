package com.wontlost.ckeditor.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.wontlost.ckeditor.config.CKEditorProperties;
import com.wontlost.ckeditor.config.CollaborationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CollaborativeDocumentEditorView 单元测试
 * 覆盖构造函数初始化、beforeEnter 认证检查和条件渲染
 */
@ExtendWith(MockitoExtension.class)
class CollaborativeDocumentEditorViewTest {

    private static final String LICENSE_KEY = "test-license";

    @Mock
    private CKEditorProperties ckEditorProperties;
    @Mock
    private CollaborationProperties collaborationProperties;
    @Mock
    private BeforeEnterEvent beforeEnterEvent;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("构造函数")
    class Constructor {

        private CollaborativeDocumentEditorView view;

        @BeforeEach
        void setUp() {
            when(ckEditorProperties.getLicenseKey()).thenReturn(LICENSE_KEY);
            when(ckEditorProperties.hasPremiumLicense()).thenReturn(false);
            view = new CollaborativeDocumentEditorView(ckEditorProperties, collaborationProperties);
        }

        @Test
        @DisplayName("设置布局属性和 CSS class")
        void setsLayoutFlagsAndClassName() {
            assertFalse(view.isPadding());
            assertFalse(view.isSpacing());
            assertEquals("100%", view.getWidth());
            assertEquals("100%", view.getHeight());
            assertTrue(view.getClassNames().contains("collaborative-document-editor-view"));
        }
    }

    @Nested
    @DisplayName("未认证用户")
    class Unauthenticated {

        @Test
        @DisplayName("未认证时显示登录表单")
        void showsLoginFormWhenUnauthenticated() {
            when(ckEditorProperties.getLicenseKey()).thenReturn(LICENSE_KEY);
            when(ckEditorProperties.hasPremiumLicense()).thenReturn(false);
            CollaborativeDocumentEditorView view =
                new CollaborativeDocumentEditorView(ckEditorProperties, collaborationProperties);

            view.beforeEnter(beforeEnterEvent);

            // 验证不再 forward 到登录页面
            verify(beforeEnterEvent, never()).forwardTo("login");

            // 验证包含登录表单容器（内嵌 LoginForm，不覆盖整个页面）
            Div loginContainer = findChildDivByClass(view, "login-form-container");
            assertNotNull(loginContainer, "应显示内嵌登录表单容器");

            boolean hasLoginForm = loginContainer.getChildren()
                .anyMatch(LoginForm.class::isInstance);
            assertTrue(hasLoginForm, "登录容器内应包含 LoginForm");
        }

        @Test
        @DisplayName("未认证时不显示标题栏和退出按钮")
        void noTitleBarWhenUnauthenticated() {
            when(ckEditorProperties.getLicenseKey()).thenReturn(LICENSE_KEY);
            when(ckEditorProperties.hasPremiumLicense()).thenReturn(false);
            CollaborativeDocumentEditorView view =
                new CollaborativeDocumentEditorView(ckEditorProperties, collaborationProperties);

            view.beforeEnter(beforeEnterEvent);

            // 未认证时不显示标题栏，只有居中的登录表单
            Div userActions = findNestedDivByClass(view, "title-bar-user-actions");
            assertNull(userActions, "未认证时不应包含用户操作区域");
        }
    }

    @Nested
    @DisplayName("beforeEnter 条件渲染（已认证）")
    class BeforeEnterRendering {

        @BeforeEach
        void setUpAuth() {
            stubAuthentication("alice");
        }

        @Test
        @DisplayName("无 premium license 显示配置提示")
        void missingPremiumShowsNotice() {
            CollaborativeDocumentEditorView view = createView(false, true);

            view.beforeEnter(beforeEnterEvent);

            Div notice = findChildDivByClass(view, "collaboration-not-configured");
            assertNotNull(notice);
            assertEquals(
                "Premium license 未配置，协作功能需要有效的 CKEditor 5 Premium 许可证。",
                extractNoticeMessage(notice)
            );
            assertNull(findChildDivByClass(view, "collaborative-editor-container"));
        }

        @Test
        @DisplayName("有 premium 但无 collaboration 配置显示配置提示")
        void missingCollaborationShowsNotice() {
            CollaborativeDocumentEditorView view = createView(true, false);

            view.beforeEnter(beforeEnterEvent);

            Div notice = findChildDivByClass(view, "collaboration-not-configured");
            assertNotNull(notice);
            assertEquals(
                "CKEditor Cloud Services 未配置。请在 .env 中设置 CKEDITOR_CS_ENVIRONMENT_ID、" +
                    "CKEDITOR_CS_API_SECRET 和 CKEDITOR_CS_WS_URL。",
                extractNoticeMessage(notice)
            );
            assertNull(findChildDivByClass(view, "collaborative-editor-container"));
        }

        @Test
        @DisplayName("premium + configured 创建编辑器容器")
        void premiumAndConfiguredCreatesEditorContainer() {
            CollaborativeDocumentEditorView view = createView(true, true);

            assertDoesNotThrow(() -> view.beforeEnter(beforeEnterEvent));

            Div container = findChildDivByClass(view, "collaborative-editor-container");
            assertNotNull(container);
            assertNull(findChildDivByClass(view, "collaboration-not-configured"));
        }

        @Test
        @DisplayName("已认证时标题栏包含用户名和退出图标按钮")
        void titleBarShowsUserNameAndLogoutButton() {
            CollaborativeDocumentEditorView view = createView(false, false);

            view.beforeEnter(beforeEnterEvent);

            Div userActions = findNestedDivByClass(view, "title-bar-user-actions");
            assertNotNull(userActions, "已认证时标题栏应包含用户操作区域");

            // 验证用户名显示
            boolean hasUserName = userActions.getChildren()
                .filter(Span.class::isInstance)
                .map(Span.class::cast)
                .anyMatch(span -> "alice".equals(span.getText()));
            assertTrue(hasUserName, "应显示当前用户名 'alice'");

            // 验证退出图标按钮（无文本，仅图标）
            boolean hasLogoutBtn = userActions.getChildren()
                .anyMatch(Button.class::isInstance);
            assertTrue(hasLogoutBtn, "应包含退出图标按钮");
        }
    }

    private void stubAuthentication(String username) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(username, null, List.of())
        );
    }

    private CollaborativeDocumentEditorView createView(boolean hasPremium, boolean configured) {
        when(ckEditorProperties.getLicenseKey()).thenReturn(LICENSE_KEY);
        when(ckEditorProperties.hasPremiumLicense()).thenReturn(hasPremium);
        if (hasPremium) {
            when(collaborationProperties.isConfigured()).thenReturn(configured);
            if (configured) {
                when(collaborationProperties.getWebSocketUrl()).thenReturn("ws://localhost");
            }
        }
        return new CollaborativeDocumentEditorView(ckEditorProperties, collaborationProperties);
    }

    private Div findChildDivByClass(CollaborativeDocumentEditorView view, String className) {
        return view.getChildren()
            .filter(component -> component instanceof Div)
            .map(component -> (Div) component)
            .filter(div -> div.getClassNames().contains(className))
            .findFirst()
            .orElse(null);
    }

    /**
     * 递归查找嵌套的 Div（标题栏内的子 Div）
     */
    private Div findNestedDivByClass(CollaborativeDocumentEditorView view, String className) {
        return view.getChildren()
            .flatMap(c -> c instanceof Div div
                ? java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(div),
                    div.getChildren().filter(Div.class::isInstance).map(Div.class::cast))
                : java.util.stream.Stream.empty())
            .filter(div -> div.getClassNames().contains(className))
            .findFirst()
            .orElse(null);
    }

    private String extractNoticeMessage(Div notice) {
        List<Span> spans = notice.getChildren()
            .filter(Span.class::isInstance)
            .map(Span.class::cast)
            .toList();
        if (spans.size() < 2) {
            return "";
        }
        return spans.get(1).getText();
    }
}
