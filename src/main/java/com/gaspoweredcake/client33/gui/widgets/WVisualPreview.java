package com.gaspoweredcake.client33.gui.widgets;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.render.CombatVisuals;
import com.gaspoweredcake.client33.systems.modules.render.ESP;
import com.gaspoweredcake.client33.utils.render.color.Color;

/** Animated schematic driven by the actual settings; works without loading a world. */
public class WVisualPreview extends WWidget {
    private final boolean combat;
    private double time;
    private static final Color GRID = Color.fromHsv(205, 0.24, 0.20);
    private static final Color BODY = Color.fromHsv(205, 0.26, 0.38);
    public WVisualPreview(boolean combat) { this.combat = combat; }
    @Override protected void onCalculateSize() { width = theme.scale(230); height = theme.scale(155); }
    @Override protected void onRender(GuiRenderer r, double mx, double my, double delta) {
        time += Math.min(delta, 0.1);
        ESP esp = Modules.get().get(ESP.class);
        CombatVisuals effects = Modules.get().get(CombatVisuals.class);
        Color primary = combat ? effects.primary.get() : esp.signatureAccent.get();
        double cx = x + width / 2, top = y + theme.scale(12), unit = theme.scale(0.9);
        for (int i = 1; i < 6; i++) r.quad(x + width * i / 6, y + unit * 10, unit, height - unit * 20, GRID);
        for (int i = 1; i < 5; i++) r.quad(x + unit * 8, y + height * i / 5, width - unit * 16, unit, GRID);
        r.quad(cx - 10 * unit, top, 20 * unit, 20 * unit, BODY);
        r.quad(cx - 15 * unit, top + 24 * unit, 30 * unit, 43 * unit, BODY);
        r.quad(cx - 28 * unit, top + 25 * unit, 10 * unit, 43 * unit, BODY);
        r.quad(cx + 18 * unit, top + 25 * unit, 10 * unit, 43 * unit, BODY);
        r.quad(cx - 14 * unit, top + 70 * unit, 12 * unit, 37 * unit, BODY);
        r.quad(cx + 2 * unit, top + 70 * unit, 12 * unit, 37 * unit, BODY);
        double phase = combat ? time * effects.speed.get() : esp.animate.get() ? time : 0;
        if (combat || esp.mode.get() == ESP.Mode.Hologram || esp.mode.get() == ESP.Mode.Constellation) {
            if (combat ? effects.targetOrbit.get() : esp.groundMarker.get()) ellipse(r, cx, top + 108 * unit, 53 * unit, 12 * unit, phase, primary, true);
            if (combat ? effects.scanBand.get() : esp.mode.get() == ESP.Mode.Hologram)
                ellipse(r, cx, top + (55 + Math.sin(phase * 2) * 43) * unit, 41 * unit, 9 * unit, 0, primary, false);
            if (!combat && esp.mode.get() == ESP.Mode.Constellation) for (int i = 0; i < 60; i++) {
                double t = i / 59.0, a = phase + t * Math.PI * 4;
                r.quad(cx + Math.cos(a) * 37 * unit, top + t * 108 * unit, 3 * unit, 3 * unit, primary);
            }
            if (combat && effects.crown.get()) r.quad(cx - 3 * unit, top - 9 * unit, 6 * unit, 6 * unit, effects.secondary.get());
            if (combat && effects.attackBursts.get()) ellipse(r, cx, top + 60 * unit, (25 + (time % 1) * 40) * unit, 15 * unit, 0,
                new Color(primary).a((int) (primary.a * (1 - time % 1))), false);
        } else {
            double left = cx - 34 * unit, right = cx + 34 * unit, bottom = top + 111 * unit;
            r.quad(left, top - 3 * unit, 14 * unit, unit, primary);
            r.quad(right - 14 * unit, top - 3 * unit, 14 * unit, unit, primary);
            r.quad(left, top - 3 * unit, unit, 15 * unit, primary);
            r.quad(right, top - 3 * unit, unit, 15 * unit, primary);
            r.quad(left, bottom, 14 * unit, unit, primary);
            r.quad(right - 14 * unit, bottom, 14 * unit, unit, primary);
            if (esp.mode.get() == ESP.Mode.Telemetry) {
                r.quad(left, top + (Math.sin(phase * 2) + 1) * 50 * unit, right - left, unit, primary);
                for (int i = 0; i < 6; i++) r.quad(right + 5 * unit, top + i * 21 * unit, 5 * unit, unit, primary);
            }
        }
        r.text("SCHEMATIC / " + (combat ? "COMBAT" : esp.mode.get()), x + unit * 8, y + height - theme.textHeight() - unit * 3, theme.textSecondaryColor(), false);
    }
    private void ellipse(GuiRenderer r, double cx, double cy, double rx, double ry, double phase, Color color, boolean broken) {
        for (int i = 0; i < 90; i++) {
            if (broken && i % 30 > 21) continue;
            double a = i * Math.PI * 2 / 90 + phase;
            r.quad(cx + Math.cos(a) * rx, cy + Math.sin(a) * ry, theme.scale(2), theme.scale(2), color);
        }
    }
}
