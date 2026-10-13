/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.entity.DropItemsEvent;
import com.gaspoweredcake.client33.events.entity.player.ClipAtLedgeEvent;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.movement.Flight;
import com.gaspoweredcake.client33.systems.modules.movement.NoSlow;
import com.gaspoweredcake.client33.systems.modules.movement.Sprint;
import com.gaspoweredcake.client33.systems.modules.player.Reach;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.gaspoweredcake.client33.Client33.mc;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {
    @Shadow
    public abstract Abilities getAbilities();

    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    protected void clipAtLedge(CallbackInfoReturnable<Boolean> cir) {
        if (!level().isClientSide()) return;

        ClipAtLedgeEvent event = Client33.EVENT_BUS.post(ClipAtLedgeEvent.get());
        if (event.isSet()) cir.setReturnValue(event.isClip());
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void onDropItem(ItemStack itemStack, boolean thrownFromHand, CallbackInfoReturnable<ItemEntity> cir) {
        if (level().isClientSide() && !itemStack.isEmpty()) {
            if (Client33.EVENT_BUS.post(DropItemsEvent.get(itemStack)).isCancelled()) cir.setReturnValue(null);
        }
    }

    @Inject(method = "isSpectator", at = @At("HEAD"), cancellable = true)
    private void onIsSpectator(CallbackInfoReturnable<Boolean> cir) {
        if (mc.getConnection() == null) cir.setReturnValue(false);
    }

    @Inject(method = "isCreative", at = @At("HEAD"), cancellable = true)
    private void onIsCreative(CallbackInfoReturnable<Boolean> cir) {
        if (mc.getConnection() == null) cir.setReturnValue(false);
    }

    @ModifyReturnValue(method = "getSpeed", at = @At("RETURN"))
    private float onGetSpeed(float original) {
        if (!level().isClientSide()) return original;
        if (!Modules.get().get(NoSlow.class).slowness()) return original;

        float walkSpeed = getAbilities().getWalkingSpeed();

        if (original < walkSpeed) {
            if (isSprinting()) return (float) (walkSpeed * 1.30000001192092896);
            else return walkSpeed;
        }

        return original;
    }

    @Inject(method = "getFlyingSpeed", at = @At("HEAD"), cancellable = true)
    private void onGetFlyingSpeed(CallbackInfoReturnable<Float> cir) {
        if (!level().isClientSide()) return;

        float speed = Modules.get().get(Flight.class).getFlyingSpeed();
        if (speed != -1) cir.setReturnValue(speed);
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private boolean keepSprint$setDeltaMovement(Player instance, Vec3 vec3d) {
        return mc == null || instance != mc.player || Modules.get().get(Sprint.class).stopSprinting();
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
    private boolean keepSprint$setSprinting(Player instance, boolean b) {
        return mc == null || instance != mc.player || Modules.get().get(Sprint.class).stopSprinting();
    }

    @ModifyReturnValue(method = "blockInteractionRange", at = @At("RETURN"))
    private double modifyBlockInteractionRange(double original) {
        if (!level().isClientSide() || mc == null || (Object) this != mc.player) return original;
        return Modules.get().get(Reach.class).blockRange(original);
    }

    @ModifyReturnValue(method = "entityInteractionRange", at = @At("RETURN"))
    private double modifyEntityInteractionRange(double original) {
        if (!level().isClientSide() || mc == null || (Object) this != mc.player) return original;
        return Modules.get().get(Reach.class).entityRange(original);
    }
}
