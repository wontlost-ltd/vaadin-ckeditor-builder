package com.wontlost.ckeditor.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CollaborationProperties 单元测试
 * 覆盖默认值、getter/setter、isConfigured() 的各种边界条件
 */
class CollaborationPropertiesTest {

    private CollaborationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CollaborationProperties();
    }

    @Test
    @DisplayName("默认值为空字符串")
    void defaults_shouldBeEmptyStrings() {
        assertEquals("", properties.getEnvironmentId());
        assertEquals("", properties.getApiSecret());
        assertEquals("", properties.getWebSocketUrl());
        assertFalse(properties.isConfigured());
    }

    @Test
    @DisplayName("setter/getter 正常工作")
    void settersAndGetters_shouldWork() {
        properties.setEnvironmentId("env-1");
        properties.setApiSecret("secret");
        properties.setWebSocketUrl("wss://env-1.cke-cs.com/ws");

        assertEquals("env-1", properties.getEnvironmentId());
        assertEquals("secret", properties.getApiSecret());
        assertEquals("wss://env-1.cke-cs.com/ws", properties.getWebSocketUrl());
    }

    @Nested
    @DisplayName("isConfigured")
    class IsConfigured {

        @Test
        @DisplayName("全部有值时返回 true")
        void shouldReturnTrueWhenAllValuesPresent() {
            setValidValues();
            assertTrue(properties.isConfigured());
        }

        @Nested
        @DisplayName("null 值")
        class NullValues {

            @Test
            @DisplayName("environmentId 为 null 返回 false")
            void environmentIdNull_shouldReturnFalse() {
                setValidValues();
                properties.setEnvironmentId(null);
                assertFalse(properties.isConfigured());
            }

            @Test
            @DisplayName("apiSecret 为 null 返回 false")
            void apiSecretNull_shouldReturnFalse() {
                setValidValues();
                properties.setApiSecret(null);
                assertFalse(properties.isConfigured());
            }

            @Test
            @DisplayName("webSocketUrl 为 null 返回 false")
            void webSocketUrlNull_shouldReturnFalse() {
                setValidValues();
                properties.setWebSocketUrl(null);
                assertFalse(properties.isConfigured());
            }
        }

        @Nested
        @DisplayName("空字符串")
        class EmptyStrings {

            @Test
            @DisplayName("environmentId 为空返回 false")
            void environmentIdEmpty_shouldReturnFalse() {
                setValidValues();
                properties.setEnvironmentId("");
                assertFalse(properties.isConfigured());
            }

            @Test
            @DisplayName("apiSecret 为空返回 false")
            void apiSecretEmpty_shouldReturnFalse() {
                setValidValues();
                properties.setApiSecret("");
                assertFalse(properties.isConfigured());
            }

            @Test
            @DisplayName("webSocketUrl 为空返回 false")
            void webSocketUrlEmpty_shouldReturnFalse() {
                setValidValues();
                properties.setWebSocketUrl("");
                assertFalse(properties.isConfigured());
            }
        }

        @Nested
        @DisplayName("空白字符串")
        class BlankStrings {

            @Test
            @DisplayName("environmentId 为空白返回 false")
            void environmentIdBlank_shouldReturnFalse() {
                setValidValues();
                properties.setEnvironmentId("   ");
                assertFalse(properties.isConfigured());
            }

            @Test
            @DisplayName("apiSecret 为空白返回 false")
            void apiSecretBlank_shouldReturnFalse() {
                setValidValues();
                properties.setApiSecret("   ");
                assertFalse(properties.isConfigured());
            }

            @Test
            @DisplayName("webSocketUrl 为空白返回 false")
            void webSocketUrlBlank_shouldReturnFalse() {
                setValidValues();
                properties.setWebSocketUrl("   ");
                assertFalse(properties.isConfigured());
            }
        }
    }

    private void setValidValues() {
        properties.setEnvironmentId("env-1");
        properties.setApiSecret("secret");
        properties.setWebSocketUrl("wss://env-1.cke-cs.com/ws");
    }
}
