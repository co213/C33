/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.profiles;

import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.hud.Hud;
import com.gaspoweredcake.client33.systems.macros.Macros;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.waypoints.Waypoints;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.misc.ISerializable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class Profile implements ISerializable<Profile> {
    public final Settings settings = new Settings();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSave = settings.createGroup("Save");

    public Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("The name of the profile.")
        .filter(Utils::fileNameFilter)
        .build()
    );

    public Setting<List<String>> loadOnJoin = sgGeneral.add(new StringListSetting.Builder()
        .name("load-on-join")
        .description("Which servers to set this profile as active when joining.")
        .filter(Utils::ipFilter)
        .build()
    );

    public Setting<Boolean> hud = sgSave.add(new BoolSetting.Builder()
        .name("hud")
        .description("Whether the profile should save hud.")
        .defaultValue(false)
        .build()
    );

    public Setting<Boolean> macros = sgSave.add(new BoolSetting.Builder()
        .name("macros")
        .description("Whether the profile should save macros.")
        .defaultValue(false)
        .build()
    );

    public Setting<Boolean> modules = sgSave.add(new BoolSetting.Builder()
        .name("modules")
        .description("Whether the profile should save modules.")
        .defaultValue(false)
        .build()
    );

    public Setting<Boolean> waypoints = sgSave.add(new BoolSetting.Builder()
        .name("waypoints")
        .description("Whether the profile should save waypoints.")
        .defaultValue(false)
        .build()
    );

    public Profile() {
    }

    public Profile(Tag tag) {
        fromTag((CompoundTag) tag);
    }

    public void load() {
        File folder = getFile();

        if (hud.get()) Hud.get().load(folder);
        if (macros.get()) Macros.get().load(folder);
        if (modules.get()) Modules.get().load(folder);
        if (waypoints.get()) Waypoints.get().load(folder);
    }

    public void save() {
        File folder = getFile();

        if (hud.get()) Hud.get().save(folder);
        if (macros.get()) Macros.get().save(folder);
        if (modules.get()) Modules.get().save(folder);
        if (waypoints.get()) Waypoints.get().save(folder);
    }

    public void delete() {
        try {
            FileUtils.deleteDirectory(getFile());
        } catch (IOException e) {
            Client33.LOG.error("Error deleting profile {}", name.get(), e);
        }
    }

    public File getFile() {
        return ProfileFiles.resolve(Profiles.FOLDER.toPath(), name.get()).toFile();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        tag.put("settings", settings.toTag());

        return tag;
    }

    @Override
    public Profile fromTag(CompoundTag tag) {
        if (tag.contains("settings")) {
            settings.fromTag(tag.getCompoundOrEmpty("settings"));
        }

        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name.get());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Profile profile = (Profile) o;
        return Objects.equals(profile.name.get(), this.name.get());
    }
}
