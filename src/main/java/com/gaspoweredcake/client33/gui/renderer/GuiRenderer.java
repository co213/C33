/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.renderer;

import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.renderer.operations.TextOperation;
import com.gaspoweredcake.client33.gui.renderer.packer.GuiTexture;
import com.gaspoweredcake.client33.gui.renderer.packer.TexturePacker;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.renderer.Renderer2D;
import com.gaspoweredcake.client33.renderer.Texture;
import com.gaspoweredcake.client33.utils.PostInit;
import com.gaspoweredcake.client33.utils.misc.Pool;
import com.gaspoweredcake.client33.utils.render.RenderUtils;
import com.gaspoweredcake.client33.utils.render.color.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.gaspoweredcake.client33.Client33.mc;
import static com.gaspoweredcake.client33.utils.Utils.getWindowHeight;
import static com.gaspoweredcake.client33.utils.Utils.getWindowWidth;

public class GuiRenderer {
    private static final Color WHITE = new Color(255, 255, 255);

    private static final TexturePacker TEXTURE_PACKER = new TexturePacker();
    private static Texture TEXTURE;

    public static GuiTexture CIRCLE;
    public static GuiTexture TRIANGLE;
    public static GuiTexture EDIT;
    public static GuiTexture RESET;
    public static GuiTexture FAVORITE_NO, FAVORITE_YES;
    public static GuiTexture COPY, PASTE;

    public GuiTheme theme;

    private final Renderer2D r = new Renderer2D(false);
    private final Renderer2D rTex = new Renderer2D(true);

    private final Pool<Scissor> scissorPool = new Pool<>(Scissor::new);
    private final Stack<Scissor> scissorStack = new ObjectArrayList<>();

    private final Pool<TextOperation> textPool = new Pool<>(TextOperation::new);
    private final List<TextOperation> texts = new ObjectArrayList<>();

    private final List<Runnable> postTasks = new ObjectArrayList<>();

    public String tooltip, lastTooltip;
    public WWidget tooltipWidget;
    private double tooltipAnimProgress;

    private GuiGraphicsExtractor graphics;

    public static GuiTexture addTexture(Identifier id) {
        return TEXTURE_PACKER.add(id);
    }

    @PostInit
    public static void init() {
        CIRCLE = addTexture(Client33.identifier("textures/icons/gui/circle.png"));
        TRIANGLE = addTexture(Client33.identifier("textures/icons/gui/triangle.png"));
        EDIT = addTexture(Client33.identifier("textures/icons/gui/edit.png"));
        RESET = addTexture(Client33.identifier("textures/icons/gui/reset.png"));
        FAVORITE_NO = addTexture(Client33.identifier("textures/icons/gui/favorite_no.png"));
        FAVORITE_YES = addTexture(Client33.identifier("textures/icons/gui/favorite_yes.png"));

        COPY = addTexture(Client33.identifier("textures/icons/gui/copy.png"));
        PASTE = addTexture(Client33.identifier("textures/icons/gui/paste.png"));

        TEXTURE = TEXTURE_PACKER.pack();
    }

    public void begin(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
        this.graphics.nextStratum();

        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.scale(1.0f / mc.getWindow().getGuiScale());

        scissorStart(0, 0, getWindowWidth(), getWindowHeight());
    }

    public void end() {
        scissorEnd();

        for (Runnable task : postTasks) task.run();
        postTasks.clear();

        graphics.pose().popMatrix();
        graphics.nextStratum();
    }

    public void beginRender() {
        r.begin();
        rTex.begin();
    }

    public void endRender() {
        endRender(null);
    }

    public void endRender(Scissor scissor) {
        if (scissor != null) scissor.push();

        r.end();
        rTex.end();

        r.render();
        rTex.render("u_Texture", TEXTURE.getTextureView(), TEXTURE.getSampler());

        // Normal text
        theme.textRenderer().begin(graphics, theme.scale(1));
        for (TextOperation text : texts) {
            if (!text.title) text.run(textPool);
        }
        theme.textRenderer().end();

        // Title text
        theme.textRenderer().begin(graphics, theme.scale(1.25));
        for (TextOperation text : texts) {
            if (text.title) text.run(textPool);
        }
        theme.textRenderer().end();

        texts.clear();

        if (scissor != null) scissor.pop();
    }

    public void scissorStart(double x, double y, double width, double height) {
        if (!scissorStack.isEmpty()) {
            Scissor parent = scissorStack.top();

            if (x < parent.x) x = parent.x;
            else if (x + width > parent.x + parent.width) width -= (x + width) - (parent.x + parent.width);

            if (y < parent.y) y = parent.y;
            else if (y + height > parent.y + parent.height) height -= (y + height) - (parent.y + parent.height);

            endRender(parent);
        }

        scissorStack.push(scissorPool.get().set(x, y, width, height));
        graphics.enableScissor((int) x, (int) y, (int) (x + width), (int) (y + height));

        beginRender();
    }

    public void scissorEnd() {
        Scissor scissor = scissorStack.pop();

        endRender(scissor);

        scissor.push();
        for (Runnable task : scissor.postTasks) task.run();
        scissor.pop();

        graphics.disableScissor();
        if (!scissorStack.isEmpty()) beginRender();

        scissorPool.free(scissor);
    }

    public boolean renderTooltip(GuiGraphicsExtractor graphics, double mouseX, double mouseY, double delta) {
        tooltipAnimProgress += (tooltip != null ? 1 : -1) * delta * 14;
        tooltipAnimProgress = Mth.clamp(tooltipAnimProgress, 0, 1);

        boolean toReturn = false;

        if (tooltipAnimProgress > 0) {
            if (tooltip != null && !tooltip.equals(lastTooltip)) {
                tooltipWidget = theme.tooltip(tooltip);
                tooltipWidget.init();
            }

            double deltaX = -tooltipWidget.x + mouseX + 12;
            double deltaY = -tooltipWidget.y + mouseY + 12;

            if (mouseX + 12 + tooltipWidget.width > getWindowWidth())
                deltaX = -tooltipWidget.x + getWindowWidth() - tooltipWidget.width;
            if (mouseY + 12 + tooltipWidget.height > getWindowHeight())
                deltaY = -tooltipWidget.y + getWindowHeight() - tooltipWidget.height;

            tooltipWidget.move(deltaX, deltaY);

            setAlpha(tooltipAnimProgress);

            begin(graphics);
            tooltipWidget.render(this, mouseX, mouseY, delta);
            end();

            setAlpha(1);

            lastTooltip = tooltip;
            toReturn = true;
        }

        tooltip = null;
        return toReturn;
    }

    public void setAlpha(double a) {
        r.setAlpha(a);
        rTex.setAlpha(a);

        theme.textRenderer().setAlpha(a);
    }

    public void tooltip(String text) {
        tooltip = text;
    }

    public void quad(double x, double y, double width, double height, Color cTopLeft, Color cTopRight, Color cBottomRight, Color cBottomLeft) {
        r.quad(x, y, width, height, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public void quad(double x, double y, double width, double height, Color colorLeft, Color colorRight) {
        quad(x, y, width, height, colorLeft, colorRight, colorRight, colorLeft);
    }

    public void quad(double x, double y, double width, double height, Color color) {
        quad(x, y, width, height, color, color);
    }

    private final Color roundedTop = new Color(), roundedBottom = new Color(), roundedVertexColor = new Color(), roundedTransparent = new Color();
    private boolean roundedFlat;
    private double roundedY, roundedHeight;
    private final RoundedRectGeometry.TriangleSink roundedSink = this::roundedTriangle;

    /** Draw at framebuffer resolution with a one-pixel coverage fringe. */
    public void roundedQuad(double x, double y, double width, double height, double radius, Color color) {
        roundedQuad(x, y, width, height, radius, color, color);
    }

    /** A vertical color gradient, clipped to the same rounded coverage geometry. */
    public void roundedQuad(double x, double y, double width, double height, double radius, Color top, Color bottom) {
        if (!(width > 0 && height > 0) || !Double.isFinite(x + y + width + height + radius)) return;
        if (radius <= 0) { quad(x, y, width, height, top, top, bottom, bottom); return; }
        roundedFlat = top == bottom;
        roundedTop.set(top);
        roundedTransparent.set(top).a(0);
        roundedBottom.set(bottom);
        roundedY = y;
        roundedHeight = height;
        RoundedRectGeometry.draw(x, y, width, height, radius, roundedSink);
    }

    private int roundedVertex(double x, double y, int coverage) {
        if (roundedFlat) return r.triangles.vec2(x, y).color(coverage == 0 ? roundedTransparent : roundedTop).next();
        double t = Math.clamp((y - roundedY) / roundedHeight, 0, 1);
        roundedVertexColor.set(
            (int) Math.round(roundedTop.r + (roundedBottom.r - roundedTop.r) * t),
            (int) Math.round(roundedTop.g + (roundedBottom.g - roundedTop.g) * t),
            (int) Math.round(roundedTop.b + (roundedBottom.b - roundedTop.b) * t),
            coverage == 0 ? 0 : (int) Math.round(roundedTop.a + (roundedBottom.a - roundedTop.a) * t));
        return r.triangles.vec2(x, y).color(roundedVertexColor).next();
    }

    private void roundedTriangle(double x0, double y0, int a0, double x1, double y1, int a1,
                                 double x2, double y2, int a2) {
        r.triangles.ensureTriCapacity();
        r.triangles.triangle(roundedVertex(x0, y0, a0), roundedVertex(x1, y1, a1), roundedVertex(x2, y2, a2));
    }

    public void roundedQuad(WWidget widget, double radius, Color color) {
        roundedQuad(widget.x, widget.y, widget.width, widget.height, radius, color);
    }

    public void quad(WWidget widget, Color color) {
        quad(widget.x, widget.y, widget.width, widget.height, color);
    }

    public void quad(double x, double y, double width, double height, GuiTexture texture, Color color) {
        rTex.texQuad(x, y, width, height, texture.get(width, height), color);
    }

    public void rotatedQuad(double x, double y, double width, double height, double rotation, GuiTexture texture, Color color) {
        rTex.texQuad(x, y, width, height, rotation, texture.get(width, height), color);
    }

    public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, Color color) {
        r.triangle(x1, y1, x2, y2, x3, y3, color);
    }

    public void text(String text, double x, double y, Color color, boolean title) {
        texts.add(getOp(textPool, x, y, color).set(text, theme.textRenderer(), title));
    }

    public void texture(double x, double y, double width, double height, double rotation, Texture texture) {
        post(() -> {
            rTex.begin();
            rTex.texQuad(x, y, width, height, rotation, 0, 0, 1, 1, WHITE);
            rTex.end();

            rTex.render(texture.getTextureView(), texture.getSampler());
        });
    }

    public void post(Runnable task) {
        scissorStack.top().postTasks.add(task);
    }

    public void item(ItemStack itemStack, int x, int y, float scale, boolean overlay) {
        RenderUtils.drawItem(graphics, itemStack, x, y, scale, overlay, null, false);
    }

    public void absolutePost(Runnable task) {
        postTasks.add(task);
    }

    private <T extends GuiRenderOperation<T>> T getOp(Pool<T> pool, double x, double y, Color color) {
        T op = pool.get();
        op.set(x, y, color);
        return op;
    }
}
