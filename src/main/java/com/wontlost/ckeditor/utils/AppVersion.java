package com.wontlost.ckeditor.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 应用版本：取自 Spring Boot buildInfo 生成的 {@code META-INF/build-info.properties}（即 build.gradle 的 version），
 * 页面不再硬编码版本号。未经 Gradle 构建（如 IDE 直接运行而未生成该文件）时给出 {@link #UNKNOWN}。
 */
public final class AppVersion {

    /** 读不到构建信息时的标签 */
    public static final String UNKNOWN = "dev";

    private static final String BUILD_INFO = "META-INF/build-info.properties";
    private static final Pattern MAJOR_MINOR = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\D.*)?$");

    private static final String VERSION = load();

    private AppVersion() {}

    /** 完整版本号，如 {@code 6.2.0}；读不到时为 {@link #UNKNOWN} */
    public static String version() {
        return VERSION;
    }

    /** 页头版本标签，如 {@code v6.2.*} */
    public static String badge() {
        return badgeFor(VERSION);
    }

    /** 由版本号得出标签：主.次 版本加通配，补丁号不显示；不是数字版本时原样加 v 前缀 */
    static String badgeFor(String version) {
        if (version == null || version.isBlank()) {
            return UNKNOWN;
        }
        Matcher m = MAJOR_MINOR.matcher(version.strip());
        if (!m.matches()) {
            return UNKNOWN.equals(version) ? UNKNOWN : "v" + version.strip();
        }
        return "v" + m.group(1) + "." + m.group(2) + ".*";
    }

    private static String load() {
        try (InputStream in = AppVersion.class.getClassLoader().getResourceAsStream(BUILD_INFO)) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties props = new Properties();
            props.load(in);
            String v = props.getProperty("build.version");
            return v == null || v.isBlank() ? UNKNOWN : v.strip();
        } catch (IOException e) {
            return UNKNOWN;
        }
    }
}
