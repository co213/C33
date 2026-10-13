/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33.widgets;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.tabs.Tab;
import com.gaspoweredcake.client33.gui.tabs.TabScreen;
import com.gaspoweredcake.client33.gui.tabs.Tabs;
import com.gaspoweredcake.client33.gui.themes.client33.Client33Widget;
import com.gaspoweredcake.client33.gui.widgets.WTopBar;
import com.gaspoweredcake.client33.utils.render.color.Color;

import static com.gaspoweredcake.client33.Client33.mc;

public class WClient33TopBar extends WTopBar implements Client33Widget {
    @Override
    public void init() {
        for (Tab tab : Tabs.get()) {
            add(new WTopBarButton(tab) {
                @Override
                protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                    super.onRender(renderer, mouseX, mouseY, delta);

                    boolean active = mc.gui.screen() instanceof TabScreen screen && screen.tab == tab;
                    double line = WClient33TopBar.this.theme().scale(active ? 2 : 1);
                    Color color = active ? WClient33TopBar.this.theme().accentColor.get() : WClient33TopBar.this.theme().outlineColor.get(false, mouseOver);
                    renderer.roundedQuad(x + theme.scale(5), y + height - line, width - theme.scale(10), line, line / 2, color);
                }
            });
        }
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.roundedQuad(this, theme.scale(5), theme().backgroundColor.get());
    }

    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered) {
        return theme().backgroundColor.get(pressed, hovered);
    }

    @Override
    protected Color getNameColor() {
        return theme().textColor.get();
    }
}
