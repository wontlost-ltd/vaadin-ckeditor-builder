package com.wontlost.ckeditor.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 页头版本标签必须来自构建版本：此前写死的 "v5.3.*" 在 6.0 / 6.2 上线后仍显示旧版本。
 */
class AppVersionTest {

    @Test
    @DisplayName("标签显示 主.次 版本加通配，补丁号与后缀不显示")
    void badgeShowsMajorMinor() {
        assertEquals("v6.2.*", AppVersion.badgeFor("6.2.0"));
        assertEquals("v6.2.*", AppVersion.badgeFor("6.2.13"));
        assertEquals("v10.0.*", AppVersion.badgeFor("10.0.1-SNAPSHOT"));
        assertEquals("v7.1.*", AppVersion.badgeFor(" 7.1 "));
    }

    @Test
    @DisplayName("没有构建信息时给 dev，非数字版本原样加 v")
    void badgeFallbacks() {
        assertEquals(AppVersion.UNKNOWN, AppVersion.badgeFor(null));
        assertEquals(AppVersion.UNKNOWN, AppVersion.badgeFor(""));
        assertEquals(AppVersion.UNKNOWN, AppVersion.badgeFor(AppVersion.UNKNOWN));
        assertEquals("vmain", AppVersion.badgeFor("main"));
    }

    @Test
    @DisplayName("运行时版本与 build.gradle 的 version 一致（buildInfo 已生成并被读取）")
    void versionMatchesBuildGradle() {
        String expected = System.getProperty("project.version");
        assertNotNull(expected, "Gradle test 任务应传入 project.version");
        assertEquals(expected, AppVersion.version());
        assertEquals(AppVersion.badgeFor(expected), AppVersion.badge());
    }

    @Test
    @DisplayName("非数字版本按约定原样显示（如分支构建 main → vmain），不回退成 dev")
    void nonNumericVersionShownAsIs() {
        assertEquals("vnightly", AppVersion.badgeFor("nightly"));
    }
}
