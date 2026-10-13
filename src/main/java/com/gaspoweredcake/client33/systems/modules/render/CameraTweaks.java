/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.game.ChangePerspectiveEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.utils.render.CameraMotion;
import com.gaspoweredcake.client33.events.client33.MouseScrollEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.CameraType;
import com.mojang.blaze3d.platform.InputConstants;

public class CameraTweaks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScrolling = settings.createGroup("Scrolling");

    // General

    private final Setting<Boolean> clip = sgGeneral.add(new BoolSetting.Builder()
        .name("clip")
        .description("Allows the camera to clip through blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> cameraDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("camera-distance")
        .description("The distance the third person camera is from the player.")
        .defaultValue(4)
        .range(0, CameraMotion.MAX_DISTANCE)
        .sliderMax(16)
        .onChanged(this::setDistance)
        .build()
    );

    // Scrolling

    private final Setting<Boolean> scrollingEnabled = sgScrolling.add(new BoolSetting.Builder()
        .name("scrolling")
        .description("Allows you to scroll to change camera distance.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> scrollKeybind = sgScrolling.add(new KeybindSetting.Builder()
        .name("bind")
        .description("Binds camera distance scrolling to a key.")
        .visible(scrollingEnabled::get)
        .defaultValue(Keybind.fromKey(InputConstants.KEY_LALT))
        .build()
    );

    private final Setting<Double> scrollSensitivity = sgScrolling.add(new DoubleSetting.Builder()
        .name("sensitivity")
        .description("Sensitivity of the scroll wheel when changing the cameras distance.")
        .visible(scrollingEnabled::get)
        .defaultValue(1)
        .range(0.01, 10)
        .sliderRange(0.01, 3)
        .build()
    );

    private final Setting<Boolean> smoothDistance = sgScrolling.add(new BoolSetting.Builder()
        .name("smooth-distance").description("Ease camera distance changes after scrolling.")
        .defaultValue(true).visible(scrollingEnabled::get).build());
    private final Setting<Double> smoothSpeed = sgScrolling.add(new DoubleSetting.Builder()
        .name("smoothing-speed").description("How quickly the camera reaches its new distance. Higher values respond faster.")
        .defaultValue(8).range(1, 20).sliderRange(1, 20)
        .visible(() -> scrollingEnabled.get() && smoothDistance.get()).build());

    public double distance;
    private double targetDistance;

    private void setDistance(double value) {
        distance = targetDistance = CameraMotion.distance(value);
    }

    public CameraTweaks() {
        super(Categories.Render, "camera-tweaks", "Allows modification of the third person camera.");
    }

    @Override
    public void onActivate() {
        setDistance(cameraDistance.get());
    }

    @EventHandler
    private void onPerspectiveChanged(ChangePerspectiveEvent event) {
        setDistance(cameraDistance.get());
    }

    @EventHandler
    private void onMouseScroll(MouseScrollEvent event) {
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON || mc.gui.screen() != null || !scrollingEnabled.get() || (scrollKeybind.get().isSet() && !scrollKeybind.get().isPressed()))
            return;

        if (scrollSensitivity.get() > 0) {
            targetDistance = CameraMotion.scroll(targetDistance, event.value, scrollSensitivity.get());
            if (!smoothDistance.get()) distance = targetDistance;
            event.cancel();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.isPaused()) return;
        distance = smoothDistance.get()
            ? CameraMotion.approach(distance, targetDistance, event.frameTime, smoothSpeed.get())
            : targetDistance;
    }

    public boolean clip() {
        return isActive() && clip.get();
    }
}
