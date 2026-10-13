/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33.widgets;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.widgets.WQuad;
import com.gaspoweredcake.client33.utils.render.color.Color;

public class WClient33Quad extends WQuad {
    public WClient33Quad(Color color) {
        super(color);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.roundedQuad(x, y, width, height, theme.scale(4), color);
    }
}
