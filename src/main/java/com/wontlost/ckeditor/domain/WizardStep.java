package com.wontlost.ckeditor.domain;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Wizard 步骤接口
 * 每个步骤实现此接口以提供统一的行为
 */
public interface WizardStep {

    /**
     * 步骤唯一标识
     */
    String getId();

    /**
     * 步骤标题
     */
    String getTitle();

    /**
     * 步骤描述
     */
    String getDescription();

    /**
     * 步骤图标
     */
    VaadinIcon getIcon();

    /**
     * 获取步骤内容组件
     */
    Component getContent();

    /**
     * 进入步骤时调用
     */
    default void onEnter(BuilderState state) {}

    /**
     * 离开步骤时调用
     */
    default void onExit(BuilderState state) {}

    /**
     * 验证步骤是否可以继续
     */
    default ValidationResult validate(BuilderState state) {
        return ValidationResult.ok();
    }

    /**
     * 是否可以跳过此步骤
     */
    default boolean isSkippable() {
        return false;
    }

    /**
     * 刷新步骤内容
     */
    default void refresh(BuilderState state) {}
}
