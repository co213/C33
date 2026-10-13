package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WVerticalList;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.friends.Friends;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.combat.GunAim;
import com.gaspoweredcake.client33.utils.misc.Names;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.List;

/** Dedicated configurable gun aiming; firing and ballistics are owned by the server minigame. */
public final class CounterMineRage extends Module {
    public enum Activation { Always, WhileFiring }
    public enum FireButton { Attack, Use }
    public enum AimPoint { Head, Chest, Visible }
    public enum Priority { ClosestAngle, Nearest, LowestHealth }
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgProfile = settings.createGroup("Counter-Mine Profile");

    private final Setting<Activation> activation = sgGeneral.add(new EnumSetting.Builder<Activation>()
        .name("activation").description("Always tracks while holding a recognized gun; While Firing uses the configured fire key.")
        .defaultValue(Activation.Always).build());
    private final Setting<FireButton> fireButton = sgGeneral.add(new EnumSetting.Builder<FireButton>()
        .name("fire-button").description("Minecraft action used to fire your gun; configure it for the server controls.")
        .defaultValue(FireButton.Attack).visible(() -> activation.get() == Activation.WhileFiring).build());
    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range").description("Maximum gun aiming distance in blocks, independent of melee reach.")
        .defaultValue(128).range(1, 256).sliderRange(8, 128).build());
    private final Setting<Double> fov = sgTargeting.add(new DoubleSetting.Builder()
        .name("field-of-view").description("Horizontal acquisition cone; 360 permits instant turns in any direction.")
        .defaultValue(360).range(1, 360).sliderRange(30, 360).build());
    private final Setting<AimPoint> aimPoint = sgTargeting.add(new EnumSetting.Builder<AimPoint>()
        .name("aim-point").description("Head or chest; Visible prefers the head and falls back to exposed torso points.")
        .defaultValue(AimPoint.Visible).build());
    private final Setting<Priority> priority = sgTargeting.add(new EnumSetting.Builder<Priority>()
        .name("priority").description("How to select among valid opponents.")
        .defaultValue(Priority.ClosestAngle).build());
    private final Setting<Boolean> keepTarget = sgTargeting.add(new BoolSetting.Builder()
        .name("keep-target").description("Keep the current opponent while its selected aim point remains valid.")
        .defaultValue(true).build());
    private final Setting<Boolean> teams = sgTargeting.add(new BoolSetting.Builder()
        .name("respect-scoreboard-teams").description("Exclude allies reported by Minecraft scoreboard teams. Custom server team systems may need manual friend entries.")
        .defaultValue(true).build());
    private final Setting<Boolean> visible = sgTargeting.add(new BoolSetting.Builder()
        .name("require-visible").description("Require a clear collision ray to the selected point. Off permits aiming through cover; the server decides penetration.")
        .defaultValue(true).build());
    private final Setting<Boolean> ignoreInvisible = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-invisible").description("Exclude invisible players, including possible spectator representations.")
        .defaultValue(true).build());
    private final Setting<Double> prediction = sgTargeting.add(new DoubleSetting.Builder()
        .name("prediction-ticks").description("Optional observed-motion lead. Zero is direct hitscan aim; lead is capped at 0.75 blocks and ignores teleports.")
        .defaultValue(0).range(0, 4).sliderRange(0, 2).build());
    private final Setting<Boolean> serverOnly = sgProfile.add(new BoolSetting.Builder()
        .name("server-filter").description("Activate aiming only on the configured server hosts and their subdomains.")
        .defaultValue(true).build());
    private final Setting<List<String>> hosts = sgProfile.add(new StringListSetting.Builder()
        .name("server-hosts").description("Addresses permitted by Server Filter. Add an alternate server address here if needed.")
        .defaultValue("cherry.pizza").visible(serverOnly::get).build());
    private final Setting<Boolean> requireGun = sgProfile.add(new BoolSetting.Builder()
        .name("require-gun").description("Require a configured gun display name or base item. Add the held gun name below to calibrate the server profile.")
        .defaultValue(true).build());
    private final Setting<List<String>> gunNames = sgProfile.add(new StringListSetting.Builder()
        .name("gun-names").description("Case-insensitive whole-word gun aliases; formatting and skin suffixes are accepted. These are configurable CS-style names, not verified server item IDs.")
        .defaultValue("AK-47", "AK47", "M4A1-S", "M4A4", "AWP", "SSG 08", "Glock-18", "USP-S", "P2000",
            "P250", "Five-SeveN", "Tec-9", "CZ75-Auto", "Desert Eagle", "Deagle", "R8 Revolver", "Dual Berettas",
            "Galil AR", "FAMAS", "AUG", "SG 553", "G3SG1", "SCAR-20", "MAC-10", "MP9", "MP7", "MP5-SD",
            "UMP-45", "P90", "PP-Bizon", "Nova", "XM1014", "MAG-7", "Sawed-Off", "M249", "Negev")
        .visible(requireGun::get).build());
    private final Setting<List<Item>> gunItems = sgProfile.add(new ItemListSetting.Builder()
        .name("gun-base-items").description("Optional base-item allowlist. Shared gun/grenade base items can also match nonguns; prefer gun names.")
        .visible(requireGun::get).build());

    private record Candidate(Player player, GunAim.Solution aim, double score) {}
    private Player target;
    private Object owner, world;
    private String state = "Idle";

    public CounterMineRage() {
        super(Categories.Combat, "counter-mine-rage", "Separate instant gun aiming profile for Counter-Mine 2 / CherryPizza.");
    }

    @Override public void onActivate() { clear(); }
    @Override public void onDeactivate() { clear(); }
    private void clear() { target = null; owner = world = null; state = "Idle"; }

    private boolean ready() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null || !mc.player.isAlive()
            || mc.player.isSpectator() || mc.player.isSleeping() || mc.gui.screen() != null || mc.isPaused()) {
            state = "Paused"; return false;
        }
        if (serverOnly.get() && (mc.getCurrentServer() == null || !GunAim.serverMatches(mc.getCurrentServer().ip, hosts.get()))) {
            state = "Server filter"; return false;
        }
        var held = mc.player.getMainHandItem();
        if (held.isEmpty() || requireGun.get() && !gunItems.get().contains(held.getItem()) && !GunAim.weaponMatches(Names.get(held), gunNames.get())) {
            state = "No recognized gun"; return false;
        }
        if (activation.get() == Activation.WhileFiring && !(fireButton.get() == FireButton.Attack ? mc.options.keyAttack.isDown() : mc.options.keyUse.isDown())) {
            state = "Waiting for fire"; return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTick(TickEvent.Pre event) {
        if (owner != mc.player || world != mc.level) { clear(); owner = mc.player; world = mc.level; }
        if (!ready()) { target = null; return; }
        Vec3 eye = mc.player.getEyePosition();
        Candidate best = null;
        for (Player player : mc.level.players()) {
            Candidate candidate = candidate(player, eye);
            if (candidate == null) continue;
            if (keepTarget.get() && player == target) { best = candidate; break; }
            if (best == null || candidate.score < best.score) best = candidate;
        }
        if (best == null) { target = null; state = "No valid opponent"; return; }
        target = best.player;
        float yaw = (float) best.aim.yaw(), pitch = (float) best.aim.pitch();
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        // Guns may fire during vanilla key handling, before sendPosition. Synchronize aim first.
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
        state = target.getName().getString();
    }

    private Candidate candidate(Player player, Vec3 eye) {
        if (player == mc.player || player == mc.getCameraEntity() || player.level() != mc.level || !player.isAlive()
            || player.isDeadOrDying() || player.isSpectator() || ignoreInvisible.get() && player.isInvisible()
            || !Friends.get().shouldAttack(player) || teams.get() && mc.player.isAlliedTo(player)) return null;
        if (mc.player.distanceToSqr(player) > (range.get() + 2) * (range.get() + 2)) return null;
        var box = player.getBoundingBox();
        Vec3 head = new Vec3(player.getX(), player.getEyeY(), player.getZ());
        Vec3 chest = new Vec3(player.getX(), box.minY + box.getYsize() * 0.6, player.getZ());
        Vec3 lower = new Vec3(player.getX(), box.minY + box.getYsize() * 0.4, player.getZ());
        List<Vec3> points = switch (aimPoint.get()) {
            case Head -> List.of(head);
            case Chest -> List.of(chest);
            case Visible -> List.of(head, chest, lower);
        };
        GunAim.Point velocity = new GunAim.Point(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
        for (Vec3 point : points) {
            GunAim.Point lead = GunAim.predict(new GunAim.Point(point.x, point.y, point.z), velocity, prediction.get());
            point = new Vec3(lead.x(), lead.y(), lead.z());
            GunAim.Solution aim = GunAim.solve(new GunAim.Point(eye.x, eye.y, eye.z), lead);
            if (aim == null || aim.distance() > range.get() || !GunAim.inCone(aim, mc.player.getYRot(), fov.get())) continue;
            if (visible.get() && mc.level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) continue;
            double score = switch (priority.get()) {
                case Nearest -> aim.distance();
                case LowestHealth -> player.getHealth() + player.getAbsorptionAmount();
                case ClosestAngle -> Math.pow(GunAim.angleError(aim.yaw(), mc.player.getYRot()), 2) + Math.pow(aim.pitch() - mc.player.getXRot(), 2);
            };
            return new Candidate(player, aim, score);
        }
        return null;
    }

    @Override public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        list.add(theme.label("Configure the server profile and weapon names. Aiming uses visible Minecraft player positions; server recoil, spread, penetration and custom team rules are not exposed by the public listing.", 420));
        list.add(theme.button("Add held gun name")).expandX().widget().action = () -> {
            if (mc.player == null || mc.player.getMainHandItem().isEmpty()) { warning("Hold the gun first."); return; }
            String name = Names.get(mc.player.getMainHandItem());
            if (name.isBlank()) { warning("The held item has no display name."); return; }
            var aliases = new ArrayList<>(gunNames.get());
            if (!aliases.contains(name)) { aliases.add(name); gunNames.set(aliases); }
            info("Added held gun name: %s", name);
        };
        return list;
    }
    public Player getTarget() { return isActive() ? target : null; }
    @Override public String getInfoString() { return state; }
}
