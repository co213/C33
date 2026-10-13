/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.profiles;

import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.game.GameJoinedEvent;
import com.gaspoweredcake.client33.systems.System;
import com.gaspoweredcake.client33.systems.Systems;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Profiles extends System<Profiles> implements Iterable<Profile> {
    public static final File FOLDER = new File(Client33.FOLDER, "profiles");

    private List<Profile> profiles = new ArrayList<>();

    public Profiles() {
        super("profiles");
    }

    public static Profiles get() {
        return Systems.get(Profiles.class);
    }

    public void add(Profile profile) {
        if (!ProfileFiles.validName(profile.name.get()) || get(profile.name.get()) != null) {
            throw new IllegalArgumentException("Profile name is invalid or already in use.");
        }
        try {
            if (ProfileFiles.exists(FOLDER.toPath(), profile.name.get())) {
                throw new IllegalArgumentException("A profile folder with this name already exists. Use Refresh to recover it.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not inspect profile folders.", e);
        }
        profiles.add(profile);
        profile.save();
        save();
    }

    public void remove(Profile profile) {
        if (profiles.remove(profile)) profile.delete();
        save();
    }

    public Profile get(String name) {
        for (Profile profile : this) {
            if (profile.name.get().equalsIgnoreCase(name)) {
                return profile;
            }
        }

        return null;
    }

    public List<Profile> getAll() {
        return List.copyOf(profiles);
    }

    @Override
    public File getFile() {
        return new File(FOLDER, "profiles.nbt");
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        for (Profile profile : this) {
            if (profile.loadOnJoin.get().contains(Utils.getWorldName())) {
                profile.load();
            }
        }
    }

    public boolean isEmpty() {
        return profiles.isEmpty();
    }

    @Override
    public @NonNull Iterator<Profile> iterator() {
        return getAll().iterator();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("profiles", NbtUtils.listToTag(profiles));
        return tag;
    }

    @Override
    public Profiles fromTag(CompoundTag tag) {
        List<Profile> loaded = NbtUtils.listFromTag(tag.getListOrEmpty("profiles"), Profile::new);
        profiles = new ArrayList<>();
        for (Profile profile : loaded) {
            if (!ProfileFiles.validName(profile.name.get()) || get(profile.name.get()) != null) continue;
            try {
                profile.getFile();
                profiles.add(profile);
            } catch (IllegalArgumentException e) {
                Client33.LOG.warn("Skipping profile with an invalid folder: {}", profile.name.get());
            }
        }
        refresh();
        return this;
    }

    @Override
    public synchronized void load(File folder) {
        super.load(folder);
        // Also recover copied folders when the index is missing or unreadable.
        refresh();
    }

    /** Discover saved data without calling Profile.save(), which captures live settings. */
    public int refresh() {
        int added = 0;
        try {
            for (Path folder : ProfileFiles.discover(FOLDER.toPath())) {
                if (get(folder.getFileName().toString()) != null) continue;
                Profile profile = new Profile();
                profile.name.set(folder.getFileName().toString());
                List<Path> files;
                try {
                    files = ProfileFiles.dataFiles(folder);
                } catch (IOException e) {
                    Client33.LOG.warn("Could not inspect profile folder '{}'.", folder, e);
                    continue;
                }
                if (files.isEmpty()) continue;
                for (Path file : files) {
                    switch (file.getFileName().toString()) {
                        case "hud.nbt" -> profile.hud.set(true);
                        case "macros.nbt" -> profile.macros.set(true);
                        case "modules.nbt" -> profile.modules.set(true);
                        default -> profile.waypoints.set(true);
                    }
                }
                profiles.add(profile);
                added++;
            }
        } catch (IOException e) {
            Client33.LOG.error("Could not discover profile folders.", e);
        }
        return added;
    }

    public String availableName(String requested) throws IOException {
        if (!ProfileFiles.validName(requested)) throw new IOException("Invalid profile name.");
        String candidate = requested;
        for (int suffix = 2; get(candidate) != null || ProfileFiles.exists(FOLDER.toPath(), candidate); suffix++) {
            candidate = requested + " " + suffix;
        }
        return candidate;
    }

    /** Apply an edited copy only after a successful filesystem rename. */
    public void update(Profile original, Profile edited) throws IOException {
        String name = edited.name.get();
        Profile conflict = get(name);
        if (conflict != null && conflict != original) throw new IOException("Profile name is already in use.");
        ProfileFiles.rename(FOLDER.toPath(), original.name.get(), name);
        original.fromTag(edited.toTag());
        save();
    }
}
