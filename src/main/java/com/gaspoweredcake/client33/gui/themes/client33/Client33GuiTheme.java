/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33;

import com.gaspoweredcake.client33.gui.DefaultSettingsWidgetFactory;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.WidgetScreen;
import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.renderer.packer.GuiTexture;
import com.gaspoweredcake.client33.gui.themes.client33.widgets.*;
import com.gaspoweredcake.client33.gui.themes.client33.widgets.input.WClient33Dropdown;
import com.gaspoweredcake.client33.gui.themes.client33.widgets.input.WClient33Slider;
import com.gaspoweredcake.client33.gui.themes.client33.widgets.input.WClient33TextBox;
import com.gaspoweredcake.client33.gui.themes.client33.widgets.pressable.*;
import com.gaspoweredcake.client33.gui.utils.AlignmentX;
import com.gaspoweredcake.client33.gui.utils.CharFilter;
import com.gaspoweredcake.client33.gui.widgets.*;
import com.gaspoweredcake.client33.gui.widgets.containers.WSection;
import com.gaspoweredcake.client33.gui.widgets.containers.WView;
import com.gaspoweredcake.client33.gui.widgets.containers.WWindow;
import com.gaspoweredcake.client33.gui.widgets.input.WDropdown;
import com.gaspoweredcake.client33.gui.widgets.input.WSlider;
import com.gaspoweredcake.client33.gui.widgets.input.WTextBox;
import com.gaspoweredcake.client33.gui.widgets.pressable.*;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.accounts.Account;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import com.mojang.blaze3d.platform.MacosUtil;

import static com.gaspoweredcake.client33.Client33.mc;

public class Client33GuiTheme extends GuiTheme {
    // HSV keeps surface brightness and accent saturation consistent across states.
    private static final double SURFACE_HUE = 205;
    private static final double ACCENT_HUE = 128;
    private static final Color SCREEN_SKY = Color.fromHsv(205, 0.65, 0.30, 170);
    private static final Color SCREEN_LEAF = Color.fromHsv(155, 0.65, 0.20, 185);
    private static final Color SCREEN_SUN = Color.fromHsv(48, 0.50, 0.22, 180);
    private static final String SCREEN_MARK = "33";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgTextColors = settings.createGroup("Text");
    private final SettingGroup sgBackgroundColors = settings.createGroup("Background");
    private final SettingGroup sgOutline = settings.createGroup("Outline");
    private final SettingGroup sgSeparator = settings.createGroup("Separator");
    private final SettingGroup sgScrollbar = settings.createGroup("Scrollbar");
    private final SettingGroup sgSlider = settings.createGroup("Slider");
    private final SettingGroup sgStarscript = settings.createGroup("Starscript");

    // General

    public final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the GUI.")
        .defaultValue(1)
        .min(0.75)
        .sliderRange(0.75, 4)
        .onSliderRelease()
        .onChanged(aDouble -> {
            if (mc.gui.screen() instanceof WidgetScreen) ((WidgetScreen) mc.gui.screen()).invalidate();
        })
        .build()
    );

    public final Setting<AlignmentX> moduleAlignment = sgGeneral.add(new EnumSetting.Builder<AlignmentX>()
        .name("module-alignment")
        .description("How module titles are aligned.")
        .defaultValue(AlignmentX.Left)
        .build()
    );

    public final Setting<Boolean> categoryIcons = sgGeneral.add(new BoolSetting.Builder()
        .name("category-icons")
        .description("Adds item icons to module categories.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> hideHUD = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-HUD")
        .description("Hide HUD when in GUI.")
        .defaultValue(false)
        .onChanged(v -> {
            if (mc.gui.screen() instanceof WidgetScreen) mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden = v;
        })
        .build()
    );

    public final Setting<Boolean> modulesHelpText = sgGeneral.add(new BoolSetting.Builder()
        .name("modules-help-text")
        .description("Toggle help text in the modules screen.")
        .defaultValue(true)
        .build()
    );

    // Colors

    public final Setting<SettingColor> accentColor = color("accent", "Main color of the GUI.", hsv(ACCENT_HUE, 0.55, 0.96));
    public final Setting<SettingColor> checkboxColor = color("checkbox", "Color of checkbox.", hsv(ACCENT_HUE, 0.55, 0.96));
    public final Setting<SettingColor> plusColor = color("plus", "Color of plus button.", hsv(ACCENT_HUE, 0.48, 0.96));
    public final Setting<SettingColor> minusColor = color("minus", "Color of minus button.", hsv(38, 0.42, 0.96));
    public final Setting<SettingColor> favoriteColor = color("favorite", "Color of checked favorite button.", hsv(48, 0.60, 1));

    public final Setting<SettingColor> skyAccent = color("sky-accent", "Sky blue accents on studio cards.", hsv(205, 0.48, 1));

    // Text

    public final Setting<SettingColor> textColor = color(sgTextColors, "text", "Color of text.", hsv(48, 0.06, 0.98));
    public final Setting<SettingColor> textSecondaryColor = color(sgTextColors, "text-secondary-text", "Color of secondary text.", hsv(185, 0.12, 0.85));
    public final Setting<SettingColor> textHighlightColor = color(sgTextColors, "text-highlight", "Color of text highlighting.", hsv(ACCENT_HUE, 0.40, 0.90, 90));
    public final Setting<SettingColor> titleTextColor = color(sgTextColors, "title-text", "Color of title text.", hsv(48, 0.28, 1));
    public final Setting<SettingColor> loggedInColor = color(sgTextColors, "logged-in-text", "Color of logged in account name.", hsv(ACCENT_HUE, 0.55, 0.96));
    public final Setting<SettingColor> placeholderColor = color(sgTextColors, "placeholder", "Color of placeholder text.", hsv(185, 0.12, 0.85));

    // Background

    public final ThreeStateColorSetting backgroundColor = new ThreeStateColorSetting(
            sgBackgroundColors,
            "background",
            hsv(190, 0.65, 0.19, 252),
            hsv(185, 0.62, 0.26, 252),
            hsv(155, 0.65, 0.32)
    );

    public final Setting<SettingColor> moduleBackground = color(sgBackgroundColors, "module-background", "Color of module background when active.", hsv(150, 0.65, 0.32, 250));

    public final Setting<SettingColor> headerBackground = color(sgBackgroundColors, "header-background", "Elevated blue surface for window headers.", hsv(195, 0.65, 0.30));
    public final Setting<SettingColor> insetBackground = color(sgBackgroundColors, "inset-background", "Deep blue surface for inset control groups.", hsv(175, 0.60, 0.12, 252));
    public final Setting<SettingColor> insetOutline = color(sgBackgroundColors, "inset-outline", "Subtle blue border around inset control groups.", hsv(175, 0.60, 0.32));
    public final Setting<SettingColor> windowShadow = color(sgBackgroundColors, "window-shadow", "Soft shadow beneath elevated windows.", hsv(SURFACE_HUE, 0.35, 0.02, 95));

    public final Setting<SettingColor> headerHighlight = color(sgBackgroundColors, "header-highlight", "Sky blue light at the top of window headers.", hsv(195, 0.65, 0.36));
    public final Setting<SettingColor> insetHighlight = color(sgBackgroundColors, "inset-highlight", "Soft teal light across the top of cards.", hsv(165, 0.60, 0.23));

    // Outline

    public final ThreeStateColorSetting outlineColor = new ThreeStateColorSetting(
            sgOutline,
            "outline",
            hsv(190, 0.52, 0.42),
            hsv(ACCENT_HUE, 0.48, 0.68),
            hsv(ACCENT_HUE, 0.55, 0.96)
    );

    // Separator

    public final Setting<SettingColor> separatorText = color(sgSeparator, "separator-text", "Color of separator text", hsv(48, 0.26, 0.95));
    public final Setting<SettingColor> separatorCenter = color(sgSeparator, "separator-center", "Center color of separators.", hsv(ACCENT_HUE, 0.45, 0.70, 190));
    public final Setting<SettingColor> separatorEdges = color(sgSeparator, "separator-edges", "Color of separator edges.", hsv(205, 0.50, 0.55, 85));

    // Scrollbar

    public final ThreeStateColorSetting scrollbarColor = new ThreeStateColorSetting(
            sgScrollbar,
            "Scrollbar",
            hsv(205, 0.50, 0.58, 220),
            hsv(ACCENT_HUE, 0.28, 0.60, 220),
            hsv(ACCENT_HUE, 0.48, 0.86, 235)
    );

    // Slider

    public final ThreeStateColorSetting sliderHandle = new ThreeStateColorSetting(
            sgSlider,
            "slider-handle",
            hsv(ACCENT_HUE, 0.55, 0.96),
            hsv(48, 0.32, 0.98),
            hsv(48, 0.28, 1)
    );

    public final Setting<SettingColor> sliderLeft = color(sgSlider, "slider-left", "Color of slider left part.", hsv(ACCENT_HUE, 0.60, 0.88));
    public final Setting<SettingColor> sliderRight = color(sgSlider, "slider-right", "Color of slider right part.", hsv(195, 0.60, 0.30));

    // Starscript

    private final Setting<SettingColor> starscriptText = color(sgStarscript, "starscript-text", "Color of text in Starscript code.", hsv(145, 0.12, 0.88));
    private final Setting<SettingColor> starscriptBraces = color(sgStarscript, "starscript-braces", "Color of braces in Starscript code.", hsv(145, 0.36, 0.86));
    private final Setting<SettingColor> starscriptParenthesis = color(sgStarscript, "starscript-parenthesis", "Color of parenthesis in Starscript code.", hsv(145, 0.20, 0.80));
    private final Setting<SettingColor> starscriptDots = color(sgStarscript, "starscript-dots", "Color of dots in starscript code.", hsv(145, 0.16, 0.76));
    private final Setting<SettingColor> starscriptCommas = color(sgStarscript, "starscript-commas", "Color of commas in starscript code.", hsv(145, 0.16, 0.76));
    private final Setting<SettingColor> starscriptOperators = color(sgStarscript, "starscript-operators", "Color of operators in Starscript code.", hsv(170, 0.36, 0.88));
    private final Setting<SettingColor> starscriptStrings = color(sgStarscript, "starscript-strings", "Color of strings in Starscript code.", hsv(42, 0.32, 0.88));
    private final Setting<SettingColor> starscriptNumbers = color(sgStarscript, "starscript-numbers", "Color of numbers in Starscript code.", hsv(205, 0.32, 0.88));
    private final Setting<SettingColor> starscriptKeywords = color(sgStarscript, "starscript-keywords", "Color of keywords in Starscript code.", hsv(ACCENT_HUE, 0.48, 0.96));
    private final Setting<SettingColor> starscriptAccessedObjects = color(sgStarscript, "starscript-accessed-objects", "Color of accessed objects (before a dot) in Starscript code.", hsv(145, 0.24, 0.92));

    public Client33GuiTheme() {
        super("33");

        settingsFactory = new DefaultSettingsWidgetFactory(this);
    }

    @Override
    public void renderBackground(GuiRenderer renderer, double width, double height) {
        renderer.quad(0, 0, width, height, SCREEN_SKY, SCREEN_LEAF, SCREEN_LEAF, SCREEN_SUN);

        double line = scale(2);
        renderer.quad(0, 0, width, line, accentColor.get(), favoriteColor.get());

        renderer.text(SCREEN_MARK, width - textWidth(SCREEN_MARK) - scale(36), height - textHeight() - scale(9), textSecondaryColor.get(), false);
    }

    /** Apply the current nature palette to saved colors without changing layout or modules. */
    public void applyNaturalColors() {
        for (SettingGroup group : settings) {
            for (Setting<?> setting : group) {
                if (setting instanceof ColorSetting) setting.reset();
            }
        }
    }

    private static SettingColor hsv(double hue, double saturation, double brightness) {
        return hsv(hue, saturation, brightness, 255);
    }

    private static SettingColor hsv(double hue, double saturation, double brightness, int alpha) {
        return Color.fromHsv(hue, saturation, brightness, alpha).toSetting();
    }

    private Setting<SettingColor> color(SettingGroup group, String name, String description, SettingColor color) {
        return group.add(new ColorSetting.Builder()
                .name(name + "-color")
                .description(description)
                .defaultValue(color)
                .build());
    }
    private Setting<SettingColor> color(String name, String description, SettingColor color) {
        return color(sgColors, name, description, color);
    }

    // Widgets

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WClient33Window(icon, title));
    }

    @Override
    public WLabel label(String text, boolean title, double maxWidth) {
        if (maxWidth == 0 && !text.contains("\n")) return w(new WClient33Label(text, title));
        return w(new WClient33MultiLabel(text, title, maxWidth));
    }

    @Override
    public WHorizontalSeparator horizontalSeparator(String text) {
        return w(new WClient33HorizontalSeparator(text));
    }

    @Override
    public WVerticalSeparator verticalSeparator() {
        return w(new WClient33VerticalSeparator());
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new WClient33Button(text, texture));
    }

    @Override
    protected WConfirmedButton confirmedButton(String text, String confirmText, GuiTexture texture) {
        return w(new WClient33ConfirmedButton(text, confirmText, texture));
    }

    @Override
    public WMinus minus() {
        return w(new WClient33Minus());
    }

    @Override
    public WConfirmedMinus confirmedMinus() {
        return w(new WClient33ConfirmedMinus());
    }

    @Override
    public WPlus plus() {
        return w(new WClient33Plus());
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new WClient33Checkbox(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return w(new WClient33Slider(value, min, max));
    }

    @Override
    public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        return w(new WClient33TextBox(text, placeholder, filter, renderer));
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return w(new WClient33Dropdown<>(values, value));
    }

    @Override
    public WTriangle triangle() {
        return w(new WClient33Triangle());
    }

    @Override
    public WTooltip tooltip(String text) {
        return w(new WClient33Tooltip(text));
    }

    @Override
    public WView view() {
        return w(new WClient33View());
    }

    @Override
    public WSection section(String title, boolean expanded, WWidget headerWidget) {
        return w(new WClient33Section(title, expanded, headerWidget));
    }

    @Override
    public WAccount account(WidgetScreen screen, Account<?> account) {
        return w(new WClient33Account(screen, account));
    }

    @Override
    public WWidget module(Module module, String title) {
        return w(new WClient33Module(module, title));
    }

    @Override
    public WQuad quad(Color color) {
        return w(new WClient33Quad(color));
    }

    @Override
    public WTopBar topBar() {
        return w(new WClient33TopBar());
    }

    @Override
    public WFavorite favorite(boolean checked) {
        return w(new WClient33Favorite(checked));
    }

    // Colors

    @Override
    public Color textColor() {
        return textColor.get();
    }

    @Override
    public Color textSecondaryColor() {
        return textSecondaryColor.get();
    }

    //     Starscript

    @Override
    public Color starscriptTextColor() {
        return starscriptText.get();
    }

    @Override
    public Color starscriptBraceColor() {
        return starscriptBraces.get();
    }

    @Override
    public Color starscriptParenthesisColor() {
        return starscriptParenthesis.get();
    }

    @Override
    public Color starscriptDotColor() {
        return starscriptDots.get();
    }

    @Override
    public Color starscriptCommaColor() {
        return starscriptCommas.get();
    }

    @Override
    public Color starscriptOperatorColor() {
        return starscriptOperators.get();
    }

    @Override
    public Color starscriptStringColor() {
        return starscriptStrings.get();
    }

    @Override
    public Color starscriptNumberColor() {
        return starscriptNumbers.get();
    }

    @Override
    public Color starscriptKeywordColor() {
        return starscriptKeywords.get();
    }

    @Override
    public Color starscriptAccessedObjectColor() {
        return starscriptAccessedObjects.get();
    }

    // Other

    @Override
    public TextRenderer textRenderer() {
        return TextRenderer.get();
    }

    @Override
    public double scale(double value) {
        double scaled = value * scale.get();

        if (MacosUtil.IS_MACOS) {
            scaled /= (double) mc.getWindow().getWidth() / mc.getWindow().getWidth();
        }

        return scaled;
    }

    @Override
    public boolean categoryIcons() {
        return categoryIcons.get();
    }

    @Override
    public boolean modulesHelpText() {
        return modulesHelpText.get();
    }

    @Override
    public boolean hideHUD() {
        return hideHUD.get();
    }

    public class ThreeStateColorSetting {
        private final Setting<SettingColor> normal, hovered, pressed;

        public ThreeStateColorSetting(SettingGroup group, String name, SettingColor c1, SettingColor c2, SettingColor c3) {
            normal = color(group, name, "Color of " + name + ".", c1);
            hovered = color(group, "hovered-" + name, "Color of " + name + " when hovered.", c2);
            pressed = color(group, "pressed-" + name, "Color of " + name + " when pressed.", c3);
        }

        public SettingColor get() {
            return normal.get();
        }

        public SettingColor get(boolean pressed, boolean hovered, boolean bypassDisableHoverColor) {
            if (pressed) return this.pressed.get();
            return (hovered && (bypassDisableHoverColor || !disableHoverColor)) ? this.hovered.get() : this.normal.get();
        }

        public SettingColor get(boolean pressed, boolean hovered) {
            return get(pressed, hovered, false);
        }
    }
}
