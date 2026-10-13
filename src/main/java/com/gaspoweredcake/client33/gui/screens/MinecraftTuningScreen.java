package com.gaspoweredcake.client33.gui.screens;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.WindowScreen;
import com.gaspoweredcake.client33.gui.widgets.containers.WHorizontalList;
import com.gaspoweredcake.client33.gui.widgets.containers.WSection;
import com.gaspoweredcake.client33.gui.widgets.containers.WTable;
import com.gaspoweredcake.client33.gui.widgets.input.WDoubleEdit;
import com.gaspoweredcake.client33.gui.widgets.input.WDropdown;
import com.gaspoweredcake.client33.gui.widgets.input.WIntEdit;
import com.gaspoweredcake.client33.gui.widgets.pressable.WCheckbox;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.server.level.ParticleStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.gaspoweredcake.client33.Client33.mc;

/** Edits Minecraft's native options through their validators and change callbacks. */
public final class MinecraftTuningScreen extends WindowScreen {
    private final List<Runnable> motionUndo = new ArrayList<>();
    private WDropdown<String> graphics;
    private boolean refreshOnReturn;

    public MinecraftTuningScreen(GuiTheme theme) {
        super(theme, "Minecraft Tuning");
    }

    @Override protected void init() {
        super.init();
        if (refreshOnReturn) {
            refreshOnReturn = false;
            reload();
        }
    }

    @Override public void initWidgets() {
        add(theme.label("Native Minecraft settings. Changes save when you leave.", 420)
            .color(theme.textSecondaryColor())).expandX();
        WSection display = add(theme.section("Lighting & depth", true)).expandX().widget();
        WTable quality = display.add(theme.table()).expandX().widget();
        graphics = pick(quality, "Graphics preset", mc.options.graphicsPreset(), GraphicsPreset.values(),
            new String[] {"Fast", "Fancy", "Fabulous", "Custom"}, "Minecraft's own preset; it also changes view distance and other graphics options.");
        graphics.action = () -> {
            mc.options.graphicsPreset().set(GraphicsPreset.values()[Arrays.asList("Fast", "Fancy", "Fabulous", "Custom").indexOf(graphics.get())]);
            taskAfterRender = this::reload;
        };
        integer(quality, "View distance (chunks)", mc.options.renderDistance(), 2, 32, "Server view distance still limits received terrain.");
        toggle(quality, "Smooth lighting", mc.options.ambientOcclusion(), "Adds softer shading and depth around blocks.");
        toggle(quality, "Improved transparency", mc.options.improvedTransparency(), "Use Minecraft's improved rendering for overlapping transparent surfaces.");
        toggle(quality, "Vignette", mc.options.vignette(), "Subtle darkening at the edges of the view.");
        pick(quality, "Clouds", mc.options.cloudStatus(), CloudStatus.values(), new String[] {"Off", "Fast", "Fancy"}, "Native cloud quality.");
        pick(quality, "Particles", mc.options.particles(), ParticleStatus.values(), new String[] {"All", "Decreased", "Minimal"}, "Fewer particles can improve frame rate in busy scenes.");
        number(quality, "Chunk fade (seconds)", mc.options.chunkSectionFadeInTime(), 0, 2, 1, "Fade newly rendered chunk sections into view.");
        number(quality, "Brightness (%)", mc.options.gamma(), 0, 1, 100, "Vanilla brightness. Fullbright can override its appearance.");

        WSection comfort = add(theme.section("Camera & motion", true)).expandX().widget();
        WTable camera = comfort.add(theme.table()).expandX().widget();
        integer(camera, "Field of view (°)", mc.options.fov(), 30, 110, "The base field of view; Zoom and other modules may modify it.");
        toggle(camera, "View bobbing", mc.options.bobView(), "Camera motion while walking.");
        number(camera, "FOV effects (%)", mc.options.fovEffectScale(), 0, 1, 100, "Strength of sprint and movement field-of-view changes.");
        number(camera, "Damage tilt (%)", mc.options.damageTiltStrength(), 0, 1, 100, "Strength of camera tilt when taking damage.");
        number(camera, "Screen effects (%)", mc.options.screenEffectScale(), 0, 1, 100, "Strength of portal and nausea effects.");
        number(camera, "Darkness effects (%)", mc.options.darknessEffectScale(), 0, 1, 100, "Strength of the pulsing darkness effect.");
        toggle(camera, "Hide lightning flashes", mc.options.hideLightningFlash(), "Reduce the bright screen flash during lightning.");
        WHorizontalList motion = comfort.add(theme.horizontalList()).expandX().widget();
        motion.add(theme.button("Gentle motion")).expandX().widget().action = this::gentleMotion;
        motion.add(theme.button("Undo motion preset")).expandX().widget().action = () -> {
            motionUndo.forEach(Runnable::run);
            motionUndo.clear();
            taskAfterRender = this::reload;
        };

        WSection performance = add(theme.section("Performance", false)).expandX().widget();
        WTable limits = performance.add(theme.table()).expandX().widget();
        integer(limits, "Simulation distance (chunks)", mc.options.simulationDistance(), 5, 32, "Local simulation distance; multiplayer simulation is controlled by the server.");
        integer(limits, "Frame-rate limit", mc.options.framerateLimit(), 10, 260, "The highest value is Minecraft's unlimited setting.");
        integer(limits, "Cloud range", mc.options.cloudRange(), 0, 64, "Cloud rendering range; larger values require more work.");
        integer(limits, "Weather radius", mc.options.weatherRadius(), 0, 32, "Range for visible weather effects.");

        WHorizontalList bottom = add(theme.horizontalList()).expandX().widget();
        bottom.add(theme.button("All Minecraft options")).expandX().widget().action = () ->
            mc.gui.setScreen(new OptionsScreen(this, mc.options, mc.level != null));
        bottom.add(theme.button("Back")).expandX().widget().action = this::onClose;
    }

    private void updatePresetLabel() {
        if (graphics != null) graphics.set(switch (mc.options.graphicsPreset().get()) {
            case FAST -> "Fast";
            case FANCY -> "Fancy";
            case FABULOUS -> "Fabulous";
            case CUSTOM -> "Custom";
        });
    }

    private void toggle(WTable table, String label, OptionInstance<Boolean> option, String tooltip) {
        table.add(theme.label(label)).widget().tooltip = tooltip;
        WCheckbox edit = table.add(theme.checkbox(option.get())).right().widget();
        edit.action = () -> { option.set(edit.checked); edit.checked = option.get(); updatePresetLabel(); };
        table.row();
    }

    private void integer(WTable table, String label, OptionInstance<Integer> option, int min, int max, String tooltip) {
        if (option.values() instanceof OptionInstance.SliderableValueSet<Integer> range) {
            int first = range.fromSliderValue(0), last = range.fromSliderValue(1);
            min = Math.min(first, last); max = Math.max(first, last);
        }
        table.add(theme.label(label)).widget().tooltip = tooltip;
        WIntEdit edit = table.add(theme.intEdit(option.get(), min, max, min, max, false)).expandX().widget();
        // Rebuild-heavy native options commit when the slider is released or text loses focus.
        edit.actionOnRelease = () -> { option.set(edit.get()); edit.set(option.get()); updatePresetLabel(); };
        table.row();
    }

    private void number(WTable table, String label, OptionInstance<Double> option, double min, double max, double multiplier, String tooltip) {
        table.add(theme.label(label)).widget().tooltip = tooltip;
        WDoubleEdit edit = table.add(theme.doubleEdit(option.get() * multiplier, min * multiplier, max * multiplier,
            min * multiplier, max * multiplier, 1, false)).expandX().widget();
        edit.actionOnRelease = () -> { option.set(edit.get() / multiplier); edit.set(option.get() * multiplier); updatePresetLabel(); };
        table.row();
    }

    private <T> WDropdown<String> pick(WTable table, String label, OptionInstance<T> option, T[] values, String[] labels, String tooltip) {
        table.add(theme.label(label)).widget().tooltip = tooltip;
        WDropdown<String> edit = table.add(theme.dropdown(labels, labels[Arrays.asList(values).indexOf(option.get())])).expandX().widget();
        edit.action = () -> { option.set(values[Arrays.asList(labels).indexOf(edit.get())]); updatePresetLabel(); };
        table.row();
        return edit;
    }

    private <T> void remember(OptionInstance<T> option) {
        T previous = option.get();
        motionUndo.add(() -> option.set(previous));
    }

    private void gentleMotion() {
        motionUndo.clear();
        remember(mc.options.bobView()); remember(mc.options.fovEffectScale());
        remember(mc.options.damageTiltStrength()); remember(mc.options.screenEffectScale());
        remember(mc.options.darknessEffectScale()); remember(mc.options.hideLightningFlash());
        mc.options.bobView().set(false); mc.options.fovEffectScale().set(0.25);
        mc.options.damageTiltStrength().set(0.3); mc.options.screenEffectScale().set(0.35);
        mc.options.darknessEffectScale().set(0.5); mc.options.hideLightningFlash().set(true);
        taskAfterRender = this::reload;
    }

    @Override public void removed() {
        super.removed();
        // WidgetScreen commits focused editors during removal; save those changes too.
        mc.options.save();
        refreshOnReturn = true;
    }
}
