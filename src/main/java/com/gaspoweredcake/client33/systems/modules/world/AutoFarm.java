package com.gaspoweredcake.client33.systems.modules.world;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WVerticalList;
import com.gaspoweredcake.client33.mixininterface.IMultiPlayerGameMode;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.player.FindItemResult;
import com.gaspoweredcake.client33.utils.player.InvUtils;
import com.gaspoweredcake.client33.utils.player.Rotations;
import com.gaspoweredcake.client33.utils.world.FarmAttempts;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

import java.util.LinkedHashMap;

/** Local farming using ordinary, reach-limited block interactions. */
public final class AutoFarm extends Module {
    public enum Mode { Cycle, Harvest, Plant, Bonemeal, Hoe }
    public enum Seed { Wheat, Carrot, Potato, Beetroot, NetherWart }
    public enum Aim { Silent, Visible, None }
    private enum Action { Harvest, Plant, Bonemeal, Hoe }

    private final SettingGroup operation = settings.createGroup("Operation");
    private final SettingGroup cycle = settings.createGroup("Cycle Options");
    private final SettingGroup timing = settings.createGroup("Timing", false);
    private final SettingGroup advanced = settings.createGroup("Advanced", false);

    private final Setting<Mode> mode = operation.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Cycle harvests mature crops and replants their original type.")
        .defaultValue(Mode.Cycle).onChanged(value -> reset()).build());
    private final Setting<Double> radius = operation.add(new DoubleSetting.Builder()
        .name("radius-blocks").description("Search distance from your eyes; capped by your block reach.")
        .defaultValue(4.5).range(1, 6).sliderRange(1, 6).build());
    private final Setting<Seed> seed = operation.add(new EnumSetting.Builder<Seed>()
        .name("plant-empty-with").description("Crop for empty soil. Harvested crops keep their original type.")
        .defaultValue(Seed.Wheat).visible(() -> mode.get() == Mode.Cycle || mode.get() == Mode.Plant).build());
    private final Setting<Boolean> till = cycle.add(new BoolSetting.Builder()
        .name("prepare-soil").description("Also hoe clear dirt and grass. Requires a hoe in your hotbar.")
        .defaultValue(false).visible(() -> mode.get() == Mode.Cycle).build());
    private final Setting<Boolean> fertilize = cycle.add(new BoolSetting.Builder()
        .name("use-bonemeal").description("Grow immature crops after planting. Consumes bone meal.")
        .defaultValue(false).visible(() -> mode.get() == Mode.Cycle).build());
    private final Setting<Integer> harvestDelay = timing.add(new IntSetting.Builder()
        .name("harvest-delay-ms").description("Minimum milliseconds between harvests.")
        .defaultValue(150).range(0, 5000).sliderRange(0, 1000)
        .visible(() -> mode.get() == Mode.Cycle || mode.get() == Mode.Harvest).build());
    private final Setting<Integer> useDelay = timing.add(new IntSetting.Builder()
        .name("use-delay-ms").description("Minimum milliseconds between planting, fertilizing or hoeing.")
        .defaultValue(200).range(0, 5000).sliderRange(0, 1000).visible(() -> mode.get() != Mode.Harvest).build());
    private final Setting<Aim> aim = advanced.add(new EnumSetting.Builder<Aim>()
        .name("rotation").description("Silent turns on the server; Visible also turns your camera.")
        .defaultValue(Aim.Silent).build());
    private final Setting<Boolean> sight = advanced.add(new BoolSetting.Builder()
        .name("line-of-sight").description("Only interact with blocks visible from your eyes.")
        .defaultValue(true).build());

    private record Replant(Seed seed, long expires) { }
    private record Target(BlockPos pos, BlockState state, Action action, Seed seed) { }
    private final LinkedHashMap<BlockPos, Replant> replants = new LinkedHashMap<>();
    private final FarmAttempts<BlockPos, BlockState> attempts = new FarmAttempts<>(256);
    private long generation, nextHarvest, nextUse;
    private String status = "Ready";
    private String missing;
    private Object farmWorld;

    public AutoFarm() {
        super(Categories.World, "auto-farm", "Harvest, replant and tend nearby crops. Supplies: hotbar or offhand.");
    }

    @Override public void onActivate() { reset(); }
    @Override public void onDeactivate() { reset(); }

    private void reset() {
        generation++;
        farmWorld = mc.level;
        nextHarvest = nextUse = 0;
        if (replants != null) replants.clear();
        if (attempts != null) attempts.clear();
        status = "Ready";
    }

    @Override public String getInfoString() { return status; }

    @Override public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        list.add(theme.label("Choose a mode for your farming task."));
        list.add(theme.label("Keep seeds / tools in your hotbar or offhand."));
        list.add(theme.label("Pauses in menus and during manual actions."));
        return list;
    }

    private boolean paused() {
        return mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null
            || mc.player.isSpectator() || mc.player.isUsingItem()
            || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown();
    }

    @EventHandler private void onTick(TickEvent.Pre event) {
        if (farmWorld != mc.level) reset();
        if (paused()) { status = "Paused"; return; }
        long now = now();
        replants.entrySet().removeIf(entry -> entry.getValue().expires() <= now);
        missing = null;
        Target best = null;
        double bestScore = Double.MAX_VALUE;
        int extent = (int) Math.ceil(Math.min(radius.get(), mc.player.blockInteractionRange()));
        BlockPos origin = mc.player.blockPosition();
        for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-extent, -extent, -extent), origin.offset(extent, extent, extent))) {
            if (!mc.level.getChunkSource().hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) continue;
            BlockState state = mc.level.getBlockState(cursor);
            Target target = choose(cursor, state);
            if (target == null || !reachable(target) || !attempts.ready(cursor, state, now)) continue;
            if (now < (target.action() == Action.Harvest ? nextHarvest : nextUse)) continue;
            if (!supplied(target)) continue;
            // Replant before opening more holes, then harvest, then prepare/grow.
            double score = mc.player.getEyePosition().distanceToSqr(point(target)) + switch (target.action()) {
                case Plant -> replants.containsKey(cursor.above()) ? 0 : 100;
                case Harvest -> 200;
                case Hoe -> 300;
                case Bonemeal -> 400;
            };
            if (score < bestScore) { bestScore = score; best = target; }
        }
        if (best == null) { status = missing != null ? missing : "Waiting for crops"; return; }
        Target target = best;
        long token = generation;
        var world = mc.level;
        var player = mc.player;
        Runnable action = () -> {
            if (!isActive() || token != generation || mc.level != world || mc.player != player || paused()) return;
            if (!world.getBlockState(target.pos()).equals(target.state())) return;
            Target current = choose(target.pos(), target.state());
            if (!target.equals(current) || !reachable(target) || !supplied(target)) return;
            perform(target);
        };
        Vec3 point = point(target);
        if (aim.get() == Aim.None) action.run();
        else {
            if (aim.get() == Aim.Visible) {
                mc.player.setYRot((float) Rotations.getYaw(point));
                mc.player.setXRot((float) Rotations.getPitch(point));
            }
            Rotations.rotate(Rotations.getYaw(point), Rotations.getPitch(point), -50, action);
        }
    }

    private Target choose(BlockPos pos, BlockState state) {
        Mode selected = mode.get();
        Seed crop = crop(state);
        if (crop != null) {
            if ((selected == Mode.Cycle || selected == Mode.Harvest) && mature(state))
                return new Target(pos.immutable(), state, Action.Harvest, crop);
            if ((selected == Mode.Bonemeal || selected == Mode.Cycle && fertilize.get()) && !mature(state) && crop != Seed.NetherWart)
                return new Target(pos.immutable(), state, Action.Bonemeal, crop);
        }
        if (!mc.level.getBlockState(pos.above()).isAir()) return null;
        if (selected == Mode.Cycle || selected == Mode.Plant) {
            Replant remembered = replants.get(pos.above());
            Seed desired = remembered != null ? remembered.seed() : seed.get();
            if (soil(state, desired)) return new Target(pos.immutable(), state, Action.Plant, desired);
        }
        if ((selected == Mode.Hoe || selected == Mode.Cycle && till.get())
            && (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH)))
            return new Target(pos.immutable(), state, Action.Hoe, null);
        return null;
    }

    private boolean supplied(Target target) {
        if (target.action() == Action.Harvest) {
            if (mode.get() != Mode.Cycle || InvUtils.findInHotbar(item(target.seed())).found()) return true;
            missing = "Need " + target.seed() + " to replant";
            return false;
        }
        if (supply(target).found()) return true;
        missing = switch (target.action()) {
            case Plant -> "Need " + target.seed();
            case Bonemeal -> "Need bone meal";
            case Hoe -> "Need usable hoe";
            default -> "Need supplies";
        };
        return false;
    }

    private FindItemResult supply(Target target) {
        return switch (target.action()) {
            case Plant -> InvUtils.findInHotbar(item(target.seed()));
            case Bonemeal -> InvUtils.findInHotbar(Items.BONE_MEAL);
            case Hoe -> InvUtils.findInHotbar(stack -> stack.getItem() instanceof HoeItem
                && (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 1));
            default -> new FindItemResult(-1, 0);
        };
    }

    private boolean reachable(Target target) {
        Vec3 point = point(target);
        double reach = Math.min(radius.get(), mc.player.blockInteractionRange());
        if (mc.player.getEyePosition().distanceToSqr(point) > reach * reach) return false;
        if (!sight.get()) return true;
        BlockHitResult hit = mc.level.clip(new ClipContext(mc.player.getEyePosition(), point,
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target.pos());
    }

    private static Vec3 point(Target target) {
        // Farmland is 15/16 high; aim just inside its upper face.
        double height = target.action() == Action.Plant && target.state().is(Blocks.FARMLAND) ? 0.937 : 0.999;
        return new Vec3(target.pos().getX() + 0.5, target.pos().getY() + height, target.pos().getZ() + 0.5);
    }

    private void perform(Target target) {
        long now = now();
        attempts.record(target.pos(), target.state(), now);
        status = target.action().toString();
        if (target.action() == Action.Harvest) {
            nextHarvest = now + harvestDelay.get();
            if (mode.get() == Mode.Cycle) {
                replants.put(target.pos(), new Replant(target.seed(), now + 300_000));
                while (replants.size() > 256) replants.pollFirstEntry();
            }
            mc.gameMode.startDestroyBlock(target.pos(), Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND);
            return;
        }
        FindItemResult found = supply(target);
        if (!found.found()) return;
        nextUse = now + useDelay.get();
        var player = mc.player;
        int previous = player.getInventory().getSelectedSlot();
        InteractionHand hand = found.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        try {
            if (!found.isOffhand()) select(found.slot());
            mc.gameMode.useItemOn(player, hand, new BlockHitResult(point(target), Direction.UP, target.pos(), false));
            player.swing(hand);
        } finally {
            if (mc.player == player && !found.isOffhand() && player.getInventory().getSelectedSlot() == found.slot()) select(previous);
        }
        // Preserve replant identity until expiry, including predicted/rolled-back placements.
    }

    private void select(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        ((IMultiPlayerGameMode) mc.gameMode).client33$syncSelected();
    }

    private static long now() { return System.nanoTime() / 1_000_000; }

    public static Seed crop(BlockState state) {
        if (state.is(Blocks.WHEAT)) return Seed.Wheat;
        if (state.is(Blocks.CARROTS)) return Seed.Carrot;
        if (state.is(Blocks.POTATOES)) return Seed.Potato;
        if (state.is(Blocks.BEETROOTS)) return Seed.Beetroot;
        if (state.is(Blocks.NETHER_WART)) return Seed.NetherWart;
        return null;
    }

    public static boolean mature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        return state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) == 3;
    }

    public static boolean soil(BlockState state, Seed seed) {
        return seed == Seed.NetherWart ? state.is(Blocks.SOUL_SAND) : state.is(Blocks.FARMLAND);
    }

    private static Item item(Seed seed) {
        return switch (seed) {
            case Wheat -> Items.WHEAT_SEEDS;
            case Carrot -> Items.CARROT;
            case Potato -> Items.POTATO;
            case Beetroot -> Items.BEETROOT_SEEDS;
            case NetherWart -> Items.NETHER_WART;
        };
    }
}
