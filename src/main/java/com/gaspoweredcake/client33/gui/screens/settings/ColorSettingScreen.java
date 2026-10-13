/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.screens.settings;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.WidgetScreen;
import com.gaspoweredcake.client33.gui.WindowScreen;
import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.widgets.WQuad;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WHorizontalList;
import com.gaspoweredcake.client33.gui.widgets.containers.WTable;
import com.gaspoweredcake.client33.gui.widgets.input.WDoubleEdit;
import com.gaspoweredcake.client33.gui.widgets.pressable.WButton;
import com.gaspoweredcake.client33.gui.widgets.pressable.WCheckbox;
import com.gaspoweredcake.client33.settings.Setting;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

import static com.gaspoweredcake.client33.Client33.mc;

public class ColorSettingScreen extends WindowScreen {
    private static final Color[] HUE_COLORS = {Color.fromHsv(0, 1, 1), Color.fromHsv(60, 1, 1), Color.fromHsv(120, 1, 1), Color.fromHsv(180, 1, 1), Color.fromHsv(240, 1, 1), Color.fromHsv(300, 1, 1), Color.fromHsv(360, 1, 1)};
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color BLACK = new Color(0, 0, 0);

    public Runnable action;

    private final Setting<SettingColor> setting;

    private WQuad displayQuad;

    private WBrightnessQuad brightnessQuad;
    private WHueQuad hueQuad;

    private WDoubleEdit hueEdit, saturationEdit, brightnessEdit, opacityEdit;
    private WCheckbox rainbow;

    public ColorSettingScreen(GuiTheme theme, Setting<SettingColor> setting) {
        super(theme, "Select Color");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        // Top
        displayQuad = add(theme.quad(setting.get())).expandX().widget();

        brightnessQuad = add(new WBrightnessQuad()).expandX().widget();

        hueQuad = add(new WHueQuad()).expandX().widget();

        // HSV / HSB: hue in degrees, saturation, brightness and opacity in percent.
        double[] hsv = setting.get().toHsv();
        WTable table = add(theme.table()).expandX().widget();
        hueEdit = component(table, "Hue (°):", hsv[0], 360);
        saturationEdit = component(table, "Saturation (%):", hsv[1] * 100, 100);
        brightnessEdit = component(table, "Brightness (%):", hsv[2] * 100, 100);
        opacityEdit = component(table, "Opacity (%):", setting.get().a / 255.0 * 100, 100);
        hueEdit.action = saturationEdit.action = brightnessEdit.action = () -> {
            hueQuad.hueAngle = hueEdit.get();
            brightnessQuad.saturation = saturationEdit.get() / 100;
            brightnessQuad.value = brightnessEdit.get() / 100;
            updateHandles();
            hsvChanged();
        };
        opacityEdit.action = () -> {
            setting.get().a((int) Math.round(opacityEdit.get() / 100 * 255));
            displayQuad.color.set(setting.get());
            setting.onChanged();
            callAction();
        };

        // Rainbow
        WHorizontalList rainbowList = add(theme.horizontalList()).expandX().widget();
        rainbowList.add(theme.label("Rainbow: "));
        rainbow = theme.checkbox(setting.get().rainbow);
        rainbow.action = () -> {
            setting.get().rainbow = rainbow.checked;
            setting.onChanged();
        };
        rainbowList.add(rainbow).expandCellX().right();

        // Bottom
        WHorizontalList bottomList = add(theme.horizontalList()).expandX().widget();

        WButton backButton = bottomList.add(theme.button("Back")).expandX().widget();
        backButton.action = this::onClose;

        WButton copyButton = bottomList.add(theme.button(GuiRenderer.COPY)).widget();
        copyButton.action = this::toClipboard;
        copyButton.tooltip = "Copy config";

        WButton pasteButton = bottomList.add(theme.button(GuiRenderer.PASTE)).widget();
        pasteButton.action = this::fromClipboard;
        pasteButton.tooltip = "Paste config";

        WButton resetButton = bottomList.add(theme.button(GuiRenderer.RESET)).widget();
        resetButton.action = () -> {
            setting.reset();
            setFromSetting();
            callAction();
        };
        resetButton.tooltip = "Reset";

        hueQuad.calculateFromSetting(false);
        brightnessQuad.calculateFromColor(setting.get(), false);
    }

    private WDoubleEdit component(WTable table, String label, double value, double max) {
        table.add(theme.label(label));
        WDoubleEdit edit = table.add(theme.doubleEdit(value, 0, max, 0, max, 1, false)).expandX().widget();
        table.row();
        return edit;
    }

    private void updateHandles() {
        hueQuad.handleX = hueQuad.hueAngle / 360 * hueQuad.width;
        brightnessQuad.handleX = brightnessQuad.saturation * brightnessQuad.width;
        brightnessQuad.handleY = (1 - brightnessQuad.value) * brightnessQuad.height;
    }

    private void updateFields() {
        hueEdit.set(hueQuad.hueAngle);
        saturationEdit.set(brightnessQuad.saturation * 100);
        brightnessEdit.set(brightnessQuad.value * 100);
        opacityEdit.set(setting.get().a / 255.0 * 100);
    }

    private void setFromSetting() {
        rainbow.checked = setting.get().rainbow;
        displayQuad.color.set(setting.get());
        hueQuad.calculateFromSetting(true);
        brightnessQuad.calculateFromColor(setting.get(), true);
        updateFields();
    }

    private void callAction() {
        if (action != null) action.run();
    }

    @Override
    public void tick() {
        super.tick();
        if (setting.get().rainbow) setFromSetting();
    }

    private void hsvChanged() {
        SettingColor c = setting.get();
        // Keep the selected hue at zero saturation/brightness, and preserve alpha/rainbow.
        c.set(Color.fromHsv(hueQuad.hueAngle, brightnessQuad.saturation, brightnessQuad.value, c.a));
        updateFields();
        displayQuad.color.set(c);
        setting.onChanged();
        callAction();
    }

    private class WBrightnessQuad extends WWidget {
        double saturation, value;

        double handleX, handleY;

        boolean dragging;
        double lastMouseX, lastMouseY;

        double fixedHeight = -1;

        @Override
        protected void onCalculateSize() {
            double s = theme.scale(75);

            width = s;
            height = s;

            if (fixedHeight != -1) {
                height = fixedHeight;
                fixedHeight = -1;
            }
        }

        void calculateFromColor(Color c, boolean calculateNow) {
            double[] hsv = c.toHsv();
            saturation = hsv[1];
            value = hsv[2];

            if (calculateNow) {
                handleX = saturation * width;
                handleY = (1 - value) * height;
            }
        }

        @Override
        public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) {
            if (doubled) return false;

            if (mouseOver) {
                dragging = true;
                setFocused(true);

                handleX = lastMouseX - x;
                handleY = lastMouseY - y;
                handleMoved();

                return true;
            }

            return false;
        }

        @Override
        public boolean onMouseReleased(MouseButtonEvent click) {
            if (dragging) {
                dragging = false;
                setFocused(false);
            }

            return false;
        }

        @Override
        public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
            if (dragging) {
                if (mouseX >= this.x && mouseX <= this.x + width) {
                    handleX += mouseX - lastMouseX;
                } else {
                    if (handleX > 0 && mouseX < this.x) handleX = 0;
                    else if (handleX < width && mouseX > this.x + width) handleX = width;
                }

                if (mouseY >= this.y && mouseY <= this.y + height) {
                    handleY += mouseY - lastMouseY;
                } else {
                    if (handleY > 0 && mouseY < this.y) handleY = 0;
                    else if (handleY < height && mouseY > this.y + height) handleY = height;
                }

                handleMoved();
            }

            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
        }

        void handleMoved() {
            double handleXPercentage = Mth.clamp(handleX / width, 0, 1);
            double handleYPercentage = Mth.clamp(handleY / height, 0, 1);

            saturation = handleXPercentage;
            value = 1 - handleYPercentage;

            hsvChanged();
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (height != Math.round(width)) {
                fixedHeight = Math.round(width);
                invalidate();

                handleX = saturation * width;
                handleY = (1 - value) * fixedHeight;
            }

            hueQuad.calculateColor();

            renderer.quad(x, y, width, height, WHITE, hueQuad.color, BLACK, BLACK);

            double s = theme.scale(2);
            renderer.quad(x + handleX - s / 2, y + handleY - s / 2, s, s, WHITE);
        }
    }

    @Override
    public boolean toClipboard() {
        return NbtUtils.toClipboard(setting.get());
    }

    @Override
    public boolean fromClipboard() {
        if (NbtUtils.fromClipboard(setting.get())) {
            // The color was deserialised onto the existing object, so Setting#set -
            // and with it onChanged() - was never reached.
            setting.get().validate();
            setting.onChanged();
        } else {
            String clipboard = mc.keyboardHandler.getClipboard().trim();
            SettingColor parsed;

            parsed = parseRGBA(clipboard);
            if (parsed == null) {
                parsed = parseHex(clipboard);
                if (parsed == null) return false;
            }

            setting.set(parsed);
        }

        callAction();

        if (parent instanceof WidgetScreen p) {
            p.reload();
        }
        reload();

        return true;
    }

    private SettingColor parseRGBA(String string) {
        String[] rgba = string.replaceAll("[^0-9|,]", "").split(",");
        if (rgba.length < 3 || rgba.length > 4) return null;

        SettingColor color;
        try {
            color = new SettingColor(Integer.parseInt(rgba[0]), Integer.parseInt(rgba[1]), Integer.parseInt(rgba[2]));
            if (rgba.length == 4) color.a = Integer.parseInt(rgba[3]);
        } catch (NumberFormatException _) {
            return null;
        }

        return color;
    }

    private SettingColor parseHex(String string) {
        if (!string.startsWith("#")) return null;
        String hex = string.toLowerCase(java.util.Locale.ROOT).replaceAll("[^0-9a-f]", "");
        if (hex.length() != 6 && hex.length() != 8) return null;

        SettingColor color;
        try {
            color = new SettingColor(Integer.parseInt(hex.substring(0, 2), 16), Integer.parseInt(hex.substring(2, 4), 16), Integer.parseInt(hex.substring(4, 6), 16));
            if (hex.length() == 8) color.a = Integer.parseInt(hex.substring(6, 8), 16);
        } catch (NumberFormatException _) {
            return null;
        }

        return color;
    }

    private class WHueQuad extends WWidget {
        private double hueAngle;
        private double handleX;

        private final Color color = new Color();

        private boolean dragging;
        private double lastMouseX;

        private boolean calculateHandleXOnLayout;

        @Override
        protected void onCalculateSize() {
            width = theme.scale(75);
            height = theme.scale(10);
        }

        void calculateFromSetting(boolean calculateNow) {
            double[] hsv = setting.get().toHsv();
            // Hue is undefined for gray/black; retain it so saturation can be restored.
            if (hsv[1] > 0) hueAngle = hsv[0];

            if (calculateNow) {
                double huePercentage = hueAngle / 360;
                handleX = huePercentage * width;
            } else {
                calculateHandleXOnLayout = true;
            }
        }

        @Override
        protected void onCalculateWidgetPositions() {
            if (calculateHandleXOnLayout) {
                double huePercentage = hueAngle / 360;
                handleX = huePercentage * width;

                calculateHandleXOnLayout = false;
            }

            super.onCalculateWidgetPositions();
        }

        void calculateColor() {
            color.set(Color.fromHsv(hueAngle, 1, 1));
        }

        @Override
        public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) {
            if (doubled) return false;

            if (mouseOver) {
                dragging = true;
                setFocused(true);

                handleX = lastMouseX - x;
                calculateHueAngleFromHandleX();
                hsvChanged();

                return true;
            }

            return false;
        }

        @Override
        public boolean onMouseReleased(MouseButtonEvent click) {
            if (dragging) {
                dragging = false;
                setFocused(false);
            }

            return mouseOver;
        }

        @Override
        public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
            if (dragging) {
                if (mouseX >= this.x && mouseX <= this.x + width) {
                    handleX += mouseX - lastMouseX;
                    handleX = Mth.clamp(handleX, 0, width);
                } else {
                    if (handleX > 0 && mouseX < this.x) handleX = 0;
                    else if (handleX < width && mouseX > this.x + width) handleX = width;
                }

                calculateHueAngleFromHandleX();
                hsvChanged();
            }

            this.lastMouseX = mouseX;
        }

        void calculateHueAngleFromHandleX() {
            double handleXPercentage = Mth.clamp(handleX / width, 0, 1);
            hueAngle = handleXPercentage * 360;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double sectionWidth = (width) / (HUE_COLORS.length - 1);
            double sectionX = x;

            for (int i = 0; i < HUE_COLORS.length - 1; i++) {
                renderer.quad(sectionX, y, sectionWidth, height, HUE_COLORS[i], HUE_COLORS[i + 1], HUE_COLORS[i + 1], HUE_COLORS[i]);
                sectionX += sectionWidth;
            }

            double s = theme.scale(2);
            renderer.quad(x + handleX - s / 2, y, s, height, WHITE);
        }
    }
}
