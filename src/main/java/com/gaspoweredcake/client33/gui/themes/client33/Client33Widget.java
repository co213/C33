/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.utils.BaseWidget;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.utils.render.color.Color;

public interface Client33Widget extends BaseWidget {
    default Client33GuiTheme theme() {
        return (Client33GuiTheme) getTheme();
    }

    default void renderBackground(GuiRenderer renderer, WWidget widget, Color outlineColor, Color backgroundColor) {
        Client33GuiTheme theme = theme();
        double s = theme.scale(1);
        double radius = theme.scale(5);
        renderer.roundedQuad(widget, radius, outlineColor);
        renderer.roundedQuad(widget.x + s, widget.y + s, widget.width - s * 2, widget.height - s * 2,
            Math.max(0, radius - s), backgroundColor);
    }

    default void renderBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
        Client33GuiTheme theme = theme();
        renderBackground(renderer, widget, theme.outlineColor.get(pressed, mouseOver), theme.backgroundColor.get(pressed, mouseOver));
    }
}
