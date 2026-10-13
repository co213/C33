/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33.widgets;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33Widget;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WWindow;

public class WClient33Window extends WWindow implements Client33Widget {

    public WClient33Window(WWidget icon, String title) {
        super(icon, title);
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new WClient33Header(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (expanded || animProgress > 0) {
            double shadow = theme.scale(3);
            renderer.roundedQuad(x + shadow, y + shadow, width, height, theme.scale(7), theme().windowShadow.get());
            renderBackground(renderer, this, theme().outlineColor.get(), theme().backgroundColor.get());
        }
    }

    private class WClient33Header extends WHeader {
        public WClient33Header(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            renderer.roundedQuad(this, theme.scale(6), theme().outlineColor.get());
            double inset = theme.scale(1);
            renderer.roundedQuad(x + inset, y + inset, width - inset * 2, height - inset * 2,
                theme.scale(5), theme().headerHighlight.get(), theme().headerBackground.get());
        }
    }
}
