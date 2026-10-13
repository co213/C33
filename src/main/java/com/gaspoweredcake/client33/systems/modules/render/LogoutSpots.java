/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.utils.navigation.LogoutTracker;
import com.gaspoweredcake.client33.pathing.Navigation;
import com.gaspoweredcake.client33.systems.waypoints.Waypoint;
import com.gaspoweredcake.client33.systems.waypoints.Waypoints;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WTable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.renderer.Renderer2D;
import com.gaspoweredcake.client33.renderer.ShapeMode;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import com.gaspoweredcake.client33.utils.render.NametagUtils;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;

import java.util.*;

public class LogoutSpots extends Module {
    private static final Color GREEN = new Color(103, 224, 181);
    private static final Color ORANGE = new Color(255, 195, 110);
    private static final Color RED = new Color(255, 112, 135);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale.")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Boolean> fullHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("full-height")
        .description("Displays the height as the player's full height.")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color.")
        .defaultValue(new SettingColor(157, 111, 255, 55))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color.")
        .defaultValue(new SettingColor(157, 111, 255))
        .build()
    );

    private final Setting<SettingColor> nameColor = sgRender.add(new ColorSetting.Builder()
        .name("name-color")
        .description("The name color.")
        .defaultValue(new SettingColor(242, 238, 255))
        .build()
    );

    private final Setting<SettingColor> nameBackgroundColor = sgRender.add(new ColorSetting.Builder()
        .name("name-background-color")
        .description("The name background color.")
        .defaultValue(new SettingColor(20, 18, 32, 175))
        .build()
    );

    private final Setting<Integer> maxSpots = sgGeneral.add(new IntSetting.Builder()
        .name("max-spots").description("Maximum remembered logout locations; oldest are removed first.")
        .defaultValue(100).range(1, 1000).sliderMax(250).build());
    private final Setting<Integer> expiryMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("expiry-minutes").description("Forget logout locations after this many minutes.")
        .defaultValue(60).range(1, 1440).sliderMax(180).build());
    private final Setting<Boolean> details = sgRender.add(new BoolSetting.Builder()
        .name("details").description("Show logout age, distance and coordinates.").defaultValue(true).build());

    private final LogoutTracker<Entry> tracker = new LogoutTracker<>();
    private ClientLevel world;

    public LogoutSpots() {
        super(Categories.Render, "logout-spots", "Remembers recent player logouts, with waypoints and navigation.");
        lineColor.onChanged();
    }

    private void reset() { tracker.clear(); world = mc.level; }
    @Override public void onActivate() { reset(); update(); }
    @Override public void onDeactivate() { reset(); }
    @EventHandler private void onLeave(GameLeftEvent event) { reset(); }
    @EventHandler private void onTick(TickEvent.Post event) { update(); }

    private void update() {
        if (world != mc.level) reset();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
        Set<UUID> online = new HashSet<>();
        for (PlayerInfo player : mc.getConnection().getOnlinePlayers()) online.add(player.getProfile().id());
        Map<UUID, Entry> visible = new HashMap<>();
        for (Player player : mc.level.players()) {
            if (player != mc.player) visible.put(player.getUUID(), new Entry(player));
        }
        // An entity unloading is not a logout; require tab removal and a sighting within two seconds.
        tracker.update(online, visible, System.currentTimeMillis(), 2000, expiryMinutes.get() * 60_000L, maxSpots.get());
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (world != mc.level) return;
        for (var spot : tracker.spots()) spot.snapshot().render3D(event);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (world != mc.level) return;
        for (var spot : tracker.spots()) spot.snapshot().render2D(event.graphics, spot.loggedOutAt());
    }

    /** Session-only, dimension-scoped snapshots; never claims an offline player's current position. */
    public record KnownLocation(UUID id, String name, BlockPos position, long loggedOutAt) { }

    public List<KnownLocation> getKnownLocations() {
        if (!isActive() || world != mc.level) return List.of();
        long now = System.currentTimeMillis();
        return tracker.spots().stream()
            .filter(spot -> now - spot.loggedOutAt() < expiryMinutes.get() * 60_000L)
            .map(spot -> {
                Entry entry = spot.snapshot();
                return new KnownLocation(spot.id(), entry.name,
                    BlockPos.containing(entry.x + entry.halfWidth, entry.y, entry.z + entry.halfWidth), spot.loggedOutAt());
            }).toList();
    }

    @Override public String getInfoString() { return Integer.toString(tracker.spots().size()); }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        ClientLevel capturedWorld = world;
        for (var spot : tracker.spots()) {
            Entry entry = spot.snapshot();
            BlockPos target = BlockPos.containing(entry.x + entry.halfWidth, entry.y, entry.z + entry.halfWidth);
            table.add(theme.label(entry.name + " — " + target.toShortString()));
            table.add(theme.button("Waypoint")).widget().action = () -> {
                if (mc.level == null || mc.level != capturedWorld) return;
                Waypoints.get().add(new Waypoint.Builder().name(entry.name + " logout").pos(target)
                    .dimension(PlayerUtils.getDimension()).build());
                info("Saved logout waypoint for %s.", entry.name);
            };
            table.add(theme.button("Walk to")).widget().action = () -> {
                if (mc.level == null || mc.level != capturedWorld) return;
                try { Navigation.start(target, false, false); }
                catch (IllegalStateException | IllegalArgumentException failure) { error(failure.getMessage()); }
            };
            table.add(theme.button("Mine to")).widget().action = () -> {
                if (mc.level == null || mc.level != capturedWorld) return;
                try { Navigation.start(target, false, true); }
                catch (IllegalStateException | IllegalArgumentException failure) { error(failure.getMessage()); }
            };
            table.row();
        }
        table.add(theme.button("Clear spots")).widget().action = () -> { tracker.clearSpots(); table.clear(); };
        table.add(theme.button("Stop navigation")).widget().action = Navigation::stop;
        return table;
    }

    private static final Vector3d pos = new Vector3d();

    private class Entry {
        public final double x, y, z;
        public final double xWidth, zWidth, halfWidth, height;

        public final UUID uuid;
        public final String name;
        public final int health, maxHealth;
        public final String healthText;

        public Entry(Player entity) {
            halfWidth = entity.getBbWidth() / 2;
            x = entity.getX() - halfWidth;
            y = entity.getY();
            z = entity.getZ() - halfWidth;

            xWidth = entity.getBoundingBox().getXsize();
            zWidth = entity.getBoundingBox().getZsize();
            height = entity.getBoundingBox().getYsize();

            uuid = entity.getUUID();
            name = entity.getName().getString();
            health = Math.round(entity.getHealth() + entity.getAbsorptionAmount());
            maxHealth = Math.round(entity.getMaxHealth() + entity.getAbsorptionAmount());

            healthText = " " + health;
        }

        public void render3D(Render3DEvent event) {
            if (fullHeight.get())
                event.renderer.box(x, y, z, x + xWidth, y + height, z + zWidth, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            else
                event.renderer.sideHorizontal(x, y, z, x + xWidth, z + zWidth, sideColor.get(), lineColor.get(), shapeMode.get());
        }

        public void render2D(GuiGraphicsExtractor graphics, long loggedOutAt) {
            if (!PlayerUtils.isWithinCamera(x, y, z, mc.options.renderDistance().get() * 16)) return;

            TextRenderer text = TextRenderer.get();
            double scale = LogoutSpots.this.scale.get();
            pos.set(x + halfWidth, y + height + 0.5, z + halfWidth);

            if (!NametagUtils.to2D(pos, scale)) return;

            NametagUtils.begin(pos, graphics);

            // Compute health things
            double healthPercentage = (double) health / Math.max(1, maxHealth);

            // Get health color
            Color healthColor;
            if (healthPercentage <= 0.333) healthColor = RED;
            else if (healthPercentage <= 0.666) healthColor = ORANGE;
            else healthColor = GREEN;

            String label = name;
            if (details.get()) {
                long seconds = Math.max(0, (System.currentTimeMillis() - loggedOutAt) / 1000);
                label += " | " + seconds + "s | " + Math.round(mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z)))
                    + "m | " + BlockPos.containing(x + halfWidth, y, z + halfWidth).toShortString();
            }
            // Render background
            double i = text.getWidth(label) / 2.0 + text.getWidth(healthText) / 2.0;
            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(-i, 0, i * 2, text.getHeight(), nameBackgroundColor.get());
            Renderer2D.COLOR.render();

            // Render name and health texts
            text.beginBig(graphics);
            double hX = text.render(label, -i, 0, nameColor.get());
            text.render(healthText, hX, 0, healthColor);
            text.end();

            NametagUtils.end(graphics);
        }
    }
}
