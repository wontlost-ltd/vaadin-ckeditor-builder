package com.wontlost.ckeditor.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    void versionMatchesBuildGradle() throws IOException {
        String gradle = Files.readString(Path.of("build.gradle"));
        Matcher m = Pattern.compile("(?m)^version\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(gradle);
        assertTrue(m.find(), "build.gradle 中找不到 version");
        assertEquals(m.group(1), AppVersion.version());
        assertEquals(AppVersion.badgeFor(m.group(1)), AppVersion.badge());
    }
}
