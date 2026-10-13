/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.screens;

import com.mojang.blaze3d.platform.MacosUtil;
import com.gaspoweredcake.client33.events.client33.ActiveModulesChangedEvent;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.GuiThemes;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.gui.tabs.TabScreen;
import com.gaspoweredcake.client33.gui.tabs.Tabs;
import com.gaspoweredcake.client33.gui.widgets.WLabel;
import com.gaspoweredcake.client33.gui.widgets.WVisualPreview;
import com.gaspoweredcake.client33.gui.widgets.input.WDropdown;
import com.gaspoweredcake.client33.gui.widgets.pressable.WCheckbox;
import com.gaspoweredcake.client33.systems.modules.render.ESP;
import com.gaspoweredcake.client33.systems.modules.render.CombatVisuals;
import com.gaspoweredcake.client33.gui.widgets.containers.*;
import com.gaspoweredcake.client33.gui.widgets.input.WTextBox;
import com.gaspoweredcake.client33.gui.widgets.pressable.WButton;
import com.gaspoweredcake.client33.gui.widgets.pressable.WFavorite;
import com.gaspoweredcake.client33.settings.Setting;
import com.gaspoweredcake.client33.settings.SettingGroup;
import com.gaspoweredcake.client33.systems.config.Config;
import com.gaspoweredcake.client33.systems.modules.Category;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Locale;

import static com.gaspoweredcake.client33.Client33.mc;
import static com.gaspoweredcake.client33.utils.Utils.getWindowHeight;
import static com.gaspoweredcake.client33.utils.Utils.getWindowWidth;
import static com.mojang.blaze3d.platform.InputConstants.*;

/** A single workspace for discovering, configuring and switching modules. */
public class ModulesScreen extends TabScreen {
    private WTextBox search;
    private WView results;
    private WVerticalList navigation;
    private WLabel summary;
    private Category category;
    private Filter filter = Filter.Studio;
    private WHorizontalList categories;
    private String query = "";

    public ModulesScreen(GuiTheme theme) {
        super(theme, Tabs.get().getFirst());
    }

    @Override
    public void initWidgets() {
        WWindow window = add(theme.window("33   /   VISUAL STUDIO")).centerX().top().marginTop(54).widget();
        window.padding = 12;
        window.view.spacing = 8;

        categories = window.add(theme.horizontalList()).expandX().widget();
        refreshCategories();
        WHorizontalList toolbar = window.add(theme.horizontalList()).expandX().widget();
        search = toolbar.add(theme.textBox(query, "Search modules or settings...")).minWidth(220).expandX().widget();
        search.action = () -> {
            query = search.get();
            refreshResults();
        };
        toolbar.add(theme.button("Clear")).widget().action = () -> {
            search.set("");
            query = "";
            search.setFocused(true);
            refreshResults();
        };

        if (theme instanceof Client33GuiTheme nature) {
            WButton natural = toolbar.add(theme.button("Natural colors")).widget();
            natural.tooltip = "Apply fresh leaf green, sky blue and sunshine yellow to all GUI colors. Replaces saved colors.";
            natural.action = () -> {
                nature.applyNaturalColors();
                GuiThemes.save();
            };
        }

        WHorizontalList body = window.add(theme.horizontalList()).expandX().widget();
        body.spacing = 12;
        WView sidebar = body.add(theme.view()).top().widget();
        sidebar.maxHeight = Math.max(theme.scale(90), getWindowHeight() - theme.scale(280));
        navigation = sidebar.add(theme.verticalList()).minWidth(110).expandX().widget();
        refreshNavigation();

        WVerticalList content = body.add(theme.verticalList()).top().expandX().widget();
        summary = content.add(theme.label("")).widget();
        results = content.add(theme.view()).minWidth(Math.max(260, Math.min(780, getWindowWidth() / theme.scale(1) - 220))).expandX().widget();
        results.maxHeight = sidebar.maxHeight - theme.textHeight() - theme.scale(8);
        results.spacing = 8;
        refreshResults();

        if (theme.modulesHelpText()) {
            window.add(theme.label("Click to toggle  /  Configure to edit  /  Ctrl+F to search")
                .color(theme.textSecondaryColor()));
        }
    }

    @Override
    protected void init() {
        super.init();
        refreshResults();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        reload();
    }

    @Override
    public void reload() {
        super.reload();
        // Tab.createScreen adds this outside initWidgets; a rebuilt root must restore it.
        addDirect(theme.topBar()).top().centerX();
    }

    private void refreshNavigation() {
        navigation.clear();
        navigation.add(theme.label("LIBRARY").color(theme.textSecondaryColor()));
        for (Filter value : Filter.values()) {
            WButton button = navigation.add(theme.button((category == null && filter == value ? "> " : "") + value)).expandX().widget();
            button.action = () -> select(null, value);
        }
        navigation.add(theme.horizontalSeparator()).expandX();
        navigation.add(theme.label("QUICK ACCESS").color(theme.textSecondaryColor()));
        for (String name : new String[]{"esp", "combat-visuals", "kill-aura", "crystal-aura", "faction-intel", "movement-heatmap", "structure-esp"}) {
            Module module = Modules.get().get(name);
            if (module != null) navigation.add(theme.button(module.title)).expandX().widget().action = () -> mc.gui.setScreen(theme.moduleScreen(module));
        }
    }

    private void refreshCategories() {
        categories.clear();
        categories.add(theme.button(category == null && filter == Filter.Studio ? "[ Studio ]" : "Studio")).expandX().widget().action = () -> select(null, Filter.Studio);
        for (Category value : Modules.loopCategories()) {
            categories.add(theme.button((category == value ? "[ " : "") + value.name + (category == value ? " ]" : ""))).expandX().widget().action = () -> select(value, Filter.All);
        }
    }

    private void select(Category category, Filter filter) {
        this.category = category;
        this.filter = filter;
        refreshNavigation();
        refreshCategories();
        refreshResults();
    }

    private void refreshResults() {
        if (results == null) return;
        results.clear();
        String text = query.strip().toLowerCase(Locale.ROOT);
        if (filter == Filter.Studio && category == null && text.isEmpty()) {
            summary.set("VISUAL STUDIO  /  Build your signature");
            buildStudio();
            return;
        }
        var modules = Modules.get().getAll().stream()
            .filter(module -> !Config.get().hiddenModules.get().contains(module))
            .filter(module -> category == null || module.category == category)
            .filter(module -> filter != Filter.Favorites || module.favorite)
            .filter(module -> filter != Filter.Enabled || module.isActive())
            .filter(module -> matches(module, text))
            .sorted(Comparator.comparingInt((Module module) -> searchRank(module, text))
                .thenComparing(module -> module.title, String.CASE_INSENSITIVE_ORDER))
            .toList();
        summary.set((category == null ? filter.toString() : category.name) + "  /  " + modules.size() + " modules");

        if (modules.isEmpty()) {
            results.add(theme.label("No modules found.")).pad(8);
            results.add(theme.label("Try another category or clear your search.").color(theme.textSecondaryColor()));
            return;
        }

        WTable grid = results.add(theme.table()).expandX().widget();
        grid.horizontalSpacing = 10;
        grid.verticalSpacing = 10;
        int columns = getWindowWidth() / theme.scale(1) >= 1000 ? 2 : 1;
        int index = 0;
        for (Module module : modules) {
            WPanel panel = grid.add(new WPanel()).expandX().top().widget();
            panel.add(theme.module(module)).expandX().pad(7);
            panel.add(theme.label(module.description, 290)).padHorizontal(12).padBottom(5);
            WHorizontalList row = panel.add(theme.horizontalList()).expandX().pad(8).widget();
            WFavorite favorite = row.add(theme.favorite(module.favorite)).widget();
            favorite.tooltip = "Add to favorites";
            favorite.action = () -> {
                module.favorite = favorite.checked;
                if (filter == Filter.Favorites) taskAfterRender = this::refreshResults;
            };
            row.add(theme.button("Configure")).expandX().widget().action = () -> mc.gui.setScreen(theme.moduleScreen(module));
            if (++index % columns == 0) grid.row();
        }
    }

    private void buildStudio() {
        ESP esp = Modules.get().get(ESP.class);
        CombatVisuals combat = Modules.get().get(CombatVisuals.class);
        WTable grid = results.add(theme.table()).expandX().widget();
        grid.horizontalSpacing = 12;
        grid.verticalSpacing = 12;
        boolean wide = getWindowWidth() / theme.scale(1) >= 950;
        WPanel entity = studioCard(grid, esp, "01 / ENTITY SIGNATURE", false);
        WDropdown<ESP.Mode> mode = entity.add(theme.dropdown(esp.mode.get())).expandX().padHorizontal(12).widget();
        mode.action = () -> esp.mode.set(mode.get());
        toggle(entity, "Animated scans", esp.animate);
        toggle(entity, "Ground markers", esp.groundMarker);
        toggle(entity, "Target tether", esp.tether);
        entity.add(theme.button("All ESP settings")).expandX().pad(12).widget().action = () -> mc.gui.setScreen(theme.moduleScreen(esp));
        if (!wide) grid.row();
        WPanel effects = studioCard(grid, combat, "02 / COMBAT CHOREOGRAPHY", true);
        toggle(effects, "Third-person camera", combat.thirdPerson);
        toggle(effects, "Attack bursts", combat.attackBursts);
        toggle(effects, "Movement ribbon", combat.trails);
        toggle(effects, "Target crown", combat.crown);
        effects.add(theme.button("All combat visuals")).expandX().pad(12).widget().action = () -> mc.gui.setScreen(theme.moduleScreen(combat));
        grid.row();
        WPanel looks = grid.add(new WPanel(WPanel.Tone.Sun)).expandX().top().widget();
        looks.add(theme.label("03 / LOOK PRESETS")).pad(12);
        for (String name : new String[]{"Crimson hologram", "Arctic constellation", "Minimal telemetry"}) {
            looks.add(theme.button(name)).expandX().padHorizontal(12).padBottom(6).widget().action = () -> {
                boolean minimal = name.startsWith("Minimal"), arctic = name.startsWith("Arctic");
                esp.mode.set(minimal ? ESP.Mode.Telemetry : arctic ? ESP.Mode.Constellation : ESP.Mode.Hologram);
                esp.animate.set(!minimal);
                combat.targetOrbit.set(!minimal); combat.scanBand.set(!minimal); combat.crown.set(!minimal);
                combat.attackBursts.set(!minimal); combat.sparks.set(!minimal); combat.trails.set(!minimal);
                combat.primary.set(new com.gaspoweredcake.client33.utils.render.color.SettingColor(arctic ? 92 : 255, arctic ? 220 : 65, arctic ? 255 : 95, 220));
                esp.signatureAccent.set(new com.gaspoweredcake.client33.utils.render.color.SettingColor(combat.primary.get()));
                taskAfterRender = this::refreshResults;
            };
        }
        looks.add(theme.label("Changes appearance only. Enable above.", 280).color(theme.textSecondaryColor())).pad(12);
        if (!wide) grid.row();
        WPanel scene = grid.add(new WPanel(WPanel.Tone.Sky)).expandX().top().widget();
        scene.add(theme.label("04 / SCENE & CAMERA")).pad(12);
        for (String name : new String[]{"ambience", "fullbright", "camera-tweaks", "free-look", "hand-view"}) {
            Module module = Modules.get().get(name);
            if (module != null) scene.add(theme.button(module.title + "  >")).expandX().padHorizontal(12).padBottom(6).widget().action = () -> mc.gui.setScreen(theme.moduleScreen(module));
        }
        scene.add(theme.button("Minecraft tuning  >")).expandX().padHorizontal(12).padBottom(6).widget().action = () ->
            mc.gui.setScreen(new MinecraftTuningScreen(theme));
        scene.add(theme.button("Import & build a building  >")).expandX().padHorizontal(12).padBottom(6).widget().action = () ->
            mc.gui.setScreen(new SchematicBuildScreen(theme));
        scene.add(theme.label("Fine-tune your world, camera and hands.", 280).color(theme.textSecondaryColor())).pad(12);
        grid.row();
        WPanel recon = grid.add(new WPanel(WPanel.Tone.Leaf)).expandX().top().widget();
        recon.add(theme.label("05 / WORLD INTELLIGENCE")).pad(12);
        for (String name : new String[]{"storage-esp", "faction-intel", "movement-heatmap", "structure-esp"}) {
            Module module = Modules.get().get(name);
            recon.add(theme.button(module.title + "  >")).expandX().padHorizontal(12).padBottom(6).widget().action = () -> mc.gui.setScreen(theme.moduleScreen(module));
        }
        recon.add(theme.label("Loaded data, source labels and expiring observations.", 280).color(theme.textSecondaryColor())).pad(12);
        if (!wide) grid.row();
        WPanel combatTools = grid.add(new WPanel(WPanel.Tone.Sun)).expandX().top().widget();
        combatTools.add(theme.label("06 / PVP CONTROL")).pad(12);
        for (String name : new String[]{"kill-aura", "shield-predict", "crystal-aura", "auto-totem"}) {
            Module module = Modules.get().get(name);
            combatTools.add(theme.button(module.title + "  >")).expandX().padHorizontal(12).padBottom(6).widget().action = () -> mc.gui.setScreen(theme.moduleScreen(module));
        }
        combatTools.add(theme.label("Server timing, shield estimates and explosion risk controls.", 280).color(theme.textSecondaryColor())).pad(12);
    }

    private WPanel studioCard(WTable grid, Module module, String title, boolean combat) {
        WPanel card = grid.add(new WPanel(combat ? WPanel.Tone.Sun : WPanel.Tone.Sky)).expandX().top().widget();
        card.add(theme.label(title)).pad(12);
        card.add(new WVisualPreview(combat)).expandX().padHorizontal(8);
        card.add(theme.module(module)).expandX().pad(8);
        return card;
    }

    private void toggle(WPanel card, String title, Setting<Boolean> setting) {
        WHorizontalList row = card.add(theme.horizontalList()).expandX().padHorizontal(12).padVertical(4).widget();
        row.add(theme.label(title)).expandCellX();
        WCheckbox checkbox = row.add(theme.checkbox(setting.get())).widget();
        checkbox.action = () -> setting.set(checkbox.checked);
        checkbox.tooltip = setting.description;
    }

    private int searchRank(Module module, String query) {
        String title = module.title.toLowerCase(Locale.ROOT);
        if (title.equals(query) || module.name.equalsIgnoreCase(query)) return 0;
        if (title.startsWith(query)) return 1;
        if (title.contains(query)) return 2;
        return 3;
    }

    private boolean matches(Module module, String query) {
        if (query.isEmpty()) return true;
        if ((module.title + " " + module.name + " " + module.description).toLowerCase(Locale.ROOT).contains(query)) return true;
        for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
                if (setting.title.toLowerCase(Locale.ROOT).contains(query)) return true;
            }
        }
        return false;
    }

    @EventHandler
    private void onActiveModulesChanged(ActiveModulesChangedEvent event) {
        if (filter == Filter.Enabled) taskAfterRender = this::refreshResults;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent value) {
        if (locked) return false;
        int modifier = MacosUtil.IS_MACOS ? MOD_SUPER : MOD_CONTROL;
        if ((value.modifiers() & modifier) != 0 && value.key() == KEY_F) {
            search.setFocused(true);
            search.setCursorMax();
            return true;
        }
        return super.keyPressed(value);
    }

    @Override
    public boolean toClipboard() {
        return NbtUtils.toClipboard(Modules.get());
    }

    @Override
    public boolean fromClipboard() {
        boolean imported = NbtUtils.fromClipboard(Modules.get());
        refreshResults();
        return imported;
    }

    private enum Filter { Studio, All, Favorites, Enabled }
}
