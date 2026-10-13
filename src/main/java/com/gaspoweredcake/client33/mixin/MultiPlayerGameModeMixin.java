/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.entity.DropItemsEvent;
import com.gaspoweredcake.client33.events.entity.player.*;
import com.gaspoweredcake.client33.mixininterface.IMultiPlayerGameMode;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.player.BreakDelay;
import com.gaspoweredcake.client33.systems.modules.player.SpeedMine;
import com.gaspoweredcake.client33.utils.world.BlockUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.gaspoweredcake.client33.Client33.mc;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin implements IMultiPlayerGameMode {
    @Shadow
    private int destroyDelay;

    @Shadow
    protected abstract void ensureHasSentCarriedItem();

    @Shadow
    public abstract void startPrediction(ClientLevel level, PredictiveAction predictiveAction);

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void onHandleInventoryMouseClick(int containerId, int slotNum, int buttonNum, ContainerInput containerInput, Player player, CallbackInfo ci) {
        if (containerInput == ContainerInput.THROW && slotNum >= 0 && slotNum < player.containerMenu.slots.size()) {
            if (Client33.EVENT_BUS.post(DropItemsEvent.get(player.containerMenu.slots.get(slotNum).getItem())).isCancelled())
                ci.cancel();
        } else if (slotNum == -999) {
            // Clicking outside of inventory
            if (Client33.EVENT_BUS.post(DropItemsEvent.get(player.containerMenu.getCarried())).isCancelled())
                ci.cancel();
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (Client33.EVENT_BUS.post(StartBreakingBlockEvent.get(pos, direction)).isCancelled()) cir.setReturnValue(false);
    }

    // Keep vanilla's permission checks, target switching and prediction bookkeeping.
    @ModifyExpressionValue(method = "lambda$startDestroyBlock$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float instantMineProgress(float original, BlockState state, BlockPos pos, Direction direction, int sequence) {
        return original < 1 && Modules.get().get(SpeedMine.class).canInstantMine(pos, state) ? 1 : original;
    }

    @WrapOperation(method = "startDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startPrediction(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V", ordinal = 1))
    private void instantMinePrediction(MultiPlayerGameMode instance, ClientLevel level, PredictiveAction action, Operation<Void> original, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        SpeedMine speedMine = Modules.get().get(SpeedMine.class);
        boolean accelerated = speedMine.canInstantMine(pos, state) && state.getDestroyProgress(mc.player, level, pos) < 1;
        original.call(instance, level, action);
        // START is already submitted. Send STOP only after successful predicted destruction.
        if (accelerated && mc.level == level && !level.getBlockState(pos).equals(state)) {
            speedMine.markAccelerated(pos);
            startPrediction(level, sequence -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence));
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    public void useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult blockHit, CallbackInfoReturnable<InteractionResult> cir) {
        if (Client33.EVENT_BUS.post(InteractBlockEvent.get(player.getMainHandItem().isEmpty() ? InteractionHand.OFF_HAND : hand, blockHit)).isCancelled())
            cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttack(Player player, Entity entity, CallbackInfo ci) {
        if (Client33.EVENT_BUS.post(AttackEntityEvent.get(entity)).isCancelled()) ci.cancel();
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteract(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (Client33.EVENT_BUS.post(InteractEntityEvent.get(entity, hand)).isCancelled())
            cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "handleCreativeModeItemDrop", at = @At("HEAD"), cancellable = true)
    private void onHandleCreativeModeItemDrop(ItemStack clicked, CallbackInfo ci) {
        if (Client33.EVENT_BUS.post(DropItemsEvent.get(clicked)).isCancelled()) ci.cancel();
    }

    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", opcode = Opcodes.PUTFIELD, ordinal = 1))
    private void creativeBreakDelayChange(MultiPlayerGameMode instance, int value, Operation<Void> original) {
        BlockBreakingCooldownEvent event = Client33.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
        original.call(instance, event.cooldown);
    }

    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", opcode = Opcodes.PUTFIELD, ordinal = 2))
    private void survivalBreakDelayChange(MultiPlayerGameMode instance, int value, Operation<Void> original) {
        BlockBreakingCooldownEvent event = Client33.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
        original.call(instance, event.cooldown);
    }

    @WrapOperation(method = "startDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", opcode = Opcodes.PUTFIELD))
    private void creativeBreakDelayChange2(MultiPlayerGameMode instance, int value, Operation<Void> original) {
        BlockBreakingCooldownEvent event = Client33.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
        original.call(instance, event.cooldown);
    }

    @ModifyExpressionValue(method = "continueDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float modifyBlockBreakingDelta(float original, BlockPos pos, Direction direction) {
        if (Modules.get().get(BreakDelay.class).preventInstaBreak() && original >= 1) {
            BlockBreakingCooldownEvent event = Client33.EVENT_BUS.post(BlockBreakingCooldownEvent.get(destroyDelay));
            destroyDelay = event.cooldown;
            return 0;
        }
        if (mc.player == null || mc.level == null) return original;
        float progress = ((MultiPlayerGameModeAccessor) this).client33$getBreakingProgress();
        return Modules.get().get(SpeedMine.class).finishProgress(pos, mc.level.getBlockState(pos), progress, original);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Client33.EVENT_BUS.post(BreakBlockEvent.get(pos)).isCancelled()) cir.setReturnValue(false);
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        InteractItemEvent event = Client33.EVENT_BUS.post(InteractItemEvent.get(hand));
        if (event.toReturn != null) cir.setReturnValue(event.toReturn);
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onStopDestroyBlock(CallbackInfo ci) {
        if (BlockUtils.breaking) ci.cancel();
    }

    @Override
    public void client33$syncSelected() {
        ensureHasSentCarriedItem();
    }
}
