/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33.widgets.input;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.gui.themes.client33.Client33Widget;
import com.gaspoweredcake.client33.gui.widgets.input.WSlider;

public class WClient33Slider extends WSlider implements Client33Widget {
    public WClient33Slider(double value, double min, double max) {
        super(value, min, max);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double valueWidth = valueWidth();

        renderBar(renderer, valueWidth);
        renderHandle(renderer, valueWidth);
    }

    private void renderBar(GuiRenderer renderer, double valueWidth) {
        Client33GuiTheme theme = theme();

        double s = theme.scale(3);
        double handleSize = handleSize();

        double x = this.x + handleSize / 2;
        double y = this.y + height / 2 - s / 2;

        renderer.roundedQuad(x, y, valueWidth, s, s / 2, theme.sliderLeft.get());
        renderer.roundedQuad(x + valueWidth, y, width - valueWidth - handleSize, s, s / 2, theme.sliderRight.get());
    }

    private void renderHandle(GuiRenderer renderer, double valueWidth) {
        Client33GuiTheme theme = theme();
        double s = handleSize();

        renderer.roundedQuad(x + valueWidth, y, s, s, s / 2, theme.sliderHandle.get(dragging, handleMouseOver));
    }
}
