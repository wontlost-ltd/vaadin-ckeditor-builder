package com.wontlost.ckeditor.views.wizard.steps;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.textfield.TextField;
import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.CKEditorType;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.BuilderState.FullscreenPageMode;
import com.wontlost.ckeditor.i18n.I18nUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 高级配置步骤中 ckeditor-vaadin 6.2 选项的输入校验：非法输入不写入 state，并阻止进入下一步。
 */
class AdvancedConfigStepTest {

    private AdvancedConfigStep step;
    private BuilderState state;

    @BeforeEach
    void setUp() {
        step = new AdvancedConfigStep();
        state = new BuilderState();
        state.addPlugin(CKEditorPlugin.FULLSCREEN);
        step.onEnter(state);
    }

    private TextField textField(String labelKey) {
        String label = I18nUtil.get(labelKey);
        return descendants(step.getContent())
            .filter(c -> c instanceof TextField tf && label.equals(tf.getLabel()))
            .map(TextField.class::cast).findFirst().orElseThrow();
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(AdvancedConfigStepTest::descendants));
    }

    @Test
    @DisplayName("非法 GHS 元素名不写入 state，且阻止前进")
    void invalidHtmlElementsBlockNext() {
        state.setGhsEnabled(true);
        TextField elements = textField("step6.htmlSupportElements");
        elements.setValue("div,section");
        assertEquals(List.of("div", "section"), state.getHtmlSupportAllowedElements());

        elements.setValue("div,1bad");
        assertTrue(elements.isInvalid());
        assertEquals(List.of("div", "section"), state.getHtmlSupportAllowedElements(), "非法值不得覆盖 state");
        assertFalse(step.validate(state).isValid());

        elements.setValue("div");
        assertTrue(step.validate(state).isValid());
    }

    @Test
    @DisplayName("自定义全屏宽度为空或非法时阻止前进，合法宽度写入 state")
    void customFullscreenWidthMustBeValid() {
        state.setFullscreenPageMode(FullscreenPageMode.CUSTOM);
        assertFalse(step.validate(state).isValid(), "CUSTOM 未填宽度");

        TextField width = textField("step6.fullscreenCustomWidth");
        width.setValue("red; x");
        assertTrue(width.isInvalid());
        assertEquals("", state.getFullscreenCustomWidth());
        assertFalse(step.validate(state).isValid());

        width.setValue("1100px");
        assertFalse(width.isInvalid());
        assertEquals("1100px", state.getFullscreenCustomWidth());
        assertTrue(step.validate(state).isValid());
    }

    @Test
    @DisplayName("BALLOON 没有全屏模式：自定义宽度不阻止前进")
    void balloonIgnoresFullscreenWidth() {
        state.setFullscreenPageMode(FullscreenPageMode.CUSTOM);
        state.setEditorType(CKEditorType.BALLOON);
        assertTrue(step.validate(state).isValid());
    }
}
