/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("positionReminder")
    void client33$setPositionReminder(int ticks);

    @Invoker("canStartSprinting")
    boolean client33$canStartSprinting();

    @Invoker("shouldStopRunSprinting")
    boolean client33$shouldStopRunSprinting();

    @Invoker("shouldStopSwimSprinting")
    boolean client33$shouldStopSwimSprinting();

    @Invoker("isSprintingPossible")
    boolean client33$isSprintingPossible(boolean allowShallowWater);

    @Invoker("isSlowDueToUsingItem")
    boolean client33$isSlowDueToUsingItem();

    @Invoker("sendIsSprintingIfNeeded")
    void client33$sendIsSprintingIfNeeded();
}
