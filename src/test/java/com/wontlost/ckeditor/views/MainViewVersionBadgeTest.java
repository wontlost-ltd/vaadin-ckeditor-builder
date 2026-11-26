package com.wontlost.ckeditor.views;

import com.vaadin.flow.component.html.Span;
import com.wontlost.ckeditor.utils.AppVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 页头版本标签的回归测试：此前写死 "v5.3.*"，6.0 / 6.2 上线后页面仍显示 5.3。
 */
class MainViewVersionBadgeTest {

    @Test
    @DisplayName("页头版本标签显示构建版本，而不是写死的文字")
    void badgeShowsBuildVersion() {
        Span badge = MainView.createVersionBadge();
        // AppVersionTest 已核对 badge() 与 build.gradle 的 version 一致
        assertEquals(AppVersion.badge(), badge.getText());
        assertNotEquals(AppVersion.UNKNOWN, badge.getText(), "测试类路径上应有 buildInfo 生成的版本");
        assertTrue(badge.getClassNames().contains("version-badge"));
    }
}
