/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33.widgets;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.gui.themes.client33.Client33Widget;
import com.gaspoweredcake.client33.gui.utils.AlignmentX;
import com.gaspoweredcake.client33.gui.widgets.pressable.WPressable;
import com.gaspoweredcake.client33.systems.modules.Module;
import net.minecraft.util.Mth;

import static com.gaspoweredcake.client33.Client33.mc;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class WClient33Module extends WPressable implements Client33Widget {
    private final Module module;
    private final String title;

    private double titleWidth;

    private double animationProgress1;

    private double animationProgress2;

    public WClient33Module(Module module, String title) {
        this.module = module;
        this.title = title;
        this.tooltip = module.description;

        if (module.isActive()) {
            animationProgress1 = 1;
            animationProgress2 = 1;
        } else {
            animationProgress1 = 0;
            animationProgress2 = 0;
        }
    }

    @Override
    public double pad() {
        return theme.scale(7);
    }

    @Override
    protected void onCalculateSize() {
        double pad = pad();

        if (titleWidth == 0) titleWidth = theme.textWidth(title);

        width = pad + titleWidth + theme.textWidth("OFF") + pad * 3;
        height = pad + theme.textHeight() + pad;
    }

    @Override
    protected void onPressed(int button) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) module.toggle();
        else if (button == GLFW_MOUSE_BUTTON_RIGHT) mc.gui.setScreen(theme.moduleScreen(module));
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Client33GuiTheme theme = theme();
        double pad = pad();

        animationProgress1 += delta * 4 * ((module.isActive() || mouseOver) ? 1 : -1);
        animationProgress1 = Mth.clamp(animationProgress1, 0, 1);

        animationProgress2 += delta * 6 * (module.isActive() ? 1 : -1);
        animationProgress2 = Mth.clamp(animationProgress2, 0, 1);

        if (animationProgress1 > 0) {
            renderer.roundedQuad(x, y, width * animationProgress1, height, theme.scale(5), theme.moduleBackground.get());
        }
        if (animationProgress2 > 0) {
            renderer.roundedQuad(x, y + height * (1 - animationProgress2), theme.scale(2), height * animationProgress2, theme.scale(1), theme.accentColor.get());
        }
        renderer.roundedQuad(x, y + height - theme.scale(1), width, theme.scale(1), theme.scale(0.5), theme.separatorEdges.get());
        String status = module.isActive() ? "ON" : "OFF";
        double statusWidth = theme.textWidth("OFF");
        renderer.text(status, x + width - pad - statusWidth, y + pad,
            module.isActive() ? theme.accentColor.get() : theme.textSecondaryColor.get(), false);

        double x = this.x + pad;
        double w = width - pad * 3 - statusWidth;

        if (theme.moduleAlignment.get() == AlignmentX.Center) {
            x += w / 2 - titleWidth / 2;
        }
        else if (theme.moduleAlignment.get() == AlignmentX.Right) {
            x += w - titleWidth;
        }

        renderer.text(title, x, y + pad, module.isActive() ? theme.titleTextColor.get() : theme.textColor.get(), false);
    }
}
