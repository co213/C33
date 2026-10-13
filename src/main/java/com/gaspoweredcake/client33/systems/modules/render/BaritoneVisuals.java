package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.gui.renderer.RoundedRectGeometry;
import com.gaspoweredcake.client33.pathing.IPathManager.VisualPath;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.renderer.Renderer2D;
import com.gaspoweredcake.client33.renderer.Renderer3D;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.world.HighwayBuilder;
import com.gaspoweredcake.client33.utils.render.PathViewMath;
import com.gaspoweredcake.client33.utils.render.VisualGeometry;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.CameraType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/** A read-only journey overlay and third-person camera for watching automated travel. */
public final class BaritoneVisuals extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCamera = settings.createGroup("AFK Camera");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final Setting<Boolean> route = sgGeneral.add(new BoolSetting.Builder().name("route")
        .description("Draw a feathered ribbon along the current planned path.").defaultValue(true).build());
    private final Setting<Boolean> markers = sgGeneral.add(new BoolSetting.Builder().name("goal-marker")
        .description("Draw a warm destination ring when the backend supplies a concrete goal.").defaultValue(true).build());
    private final Setting<Boolean> hud = sgGeneral.add(new BoolSetting.Builder().name("journey-card")
        .description("Show task status and progress within the current path segment.").defaultValue(true).build());
    private final Setting<Boolean> animate = sgGeneral.add(new BoolSetting.Builder().name("animation")
        .description("Subtle traveling light and rotating goal rings. Disable for a still presentation.").defaultValue(true).build());
    private final Setting<Boolean> throughWalls = sgGeneral.add(new BoolSetting.Builder().name("through-walls")
        .description("Show the route through terrain. Off keeps the view less cluttered.").defaultValue(false).build());
    private final Setting<Integer> budget = sgGeneral.add(new IntSetting.Builder().name("path-nodes")
        .description("Maximum path nodes in the visual snapshot.").defaultValue(128).range(2, 256).sliderRange(16, 256).build());
    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder().name("render-distance")
        .description("Draw route and goal geometry within this distance of the player.").defaultValue(64).range(8, 256).sliderRange(16, 128).build());
    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder().name("ribbon-width")
        .description("Ribbon core width in world blocks; feathered edges add a soft halo.").defaultValue(0.12).range(0.03, 0.5).sliderRange(0.03, 0.3).build());
    private final Setting<Boolean> cinematic = sgCamera.add(new BoolSetting.Builder().name("smooth-watch-camera")
        .description("Ease third-person rear camera turns while a path task is active. Player and pathing rotation are unchanged.").defaultValue(true).build());
    private final Setting<Double> cameraDistance = sgCamera.add(new DoubleSetting.Builder().name("camera-distance")
        .description("Third-person watching distance; terrain collision remains enabled.").defaultValue(6).range(1, 16).sliderRange(2, 12).build());
    private final Setting<Double> cameraPitch = sgCamera.add(new DoubleSetting.Builder().name("view-pitch")
        .description("A steady downward viewing angle instead of following every mining glance.").defaultValue(18).range(-20, 65).sliderRange(0, 45).build());
    private final Setting<Double> cameraResponse = sgCamera.add(new DoubleSetting.Builder().name("camera-response")
        .description("Turn response speed. Lower values make a calmer camera.").defaultValue(4).range(1, 12).sliderRange(1, 12).build());
    private final Setting<SettingColor> nearColor = sgColors.add(new ColorSetting.Builder().name("near-route")
        .defaultValue(new SettingColor(Color.fromHsv(145, 0.50, 0.94, 180).getPacked())).build());
    private final Setting<SettingColor> farColor = sgColors.add(new ColorSetting.Builder().name("far-route")
        .defaultValue(new SettingColor(Color.fromHsv(205, 0.55, 0.95, 140).getPacked())).build());
    private final Setting<SettingColor> goalColor = sgColors.add(new ColorSetting.Builder().name("destination")
        .defaultValue(new SettingColor(Color.fromHsv(48, 0.55, 1, 210).getPacked())).build());

    private VisualPath view = VisualPath.empty();
    private ClientLevel world;
    private int refresh;
    private double time;
    private boolean cameraReady;
    private long cameraSample;
    public float watchYaw, watchPitch;
    private double[] nearHsv, farHsv;
    private final Color core0 = new Color(), core1 = new Color(), clear0 = new Color(), clear1 = new Color();

    public BaritoneVisuals() { super(Categories.Render, "baritone-visuals", "A calm third-person camera, luminous route and journey card for watching Baritone."); }

    @Override public void onActivate() { reset(); }
    @Override public void onDeactivate() { reset(); }
    private void reset() { view = VisualPath.empty(); world = null; refresh = 0; time = 0; cameraReady = false; cameraSample = 0; }

    @EventHandler private void onTick(TickEvent.Post event) {
        if (world != mc.level) { reset(); world = mc.level; }
        if (mc.player == null || world == null) { reset(); return; }
        if (refresh-- <= 0) { view = PathManagers.get().getVisualPath(budget.get()); refresh = 4; }
        if (!watchCamera()) cameraReady = false;
    }

    public boolean watchCamera() {
        return isActive() && cinematic.get() && view.active() && world == mc.level && mc.player != null
            && mc.getCameraEntity() == mc.player && !mc.player.isSleeping()
            && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK
            && !Modules.get().isActive(Freecam.class) && !Modules.get().isActive(FreeLook.class)
            && !Modules.get().isActive(HighwayBuilder.class);
    }

    public float watchDistance() { return cameraDistance.get().floatValue(); }

    /** Called by Camera only; this never writes player angles or sends packets. */
    public void sampleCamera(float yaw, float pitch) {
        long now = System.nanoTime();
        if (!cameraReady || now - cameraSample > 500_000_000L) {
            watchYaw = yaw; watchPitch = pitch; cameraReady = true;
        }
        double elapsed = cameraSample == 0 ? 0 : (now - cameraSample) / 1e9;
        cameraSample = now;
        if (mc.isPaused()) return;
        watchYaw = (float) PathViewMath.angle(watchYaw, yaw, elapsed, cameraResponse.get());
        watchPitch = (float) PathViewMath.pitch(watchPitch, cameraPitch.get(), elapsed, cameraResponse.get());
    }

    @EventHandler private void onRender(Render3DEvent event) {
        if (world != mc.level || mc.player == null || !view.active()) return;
        if (!mc.isPaused() && !view.paused() && Double.isFinite(event.frameTime)) time += Math.clamp(event.frameTime, 0, 0.1);
        Renderer3D renderer = throughWalls.get() ? event.renderer : event.depthRenderer;
        double along = 0, maximum = distance.get();
        nearHsv = nearColor.get().toHsv(); farHsv = farColor.get().toHsv();
        if (route.get()) for (int i = 1; i < view.nodes().size(); i++) {
            BlockPos a = view.nodes().get(i - 1), b = view.nodes().get(i);
            double ax = a.getX() + 0.5, ay = a.getY() + 0.09, az = a.getZ() + 0.5;
            double bx = b.getX() + 0.5, by = b.getY() + 0.09, bz = b.getZ() + 0.5;
            double dx = bx - ax, dy = by - ay, dz = bz - az, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1e-6 || length > 10) continue;
            double da = mc.player.distanceToSqr(ax, ay, az), db = mc.player.distanceToSqr(bx, by, bz);
            if (Math.min(da, db) > maximum * maximum) { along += length; continue; }
            tint(along, Math.sqrt(da), maximum, core0); tint(along + length, Math.sqrt(db), maximum, core1);
            clear0.set(core0).a(0); clear1.set(core1).a(0);
            double horizontal = Math.hypot(dx, dz), sx = horizontal < 1e-6 ? 1 : -dz / horizontal, sz = horizontal < 1e-6 ? 0 : dx / horizontal;
            strip(renderer, ax, ay, az, bx, by, bz, sx, sz, -3, -1, clear0, core0, core1, clear1);
            strip(renderer, ax, ay, az, bx, by, bz, sx, sz, -1, 1, core0, core0, core1, core1);
            strip(renderer, ax, ay, az, bx, by, bz, sx, sz, 1, 3, core0, clear0, clear1, core1);
            along += length;
        }
        BlockPos goal = view.goal();
        if (markers.get() && goal != null && mc.player.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5) <= maximum * maximum) {
            double x = goal.getX() + 0.5, y = goal.getY() + 0.12, z = goal.getZ() + 0.5;
            Color color = goalColor.get();
            double angle = animate.get() ? time * 0.35 : 0;
            VisualGeometry.ring(renderer, x, y, z, 0.65, angle, Math.PI * 1.65, 48, color);
            VisualGeometry.ring(renderer, x, y + 0.035, z, 0.9, -angle, Math.PI * 1.2, 36, new Color(color).a(color.a / 3));
            Color transparent = new Color(color).a(0);
            renderer.line(x, y, z, x, y + 2.2, z, color, transparent);
        }
    }

    private void tint(double along, double range, double maximum, Color out) {
        double t = Math.clamp(along / maximum, 0, 1), fade = 1 - Math.clamp(range / maximum, 0, 1);
        Color near = nearColor.get(), far = farColor.get();
        double hue = nearHsv[0] + PathViewMath.wrap(farHsv[0] - nearHsv[0]) * t;
        double saturation = nearHsv[1] + (farHsv[1] - nearHsv[1]) * t;
        double value = nearHsv[2] + (farHsv[2] - nearHsv[2]) * t;
        double pulse = animate.get() && !view.paused() ? PathViewMath.pulse(along, time) : 0;
        saturation *= 1 - pulse * 0.15;
        value = Math.min(1, value + pulse * 0.08);
        out.set(Color.fromHsv(hue, saturation, value,
            (int) ((near.a + (far.a - near.a) * t) * Math.min(1, fade * 4))));
    }

    private void strip(Renderer3D r, double ax, double ay, double az, double bx, double by, double bz,
                       double sx, double sz, double left, double right, Color a, Color b, Color c, Color d) {
        double w = width.get() / 2;
        r.quad(ax + sx * w * left, ay, az + sz * w * left, ax + sx * w * right, ay, az + sz * w * right,
            bx + sx * w * right, by, bz + sz * w * right, bx + sx * w * left, by, bz + sz * w * left, b, c, d, a);
    }

    @EventHandler private void onHud(Render2DEvent event) {
        if (!hud.get() || world != mc.level || mc.player == null || !view.active()
            || mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden) return;
        double x = 18, w = Math.min(380, event.screenWidth - 36);
        if (w < 140 || event.screenHeight < 170) return;
        TextRenderer text = TextRenderer.get();
        text.begin(event.graphics, 0.9);
        double h = text.getHeight(), cardHeight = h * 3 + 42;
        if (cardHeight > event.screenHeight - 36) { text.end(); return; }
        double y = event.screenHeight - cardHeight - 18;
        String status = view.paused() ? "Paused" : view.status();
        while (text.getWidth(status) > w - 28 && status.length() > 1) status = status.substring(0, status.length() - 1);
        boolean path = view.nodes().size() > 1;
        String detail = path ? String.format(Locale.ROOT, "Current path  %.0f%%", view.progress() * 100) : "Waiting for the next path";
        if (path && view.remainingTicks() >= 0 && !view.paused()) detail += String.format(Locale.ROOT, "  /  ~%.0fs", view.remainingTicks() / 20);
        while (text.getWidth(detail) > w - 28 && detail.length() > 1) detail = detail.substring(0, detail.length() - 1);
        Renderer2D.COLOR.begin();
        rounded(x, y + 3, w, cardHeight, 10, Color.fromHsv(210, 0.3, 0.02, 110));
        rounded(x, y, w, cardHeight, 10, Color.fromHsv(205, 0.4, 0.12, 235));
        Color accent = view.paused() ? goalColor.get() : nearColor.get();
        rounded(x + 12, y + cardHeight - 16, w - 24, 4, 2, Color.fromHsv(205, 0.2, 0.3, 220));
        if (path && view.progress() > 0) rounded(x + 12, y + cardHeight - 16, (w - 24) * view.progress(), 4, 2, accent);
        Renderer2D.COLOR.render();
        text.render("BARITONE  /  WATCH", x + 14, y + 10, accent);
        text.render(status, x + 14, y + h + 16, Color.fromHsv(55, 0.07, 1));
        text.render(detail, x + 14, y + h * 2 + 22, Color.fromHsv(195, 0.15, 0.85));
        text.end();
    }

    private void rounded(double x, double y, double w, double h, double radius, Color color) {
        var mesh = Renderer2D.COLOR.triangles;
        Color clear = new Color(color).a(0);
        RoundedRectGeometry.draw(x, y, w, h, radius, (x0, y0, a0, x1, y1, a1, x2, y2, a2) -> {
            mesh.ensureTriCapacity();
            mesh.triangle(mesh.vec2(x0, y0).color(a0 == 0 ? clear : color).next(),
                mesh.vec2(x1, y1).color(a1 == 0 ? clear : color).next(), mesh.vec2(x2, y2).color(a2 == 0 ? clear : color).next());
        });
    }

    @Override public String getInfoString() { return view.paused() ? "Paused" : view.active() ? "Watching" : "Idle"; }
}
