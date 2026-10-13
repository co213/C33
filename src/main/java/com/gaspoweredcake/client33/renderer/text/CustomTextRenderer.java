/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.renderer.text;

import com.gaspoweredcake.client33.renderer.MeshBuilder;
import com.gaspoweredcake.client33.renderer.MeshRenderer;
import com.gaspoweredcake.client33.renderer.Client33RenderPipelines;
import com.gaspoweredcake.client33.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.IOException;
import java.nio.ByteBuffer;

public class CustomTextRenderer implements TextRenderer {
    public static final Color SHADOW_COLOR = new Color(60, 60, 60, 180);

    private final MeshBuilder mesh = new MeshBuilder(Client33RenderPipelines.UI_TEXT);

    public final FontFace fontFace;

    private static final int[] FONT_HEIGHTS = {27, 41, 54, 68, 81, 108, 135};
    private final ByteBuffer fontBuffer;
    private final Font[] fonts;
    private Font font;

    private boolean building;
    private boolean scaleOnly;
    private double fontScale = 1;
    private double scale = 1;

    public CustomTextRenderer(FontFace fontFace) throws IOException {
        this.fontFace = fontFace;

        fontBuffer = fontFace.readToDirectByteBuffer();
        fonts = new Font[FONT_HEIGHTS.length];
        fonts[0] = new Font(fontBuffer, FONT_HEIGHTS[0]);
    }

    @Override
    public void setAlpha(double a) {
        mesh.alpha = a;
    }

    @Override
    public void begin(GuiGraphicsExtractor graphics, double scale, boolean scaleOnly, boolean big) {
        if (building) throw new RuntimeException("CustomTextRenderer.begin() called twice");

        if (!scaleOnly) mesh.begin();

        // Choose an atlas at least as large as the requested scale; create it only when used.
        int index = 0;
        double requestedHeight = 27 * (big ? Math.max(3, scale) : scale);
        while (index < FONT_HEIGHTS.length - 1 && FONT_HEIGHTS[index] < requestedHeight) index++;
        if (fonts[index] == null) fonts[index] = new Font(fontBuffer, FONT_HEIGHTS[index]);
        font = fonts[index];

        this.building = true;
        this.scaleOnly = scaleOnly;

        this.fontScale = font.getHeight() / 27.0;
        this.scale = 1 + (scale - fontScale) / fontScale;
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text.isEmpty()) return 0;

        Font font = building ? this.font : fonts[0];
        return (font.getWidth(text, length) + (shadow ? 1 : 0)) * scale / 1.5;
    }

    @Override
    public double getHeight(boolean shadow) {
        Font font = building ? this.font : fonts[0];
        return (font.getHeight() + 1 + (shadow ? 1 : 0)) * scale / 1.5;
    }

    @Override
    public double render(String text, double x, double y, Color color, boolean shadow) {
        if (!building) throw new RuntimeException("VanillaTextRenderer.render() called without calling begin()");

        double width;
        if (shadow) {
            int preShadowA = SHADOW_COLOR.a;
            SHADOW_COLOR.a = (int) (color.a / 255.0 * preShadowA);

            width = font.render(mesh, text, x + fontScale * scale / 1.5, y + fontScale * scale / 1.5, SHADOW_COLOR, scale / 1.5);
            font.render(mesh, text, x, y, color, scale / 1.5);

            SHADOW_COLOR.a = preShadowA;
        } else {
            width = font.render(mesh, text, x, y, color, scale / 1.5);
        }

        return width;
    }

    @Override
    public boolean isBuilding() {
        return building;
    }

    @Override
    public void end() {
        if (!building) throw new RuntimeException("CustomTextRenderer.end() called without calling begin()");

        if (!scaleOnly) {
            mesh.end();

            MeshRenderer.begin()
                .attachments(Minecraft.getInstance().gameRenderer.mainRenderTarget())
                .pipeline(Client33RenderPipelines.UI_TEXT)
                .mesh(mesh)
                .sampler("u_Texture", font.texture.getTextureView(), font.texture.getSampler())
                .end();
        }

        building = false;
        scale = 1;
    }

    public void destroy() {
        for (Font font : this.fonts) {
            if (font != null) font.texture.close();
        }
    }
}
