/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package com.gaspoweredcake.client33.gui.tabs.builtin;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.DefaultSettingsWidgetFactory;
import com.gaspoweredcake.client33.gui.tabs.Tab;
import com.gaspoweredcake.client33.gui.tabs.TabScreen;
import com.gaspoweredcake.client33.gui.tabs.WindowTabScreen;
import com.gaspoweredcake.client33.gui.widgets.WLabel;
import com.gaspoweredcake.client33.gui.widgets.containers.WContainer;
import com.gaspoweredcake.client33.gui.widgets.containers.WHorizontalList;
import com.gaspoweredcake.client33.gui.widgets.input.WTextBox;
import com.gaspoweredcake.client33.gui.widgets.pressable.WButton;
import com.gaspoweredcake.client33.pathing.IPathManager;
import com.gaspoweredcake.client33.pathing.Navigation;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.waypoints.Waypoints;
import com.gaspoweredcake.client33.systems.modules.render.LogoutSpots;
import net.minecraft.core.BlockPos;
import java.util.UUID;
import java.util.HashSet;
import java.util.function.Supplier;
import com.gaspoweredcake.client33.utils.navigation.BaritoneTasks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Locale;

import static com.gaspoweredcake.client33.Client33.mc;

public class PathManagerTab extends Tab {
    public PathManagerTab() { super("Baritone"); }
    @Override public TabScreen createScreen(GuiTheme theme) { return new PathManagerScreen(theme, this); }
    @Override public boolean isScreen(Screen screen) { return screen instanceof PathManagerScreen; }

    private enum Task {
        GoTo, Mine, Follow, Explore, Farm, Tunnel, Build, Command;
        @Override public String toString() { return this == GoTo ? "Go To" : name(); }
    }

    private static class PathManagerScreen extends WindowTabScreen {
        private final IPathManager manager = PathManagers.get();
        private final Settings form = new Settings();
        private final Settings movement = new Settings();
        private final SettingGroup group = form.createGroup("Task");
        private final Setting<Task> task = group.add(new EnumSetting.Builder<Task>()
            .name("task").description("Choose what Baritone should do.").defaultValue(Task.GoTo).build());
        private final Setting<Integer> x = coordinate("x", false);
        private final Setting<Integer> y = coordinate("y", true);
        private final Setting<Integer> z = coordinate("z", false);
        private final Setting<Boolean> ignoreY = group.add(new BoolSetting.Builder().name("ignore-height")
            .description("Go to X/Z at any height.").defaultValue(false).visible(() -> task.get() == Task.GoTo).build());
        private final Setting<List<Block>> blocks = group.add(new BlockListSetting.Builder().name("blocks")
            .description("Blocks to search for and mine.").defaultValue(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)
            .visible(() -> task.get() == Task.Mine).build());
        private final Setting<Integer> quantity = number("quantity", "Items to collect; zero keeps mining.", 0, 0, Task.Mine);
        private final Setting<String> player = group.add(new StringSetting.Builder().name("player")
            .description("Exact player name to follow.").defaultValue("").visible(() -> task.get() == Task.Follow).build());
        private final Setting<Integer> radius = number("radius", "Farm radius; zero leaves it unrestricted.", 32, 0, Task.Farm);
        private final Setting<Integer> height = number("height", "Tunnel height in blocks.", 3, 2, Task.Tunnel);
        private final Setting<Integer> width = number("width", "Tunnel width in blocks.", 2, 1, Task.Tunnel);
        private final Setting<Integer> length = number("length", "Tunnel length in blocks.", 16, 1, Task.Tunnel);
        private final Setting<String> filename = group.add(new StringSetting.Builder().name("schematic")
            .description("Filename in the Minecraft schematics folder; format support depends on your backend.")
            .defaultValue("example.schematic").visible(() -> task.get() == Task.Build).build());
        private final Setting<String> command = group.add(new StringSetting.Builder().name("command")
            .description("Any installed Baritone command, with or without its chat prefix.")
            .defaultValue("help").visible(() -> task.get() == Task.Command).build());
        private WContainer formContainer;
        private WLabel status;
        private WLabel feedback;

        PathManagerScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);
            manager.getSettings().get().onActivated();
            if (mc.player != null) usePosition();
        }

        private boolean hasCoordinates() { return task.get() == Task.GoTo || task.get() == Task.Explore || task.get() == Task.Build; }
        private Setting<Integer> coordinate(String name, boolean vertical) {
            return group.add(new IntSetting.Builder().name(name).description("World " + name.toUpperCase(Locale.ROOT) + " coordinate.")
                .defaultValue(0).range(vertical ? Integer.MIN_VALUE : -30_000_000, vertical ? Integer.MAX_VALUE : 30_000_000)
                .noSlider().visible(() -> hasCoordinates() && (!vertical || task.get() != Task.Explore)).build());
        }
        private Setting<Integer> number(String name, String description, int value, int min, Task mode) {
            return group.add(new IntSetting.Builder().name(name).description(description).defaultValue(value).min(min)
                .noSlider().visible(() -> task.get() == mode).build());
        }

        @Override public void initWidgets() {
            if (!manager.supportsCommands()) {
                add(theme.label("Baritone is not installed.", 380));
                add(theme.label("Add a Baritone build compatible with this Minecraft version to your mods folder, then restart.", 380));
                add(theme.label("This section provides tasks, movement controls and the installed command library.", 380));
                return;
            }
            status = add(theme.label("Status: Idle")).minWidth(380).widget();
            WHorizontalList controls = add(theme.horizontalList()).expandX().widget();
            button(controls, "Pause", () -> { manager.pause(); feedback.set("Paused. Resume continues the task."); });
            button(controls, "Resume", () -> { manager.resume(); feedback.set("Resumed."); });
            button(controls, "Stop", () -> { Navigation.stop(); manager.stop(); feedback.set("Stopped."); });
            formContainer = add(theme.verticalList()).expandX().widget();
            formContainer.add(theme.settings(form)).expandX();
            WHorizontalList actions = add(theme.horizontalList()).expandX().widget();
            button(actions, "Start task", this::startTask);
            button(actions, "Use my position", () -> {
                if (mc.player == null) feedback.set("Join a world first.");
                else { usePosition(); form.invalidate(); feedback.set("Coordinates copied from your player."); }
            });
            feedback = add(theme.label("Choose a task, then Start. Stop cancels it.", 380)).expandX().widget();
            addWaypoints();
            addDestinations();
            addMovement();
            addLibrary();
            var advanced = add(theme.section("Advanced settings", false)).expandX().widget();
            WTextBox filter = advanced.add(theme.textBox("")).expandX().widget();
            filter.tooltip = "Search backend setting names.";
            WContainer settings = advanced.add(theme.verticalList()).expandX().widget();
            Runnable update = () -> {
                settings.clear();
                settings.add(new DefaultSettingsWidgetFactory(theme) {
                    @Override protected WLabel settingLabel(Setting<?> setting) {
                        return theme.label(setting.title.replaceAll("([a-z])([A-Z])", "$1 $2"), 130);
                    }
                }.create(theme, manager.getSettings().get(), filter.get().trim())).expandX();
            };
            filter.action = update;
            update.run();
        }

        private void button(WContainer parent, String title, Runnable action) {
            WButton button = parent.add(theme.button(title)).expandX().widget();
            button.action = action;
        }
        private void usePosition() {
            var pos = mc.player.blockPosition();
            x.set(pos.getX()); y.set(pos.getY()); z.set(pos.getZ());
        }
        private void addWaypoints() {
            var section = add(theme.section("Saved waypoints", false)).expandX().widget();
            WTextBox search = section.add(theme.textBox("")).expandX().widget();
            search.tooltip = "Search saved waypoint names. Only reachable dimensions are listed.";
            WContainer results = section.add(theme.verticalList()).expandX().widget();
            Runnable refresh = () -> {
                results.clear();
                if (mc.level == null) { results.add(theme.label("Join a world first.")); return; }
                var capturedWorld = mc.level;
                String query = search.get().trim().toLowerCase(Locale.ROOT);
                int shown = 0;
                for (var waypoint : Waypoints.get()) {
                    if (!Waypoints.checkDimension(waypoint) || !waypoint.name.get().toLowerCase(Locale.ROOT).contains(query)) continue;
                    results.add(theme.label(waypoint.name.get() + " | " + waypoint.getPos().toShortString(), 360));
                    Supplier<BlockPos> resolve = () -> {
                        if (mc.level != capturedWorld || !Waypoints.checkDimension(waypoint))
                            throw new IllegalStateException("World or dimension changed. Refresh waypoints.");
                        boolean present = false;
                        for (var current : Waypoints.get()) if (current == waypoint) { present = true; break; }
                        if (!present) throw new IllegalStateException("Waypoint was removed. Refresh the list.");
                        return waypoint.getPos();
                    };
                    WHorizontalList actions = results.add(theme.horizontalList()).expandX().widget();
                    button(actions, "Walk to", () -> startDestination(resolve, false));
                    button(actions, "Mine to", () -> startDestination(resolve, true));
                    shown++;
                }
                if (shown == 0) results.add(theme.label("No matching waypoints in this dimension. Mark one in Freecam or use .waypoint add.", 360));
            };
            button(section, "Refresh waypoints", refresh);
            search.action = refresh;
            section.action = refresh;
            refresh.run();
        }

        private void addDestinations() {
            var section = add(theme.section("Players and logout spots", false)).expandX().widget();
            section.add(theme.label("Loaded players and recorded logout locations in this dimension. Refresh before choosing a target.", 360));
            WTextBox search = section.add(theme.textBox("")).expandX().widget();
            search.tooltip = "Filter known players by name.";
            WContainer results = section.add(theme.verticalList()).expandX().widget();
            Runnable refresh = () -> {
                results.clear();
                if (mc.level == null || mc.player == null) { results.add(theme.label("Join a world first.")); return; }
                var capturedWorld = mc.level;
                String query = search.get().trim().toLowerCase(Locale.ROOT);
                var located = new HashSet<UUID>();
                int shown = 0;
                for (var target : mc.level.players()) {
                    if (target == mc.player) continue;
                    located.add(target.getUUID());
                    String name = target.getGameProfile().name();
                    if (!name.toLowerCase(Locale.ROOT).contains(query)) continue;
                    UUID id = target.getUUID();
                    results.add(theme.label(name + " | Loaded | " + target.blockPosition().toShortString(), 360));
                    WHorizontalList actions = results.add(theme.horizontalList()).expandX().widget();
                    Supplier<BlockPos> resolve = () -> {
                        if (mc.level != capturedWorld) throw new IllegalStateException("World changed. Refresh the player list.");
                        var live = mc.level.getPlayerByUUID(id);
                        if (live == null) throw new IllegalStateException("Player is no longer loaded. Refresh for recorded logout spots.");
                        return live.blockPosition();
                    };
                    button(actions, "Walk to", () -> startDestination(resolve, false));
                    button(actions, "Mine to", () -> startDestination(resolve, true));
                    button(actions, "Follow", () -> {
                        try { resolve.get(); task.set(Task.Follow); player.set(name); form.invalidate(); startTask(); }
                        catch (IllegalStateException e) { feedback.set(e.getMessage()); }
                    });
                    shown++;
                }
                LogoutSpots logout = Modules.get().get(LogoutSpots.class);
                for (var spot : logout.getKnownLocations()) {
                    if (!spot.name().toLowerCase(Locale.ROOT).contains(query)) continue;
                    long minutes = Math.max(0, (System.currentTimeMillis() - spot.loggedOutAt()) / 60_000);
                    results.add(theme.label(spot.name() + " | Logout " + minutes + "m ago | " + spot.position().toShortString(), 360));
                    Supplier<BlockPos> resolve = () -> {
                        if (mc.level != capturedWorld) throw new IllegalStateException("World changed. Refresh the player list.");
                        return logout.getKnownLocations().stream()
                            .filter(current -> current.id().equals(spot.id()) && current.loggedOutAt() == spot.loggedOutAt())
                            .map(LogoutSpots.KnownLocation::position).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Logout record expired or changed. Refresh the list."));
                    };
                    WHorizontalList actions = results.add(theme.horizontalList()).expandX().widget();
                    button(actions, "Walk to", () -> startDestination(resolve, false));
                    button(actions, "Mine to", () -> startDestination(resolve, true));
                    shown++;
                }
                if (shown == 0) results.add(theme.label("No known locations match. Enable Logout Spots to record nearby logouts.", 360));
                if (mc.getConnection() != null) {
                    long unknown = mc.getConnection().getOnlinePlayers().stream()
                        .filter(info -> !info.getProfile().id().equals(mc.player.getUUID()) && !located.contains(info.getProfile().id())).count();
                    if (unknown > 0) results.add(theme.label(unknown + " other online players have no position supplied by the server.", 360));
                }
            };
            button(section, "Refresh locations", refresh);
            search.action = refresh;
            section.action = refresh;
            refresh.run();
        }
        private void startDestination(Supplier<BlockPos> resolve, boolean mine) {
            try {
                BlockPos target = resolve.get();
                Navigation.start(target, false, mine);
                feedback.set((mine ? "Mining route to " : "Walking to ") + target.toShortString() + ". This is a known location, not a live tracker.");
            } catch (IllegalStateException | IllegalArgumentException e) { feedback.set(e.getMessage()); }
        }

        private void addMovement() {
            SettingGroup common = movement.createGroup("Movement", false);
            proxy(common, "allowBreak", "break-blocks", "Allow Baritone to mine blocks along its path.");
            proxy(common, "allowPlace", "place-blocks", "Allow Baritone to place blocks along its path.");
            proxy(common, "allowSprint", "sprint", "Allow sprinting when the path permits it.");
            proxy(common, "allowParkour", "parkour", "Allow jumping gaps along the path.");
            proxy(common, "allowInventory", "use-inventory", "Allow moving items from inventory into the hotbar.");
            add(theme.settings(movement)).expandX();
        }
        private void proxy(SettingGroup common, String backendName, String title, String description) {
            Setting<Boolean> backend = manager.getSettings().get().get(backendName, Boolean.class);
            if (backend == null) return;
            common.add(new BoolSetting.Builder().name(title).description(description).defaultValue(backend.get())
                .onChanged(backend::set).build());
        }
        private void addLibrary() {
            var section = add(theme.section("Command library", false)).expandX().widget();
            section.add(theme.label("Select a command to edit its arguments before submitting.", 360));
            WTextBox search = section.add(theme.textBox("")).expandX().widget();
            search.tooltip = "Search installed commands and descriptions.";
            WContainer results = section.add(theme.verticalList()).expandX().widget();
            List<IPathManager.CommandInfo> commands = manager.getCommands();
            Runnable update = () -> {
                results.clear();
                String query = search.get().trim().toLowerCase(Locale.ROOT);
                for (var entry : commands) {
                    if (!(entry.name() + " " + entry.description()).toLowerCase(Locale.ROOT).contains(query)) continue;
                    WHorizontalList row = results.add(theme.horizontalList()).expandX().widget();
                    WButton select = row.add(theme.button(entry.name())).minWidth(100).widget();
                    select.tooltip = String.join("\n", entry.help());
                    select.action = () -> {
                        task.set(Task.Command); command.set(entry.name()); form.invalidate();
                        feedback.set(entry.description() + " Check the tooltip for syntax.");
                    };
                    row.add(theme.label(entry.description(), 240)).expandX();
                }
                if (results.cells.isEmpty()) results.add(theme.label("No commands match your search."));
            };
            search.action = update;
            update.run();
        }
        private String buildCommand() {
            return switch (task.get()) {
                case GoTo -> BaritoneTasks.goTo(x.get(), y.get(), z.get(), ignoreY.get());
                case Mine -> BaritoneTasks.mine(blocks.get().stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).toString()).toList(), quantity.get());
                case Follow -> BaritoneTasks.follow(player.get());
                case Explore -> BaritoneTasks.explore(x.get(), z.get());
                case Farm -> BaritoneTasks.farm(radius.get());
                case Tunnel -> BaritoneTasks.tunnel(height.get(), width.get(), length.get());
                case Build -> BaritoneTasks.build(filename.get(), x.get(), y.get(), z.get());
                case Command -> BaritoneTasks.command(command.get(), manager.getCommandPrefix());
            };
        }
        private void startTask() {
            if (mc.player == null || mc.level == null) { feedback.set("Join a world first."); return; }
            try {
                String input = buildCommand(); // Validate before interrupting another task.
                if (task.get() != Task.Command) { Navigation.stop(); manager.stop(); }
                if (input.equalsIgnoreCase("pause")) { manager.pause(); feedback.set("Paused."); }
                else if (input.equalsIgnoreCase("resume")) { manager.resume(); feedback.set("Resumed."); }
                else if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("stop")) {
                    Navigation.stop(); manager.stop(); feedback.set("Stopped.");
                } else {
                    feedback.set(manager.executeCommand(input) ? "Submitted. Check Baritone chat for errors." : "Unknown command. See the command library.");
                }
                manager.getSettings().get().onActivated();
            } catch (IllegalArgumentException e) { feedback.set(e.getMessage()); }
        }
        @Override public void tick() {
            super.tick();
            if (!manager.supportsCommands()) return;
            form.tick(formContainer, theme);
            String value = manager.getStatus();
            String text = "Status: " + value;
            int end = text.length();
            while (end > 0 && theme.textWidth(text.substring(0, end) + (end < text.length() ? "..." : "")) > 380) end--;
            String display = text.substring(0, end) + (end < text.length() ? "..." : "");
            if (!display.equals(status.get())) status.set(display);
            status.tooltip = value;
        }
        @Override protected void onClosed() { manager.getSettings().save(); }
    }
}
