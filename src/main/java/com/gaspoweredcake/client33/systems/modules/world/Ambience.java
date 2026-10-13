/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.world;

import com.gaspoweredcake.client33.settings.BoolSetting;
import com.gaspoweredcake.client33.settings.ColorSetting;
import com.gaspoweredcake.client33.settings.Setting;
import com.gaspoweredcake.client33.settings.SettingGroup;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import com.gaspoweredcake.client33.utils.render.color.Color;

/**
 * @author Walaryne
 */
public class Ambience extends Module {
    private final SettingGroup sgSky = settings.createGroup("Sky");
    private final SettingGroup sgWorld = settings.createGroup("World");

    // Sky

    public final Setting<Boolean> endSky = sgSky.add(new BoolSetting.Builder()
        .name("end-sky")
        .description("Makes the sky like the end.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> customSkyColor = sgSky.add(new BoolSetting.Builder()
        .name("custom-sky-color")
        .description("Whether the sky color should be changed.")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> overworldSkyColor = sgSky.add(new ColorSetting.Builder()
        .name("overworld-sky-color")
        .description("The color of the overworld sky.")
        .defaultValue(Color.fromHsv(205, 0.42, 0.95).toSetting())
        .visible(customSkyColor::get)
        .build()
    );

    public final Setting<SettingColor> netherSkyColor = sgSky.add(new ColorSetting.Builder()
        .name("nether-sky-color")
        .description("The color of the nether sky.")
        .defaultValue(Color.fromHsv(16, 0.58, 0.38).toSetting())
        .visible(customSkyColor::get)
        .build()
    );

    public final Setting<SettingColor> endSkyColor = sgSky.add(new ColorSetting.Builder()
        .name("end-sky-color")
        .description("The color of the end sky.")
        .defaultValue(Color.fromHsv(265, 0.38, 0.42).toSetting())
        .visible(customSkyColor::get)
        .build()
    );

    public final Setting<Boolean> customCloudColor = sgSky.add(new BoolSetting.Builder()
        .name("custom-cloud-color")
        .description("Whether the clouds color should be changed.")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> cloudColor = sgSky.add(new ColorSetting.Builder()
        .name("cloud-color")
        .description("The color of the clouds.")
        .defaultValue(Color.fromHsv(205, 0.035, 0.96).toSetting())
        .visible(customCloudColor::get)
        .build()
    );

    public final Setting<Boolean> changeLightningColor = sgSky.add(new BoolSetting.Builder()
        .name("custom-lightning-color")
        .description("Whether the lightning color should be changed.")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> lightningColor = sgSky.add(new ColorSetting.Builder()
        .name("lightning-color")
        .description("The color of the lightning.")
        .defaultValue(Color.fromHsv(48, 0.12, 1).toSetting())
        .visible(changeLightningColor::get)
        .build()
    );

    // World
    public final Setting<Boolean> customGrassColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-grass-color")
        .description("Whether the grass color should be changed.")
        .defaultValue(false)
        .onChanged(_ -> reload())
        .build()
    );

    public final Setting<SettingColor> grassColor = sgWorld.add(new ColorSetting.Builder()
        .name("grass-color")
        .description("The color of the grass.")
        .defaultValue(Color.fromHsv(105, 0.48, 0.68).toSetting())
        .visible(customGrassColor::get)
        .onChanged(_ -> reload())
        .build()
    );

    public final Setting<Boolean> customFoliageColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-foliage-color")
        .description("Whether the foliage color should be changed.")
        .defaultValue(false)
        .onChanged(_ -> reload())
        .build()
    );

    public final Setting<SettingColor> foliageColor = sgWorld.add(new ColorSetting.Builder()
        .name("foliage-color")
        .description("The color of the foliage.")
        .defaultValue(Color.fromHsv(140, 0.45, 0.54).toSetting())
        .visible(customFoliageColor::get)
        .onChanged(_ -> reload())
        .build()
    );

    public final Setting<Boolean> customWaterColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-water-color")
        .description("Whether the water color should be changed.")
        .defaultValue(false)
        .onChanged(_ -> reload())
        .build()
    );

    public final Setting<SettingColor> waterColor = sgWorld.add(new ColorSetting.Builder()
        .name("water-color")
        .description("The color of the water.")
        .defaultValue(Color.fromHsv(200, 0.52, 0.78).toSetting())
        .visible(customWaterColor::get)
        .onChanged(_ -> reload())
        .build()
    );

    public final Setting<Boolean> customLavaColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-lava-color")
        .description("Whether the lava color should be changed.")
        .defaultValue(false)
        .onChanged(_ -> reload())
        .build()
    );

    public final Setting<SettingColor> lavaColor = sgWorld.add(new ColorSetting.Builder()
        .name("lava-color")
        .description("The color of the lava.")
        .defaultValue(Color.fromHsv(28, 0.74, 0.98).toSetting())
        .visible(customLavaColor::get)
        .onChanged(_ -> reload())
        .build()
    );

    public final Setting<Boolean> customFogColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-fog-color")
        .description("Whether the fog color should be changed.")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> fogColor = sgWorld.add(new ColorSetting.Builder()
        .name("fog-color")
        .description("The color of the fog.")
        .defaultValue(Color.fromHsv(205, 0.15, 0.78).toSetting())
        .visible(customFogColor::get)
        .build()
    );

    public Ambience() {
        super(Categories.World, "ambience", "Change the color of various pieces of the environment.");
    }

    @Override
    public void onActivate() {
        reload();
    }

    @Override
    public void onDeactivate() {
        refreshTerrain();
    }

    private void reload() {
        if (isActive()) refreshTerrain();
    }

    private void refreshTerrain() {
        if (mc.level != null && mc.levelExtractor != null) mc.levelExtractor.allChanged();
    }

    public SettingColor skyColor() {
        switch (PlayerUtils.getDimension()) {
            case Overworld -> {
                return overworldSkyColor.get();
            }
            case Nether -> {
                return netherSkyColor.get();
            }
            case End -> {
                return endSkyColor.get();
            }
        }

        return null;
    }
}
