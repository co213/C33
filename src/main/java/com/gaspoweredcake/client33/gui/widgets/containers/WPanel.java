package com.gaspoweredcake.client33.gui.widgets.containers;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.utils.render.color.Color;

/** A rounded, softly lit card with a small nature-colored identity marker. */
public class WPanel extends WVerticalList {
    public enum Tone { Leaf, Sky, Sun }
    private static final Color BACKGROUND = Color.fromHsv(175, 0.60, 0.12, 252);
    private static final Color BORDER = Color.fromHsv(175, 0.60, 0.32);
    private final Tone tone;

    public WPanel() { this(Tone.Leaf); }
    public WPanel(Tone tone) { this.tone = tone; }

    @Override protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Client33GuiTheme palette = theme instanceof Client33GuiTheme nature ? nature : null;
        Color background = palette != null ? palette.insetBackground.get() : BACKGROUND;
        Color border = palette != null ? palette.insetOutline.get() : BORDER;
        Color highlight = palette != null ? palette.insetHighlight.get() : background;
        double inset = theme.scale(1), radius = theme.scale(8);
        renderer.roundedQuad(x, y, width, height, radius, border);
        renderer.roundedQuad(x + inset, y + inset, width - inset * 2, height - inset * 2,
            radius - inset, highlight, background);
        if (palette != null && width > theme.scale(24)) {
            Color accent = switch (tone) {
                case Leaf -> palette.accentColor.get();
                case Sky -> palette.skyAccent.get();
                case Sun -> palette.favoriteColor.get();
            };
            renderer.roundedQuad(x + theme.scale(12), y + theme.scale(3),
                Math.min(theme.scale(44), width - theme.scale(24)), theme.scale(2), theme.scale(1), accent);
        }
    }
}
