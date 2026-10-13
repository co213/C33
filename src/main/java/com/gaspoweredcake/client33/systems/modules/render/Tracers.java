/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.renderer.Renderer2D;
import com.gaspoweredcake.client33.renderer.Renderer3D;
import com.gaspoweredcake.client33.utils.render.TracerGeometry;
import com.gaspoweredcake.client33.utils.render.VisualGeometry;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.config.Config;
import com.gaspoweredcake.client33.systems.friends.Friends;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.utils.entity.EntityUtils;
import com.gaspoweredcake.client33.utils.entity.Target;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import com.gaspoweredcake.client33.utils.render.NametagUtils;
import com.gaspoweredcake.client33.utils.render.RenderUtils;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector2f;
import org.joml.Vector3d;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public class Tracers extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    public enum TracerStyle {
        Lines,
        Ribbon,
        Offscreen
    }

    // General

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Select specific entities.")
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Doesn't draw tracers to yourself when in third person or freecam.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Doesn't draw tracers to friends.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> showInvis = sgGeneral.add(new BoolSetting.Builder()
        .name("show-invisible")
        .description("Shows invisible entities.")
        .defaultValue(true)
        .build()
    );

    // Appearance

    private final Setting<TracerStyle> style = sgAppearance.add(new EnumSetting.Builder<TracerStyle>()
        .name("style")
        .description("What display mode should be used")
        .defaultValue(TracerStyle.Ribbon)
        .build()
    );

    private final Setting<Target> target = sgAppearance.add(new EnumSetting.Builder<Target>()
        .name("target")
        .description("What part of the entity to target.")
        .defaultValue(Target.Body)
        .visible(() -> style.get() != TracerStyle.Offscreen)
        .build()
    );

    private final Setting<Boolean> stem = sgAppearance.add(new BoolSetting.Builder()
        .name("stem")
        .description("Draw a line through the center of the tracer target.")
        .defaultValue(true)
        .visible(() -> style.get() != TracerStyle.Offscreen)
        .build()
    );

    private final Setting<Integer> maxDist = sgAppearance.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Maximum distance for tracers to show.")
        .defaultValue(256)
        .min(0)
        .sliderMax(256)
        .build()
    );

    private final Setting<Integer> distanceOffscreen = sgAppearance.add(new IntSetting.Builder()
        .name("distance-offscreen")
        .description("Offscreen's distance from center.")
        .defaultValue(200)
        .min(0)
        .sliderMax(500)
        .visible(() -> style.get() == TracerStyle.Offscreen)
        .build()
    );

    private final Setting<Integer> sizeOffscreen = sgAppearance.add(new IntSetting.Builder()
        .name("size-offscreen")
        .description("Offscreen's size.")
        .defaultValue(10)
        .min(2)
        .sliderMax(50)
        .visible(() -> style.get() == TracerStyle.Offscreen)
        .build()
    );

    private final Setting<Boolean> blinkOffscreen = sgAppearance.add(new BoolSetting.Builder()
        .name("blink-offscreen")
        .description("Make offscreen Blink.")
        .defaultValue(true)
        .visible(() -> style.get() == TracerStyle.Offscreen)
        .build()
    );

    private final Setting<Double> blinkOffscreenSpeed = sgAppearance.add(new DoubleSetting.Builder()
        .name("blink-offscreen-speed")
        .description("Offscreen's blink speed.")
        .defaultValue(4)
        .min(1)
        .sliderMax(15)
        .visible(() -> style.get() == TracerStyle.Offscreen && blinkOffscreen.get())
        .build()
    );

    private final Setting<Double> ribbonWidth = sgAppearance.add(new DoubleSetting.Builder()
        .name("ribbon-width").description("Bright ribbon core width in screen pixels; feathered edges add a soft halo.")
        .defaultValue(1.6).range(0.5, 4).sliderRange(0.5, 4).visible(() -> style.get() == TracerStyle.Ribbon).build());
    private final Setting<Double> arc = sgAppearance.add(new DoubleSetting.Builder()
        .name("ribbon-arc").description("Gentle upward curve of ribbon tracers.")
        .defaultValue(0.8).range(0, 2).sliderRange(0, 2).visible(() -> style.get() == TracerStyle.Ribbon).build());
    private final Setting<Boolean> animate = sgAppearance.add(new BoolSetting.Builder()
        .name("animate-ribbons").description("Traveling light pulses and rotating endpoint markers.")
        .defaultValue(true).visible(() -> style.get() == TracerStyle.Ribbon).build());
    private final Setting<Double> speed = sgAppearance.add(new DoubleSetting.Builder()
        .name("ribbon-speed").description("Pulse cycles per second.")
        .defaultValue(0.45).range(0.1, 3).sliderRange(0.1, 3).visible(() -> style.get() == TracerStyle.Ribbon && animate.get()).build());
    private final Setting<Boolean> markers = sgAppearance.add(new BoolSetting.Builder()
        .name("endpoint-markers").description("Broken halos at tracer endpoints and subtle ground orbits.")
        .defaultValue(true).visible(() -> style.get() == TracerStyle.Ribbon).build());
    private final Setting<Boolean> throughWalls = sgAppearance.add(new BoolSetting.Builder()
        .name("through-walls").description("Render tracers through terrain.").defaultValue(true).build());
    private final Setting<Integer> budget = sgAppearance.add(new IntSetting.Builder()
        .name("entity-budget").description("Maximum tracers drawn per frame to limit clutter and geometry cost.")
        .defaultValue(48).range(1, 256).sliderRange(1, 128).build());
    private final Setting<SettingColor> ribbonStart = sgColors.add(new ColorSetting.Builder()
        .name("ribbon-start").description("Start color of the HSV ribbon gradient; the endpoint uses the entity's color.")
        .defaultValue(Color.fromHsv(205, 0.38, 0.96, 180).toSetting())
        .visible(() -> style.get() == TracerStyle.Ribbon).build());

    // Colors

    public final Setting<Boolean> distance = sgColors.add(new BoolSetting.Builder()
        .name("distance-colors")
        .description("Changes the color of tracers depending on distance.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> friendOverride = sgColors.add(new BoolSetting.Builder()
        .name("show-friend-colors")
        .description("Whether or not to override the distance color of friends with the friend color.")
        .defaultValue(true)
        .visible(() -> distance.get() && !ignoreFriends.get())
        .build()
    );

    private final Setting<SettingColor> playersColor = sgColors.add(new ColorSetting.Builder()
        .name("players-colors")
        .description("The player's color.")
        .defaultValue(Color.fromHsv(150, 0.48, 0.90, 200).toSetting())
        .visible(() -> !distance.get())
        .build()
    );

    private final Setting<SettingColor> animalsColor = sgColors.add(new ColorSetting.Builder()
        .name("animals-color")
        .description("The animal's color.")
        .defaultValue(Color.fromHsv(155, 0.36, 0.88, 180).toSetting())
        .visible(() -> !distance.get())
        .build()
    );

    private final Setting<SettingColor> waterAnimalsColor = sgColors.add(new ColorSetting.Builder()
        .name("water-animals-color")
        .description("The water animal's color.")
        .defaultValue(Color.fromHsv(190, 0.36, 0.90, 180).toSetting())
        .visible(() -> !distance.get())
        .build()
    );

    private final Setting<SettingColor> monstersColor = sgColors.add(new ColorSetting.Builder()
        .name("monsters-color")
        .description("The monster's color.")
        .defaultValue(Color.fromHsv(48, 0.46, 0.98, 200).toSetting())
        .visible(() -> !distance.get())
        .build()
    );

    private final Setting<SettingColor> ambientColor = sgColors.add(new ColorSetting.Builder()
        .name("ambient-color")
        .description("The ambient color.")
        .defaultValue(Color.fromHsv(205, 0.28, 0.90, 160).toSetting())
        .visible(() -> !distance.get())
        .build()
    );

    private final Setting<SettingColor> miscColor = sgColors.add(new ColorSetting.Builder()
        .name("misc-color")
        .description("The misc color.")
        .defaultValue(Color.fromHsv(185, 0.22, 0.90, 160).toSetting())
        .visible(() -> !distance.get())
        .build()
    );

    private int count;
    private final Instant initTimer = Instant.now();

    public Tracers() {
        super(Categories.Render, "tracers", "Feathered gradient ribbons, traveling pulses and endpoint halos for selected entities.");
    }

    private boolean shouldBeIgnored(Entity entity) {
        return !PlayerUtils.isWithin(entity, maxDist.get()) || (!Modules.get().isActive(Freecam.class) && entity == mc.player) || !entities.get().contains(entity.getType()) || (ignoreSelf.get() && entity == mc.player) || (ignoreFriends.get() && entity instanceof Player player && Friends.get().isFriend(player)) || (!showInvis.get() && entity.isInvisible()) || !EntityUtils.isInRenderDistance(entity);
    }

    private Color getEntityColor(Entity entity) {
        Color color;

        if (distance.get()) {
            if (friendOverride.get() && entity instanceof Player player && Friends.get().isFriend(player)) {
                color = Config.get().friendColor.get();
            } else color = EntityUtils.getColorFromDistance(entity);
        } else if (entity instanceof Player player) {
            color = PlayerUtils.getPlayerColor(player, playersColor.get());
        } else {
            color = switch (entity.getType().getCategory()) {
                case CREATURE -> animalsColor.get();
                case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> waterAnimalsColor.get();
                case MONSTER -> monstersColor.get();
                case AMBIENT -> ambientColor.get();
                default -> miscColor.get();
            };
        }

        return new Color(color);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        count = 0;
        if (mc.player == null || mc.level == null || RenderUtils.center == null
            || mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden || style.get() == TracerStyle.Offscreen) return;
        Renderer3D renderer = throughWalls.get() ? event.renderer : event.depthRenderer;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (shouldBeIgnored(entity)) continue;

            Color color = getEntityColor(entity);

            double x = entity.xo + (entity.getX() - entity.xo) * event.tickDelta;
            double y = entity.yo + (entity.getY() - entity.yo) * event.tickDelta;
            double z = entity.zo + (entity.getZ() - entity.zo) * event.tickDelta;

            double height = entity.getBoundingBox().maxY - entity.getBoundingBox().minY;
            if (target.get() == Target.Head) y += height;
            else if (target.get() == Target.Body) y += height / 2;

            if (!Double.isFinite(x + y + z + height)) continue;
            if (style.get() == TracerStyle.Ribbon) renderRibbon(renderer, entity, x, y, z, height, color, event.tickDelta);
            else renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, color);
            if (stem.get()) renderer.line(x, y - (target.get() == Target.Head ? height : target.get() == Target.Body ? height / 2 : 0), z,
                x, y + (target.get() == Target.Head ? 0 : target.get() == Target.Body ? height / 2 : height), z, new Color(color).a(color.a / 2));

            if (++count >= budget.get()) break;
        }
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (style.get() != TracerStyle.Offscreen) return;
        count = 0;
        if (mc.player == null || mc.level == null || mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden) return;

        Renderer2D.COLOR.begin();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (shouldBeIgnored(entity)) continue;

            Color color = getEntityColor(entity);

            if (blinkOffscreen.get())
                color.a = (int) (color.a * getAlpha());

            Vec2 screenCenter = new Vec2(mc.getWindow().getWidth() / 2.f, mc.getWindow().getHeight() / 2.f);

            Vector3d projection = new Vector3d(entity.xo + (entity.getX() - entity.xo) * event.tickDelta,
                entity.yo + (entity.getY() - entity.yo) * event.tickDelta, entity.zo + (entity.getZ() - entity.zo) * event.tickDelta);
            boolean projSucceeded = NametagUtils.to2D(projection, 1, false, false);

            if (projSucceeded && projection.x > 0.f && projection.x < mc.getWindow().getWidth() && projection.y > 0.f && projection.y < mc.getWindow().getHeight())
                continue;

            projection = new Vector3d(entity.xo + (entity.getX() - entity.xo) * event.tickDelta,
                entity.yo + (entity.getY() - entity.yo) * event.tickDelta, entity.zo + (entity.getZ() - entity.zo) * event.tickDelta);
            if (!NametagUtils.to2D(projection, 1, false, true) || !Double.isFinite(projection.x + projection.y)) continue;

            Vector2f angle = vectorAngles(new Vector3d(screenCenter.x - projection.x, screenCenter.y - projection.y, 0));
            angle.y += 180;

            float angleYawRad = (float) Math.toRadians(angle.y);

            Vector2f newPoint = new Vector2f(
                screenCenter.x + distanceOffscreen.get() * (float) Math.cos(angleYawRad),
                screenCenter.y + distanceOffscreen.get() * (float) Math.sin(angleYawRad)
            );

            Vector2f[] trianglePoints = {
                new Vector2f(newPoint.x - sizeOffscreen.get(), newPoint.y - sizeOffscreen.get()),
                new Vector2f(newPoint.x + sizeOffscreen.get() * 0.73205f, newPoint.y),
                new Vector2f(newPoint.x - sizeOffscreen.get(), newPoint.y + sizeOffscreen.get())
            };

            rotateTriangle(trianglePoints, angle.y);

            Renderer2D.COLOR.triangle(
                trianglePoints[2].x, trianglePoints[2].y,
                trianglePoints[1].x, trianglePoints[1].y,
                trianglePoints[0].x, trianglePoints[0].y,
                color
            );

            Color rim = new Color(color).a(Math.min(255, color.a + 45));
            for (int i = 0; i < 3; i++) {
                Vector2f a = trianglePoints[i], b = trianglePoints[(i + 1) % 3];
                Renderer2D.COLOR.line(a.x, a.y, b.x, b.y, rim);
            }
            if (++count >= budget.get()) break;
        }

        Renderer2D.COLOR.render();
    }

    private final Vector3d p0 = new Vector3d(), p1 = new Vector3d(), ribbonSide = new Vector3d();
    private final Color c0 = new Color(), c1 = new Color(), clear0 = new Color(), clear1 = new Color();

    private void renderRibbon(Renderer3D renderer, Entity entity, double x, double y, double z, double height, Color color, float tickDelta) {
        var cameraState = mc.gameRenderer.mainCamera();
        var cameraPos = cameraState.position();
        Vector3d camera = new Vector3d(cameraPos.x, cameraPos.y, cameraPos.z);
        Vector3d right = new Vector3d(cameraState.leftVector()).negate(), up = new Vector3d(cameraState.upVector());
        Vector3d start = new Vector3d(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z), end = new Vector3d(x, y, z);
        if (start.distanceSquared(end) < 0.01) return;
        double time = animate.get() ? (mc.level.getGameTime() + tickDelta) / 20.0 * speed.get() : 0;
        double phase = TracerGeometry.phase(time + entity.getId() * 0.61803398875);
        double pulse = animate.get() ? phase : -1;
        double pixel = 2 * Math.tan(Math.toRadians(Math.clamp(cameraState.getFov(), 1, 175) / 2))
            / Math.max(1, mc.getWindow().getHeight());
        double[] from = ribbonStart.get().toHsv(), to = color.toHsv();
        int alpha = Math.min(color.a, ribbonStart.get().a);
        for (int i = 0; i < 48; i++) {
            double t0 = i / 48.0, t1 = (i + 1) / 48.0;
            TracerGeometry.point(start, end, t0, arc.get(), p0);
            TracerGeometry.point(start, end, t1, arc.get(), p1);
            TracerGeometry.side(p0, p1, camera, right, ribbonSide);
            double w0 = p0.distance(camera) * pixel * ribbonWidth.get() / 2;
            double w1 = p1.distance(camera) * pixel * ribbonWidth.get() / 2;
            TracerGeometry.color(from, to, t0, pulse, alpha, c0);
            TracerGeometry.color(from, to, t1, pulse, alpha, c1);
            clear0.set(c0).a(0); clear1.set(c1).a(0);
            ribbonStrip(renderer, w0, w1, -3, -1, clear0, c0, c1, clear1);
            ribbonStrip(renderer, w0, w1, -1, 1, c0, c0, c1, c1);
            ribbonStrip(renderer, w0, w1, 1, 3, c0, clear0, clear1, c1);
        }
        if (!markers.get()) return;
        double radius = Math.clamp(entity.getBbWidth() * 0.30, 0.12, 0.4);
        Color halo = new Color(color).a((int) (color.a * 0.7));
        for (int i = 0; i < 48; i++) {
            if (i % 12 >= 9) continue;
            double a = i * Math.PI / 24 + time, b = (i + 1) * Math.PI / 24 + time;
            renderer.line(x + radius * (right.x * Math.cos(a) + up.x * Math.sin(a)),
                y + radius * (right.y * Math.cos(a) + up.y * Math.sin(a)), z + radius * (right.z * Math.cos(a) + up.z * Math.sin(a)),
                x + radius * (right.x * Math.cos(b) + up.x * Math.sin(b)),
                y + radius * (right.y * Math.cos(b) + up.y * Math.sin(b)), z + radius * (right.z * Math.cos(b) + up.z * Math.sin(b)), halo);
        }
        double ground = entity.yo + (entity.getY() - entity.yo) * tickDelta + 0.025;
        VisualGeometry.ring(renderer, x, ground, z, entity.getBbWidth() * 0.7, time, Math.PI * 1.35, 36, new Color(halo).a(halo.a / 2));
    }

    private void ribbonStrip(Renderer3D renderer, double w0, double w1, double left, double right,
                             Color a, Color b, Color c, Color d) {
        renderer.quad(p0.x + ribbonSide.x * w0 * left, p0.y + ribbonSide.y * w0 * left, p0.z + ribbonSide.z * w0 * left,
            p0.x + ribbonSide.x * w0 * right, p0.y + ribbonSide.y * w0 * right, p0.z + ribbonSide.z * w0 * right,
            p1.x + ribbonSide.x * w1 * right, p1.y + ribbonSide.y * w1 * right, p1.z + ribbonSide.z * w1 * right,
            p1.x + ribbonSide.x * w1 * left, p1.y + ribbonSide.y * w1 * left, p1.z + ribbonSide.z * w1 * left,
            b, c, d, a);
    }

    private void rotateTriangle(Vector2f[] points, float ang) {
        Vector2f triangleCenter = new Vector2f(0, 0);
        triangleCenter.add(points[0]).add(points[1]).add(points[2]).div(3.f);
        float theta = (float) Math.toRadians(ang);
        float cos = (float) Math.cos(theta);
        float sin = (float) Math.sin(theta);
        for (int i = 0; i < 3; i++) {
            Vector2f point = new Vector2f(points[i].x, points[i].y).sub(triangleCenter);

            Vector2f newPoint = new Vector2f(point.x * cos - point.y * sin, point.x * sin + point.y * cos);
            newPoint.add(triangleCenter);

            points[i] = newPoint;
        }
    }

    private Vector2f vectorAngles(final Vector3d forward) {
        float tmp, yaw, pitch;

        if (forward.x == 0 && forward.y == 0) {
            yaw = 0;
            if (forward.z > 0)
                pitch = 270;
            else
                pitch = 90;
        } else {
            yaw = (float) (Math.atan2(forward.y, forward.x) * 180 / Math.PI);
            if (yaw < 0)
                yaw += 360;

            tmp = (float) Math.sqrt(forward.x * forward.x + forward.y * forward.y);
            pitch = (float) (Math.atan2(-forward.z, tmp) * 180 / Math.PI);
            if (pitch < 0)
                pitch += 360;
        }

        return new Vector2f(pitch, yaw);
    }

    private float getAlpha() {
        double speed = blinkOffscreenSpeed.get() / 4.0;
        double duration = Math.abs(Duration.between(Instant.now(), initTimer).toMillis()) * speed;

        return (float) Math.abs((duration % 1000) - 500) / 500.f;
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }
}
