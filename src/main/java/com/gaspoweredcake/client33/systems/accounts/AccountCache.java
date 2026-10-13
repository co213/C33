/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.accounts;

import com.mojang.util.UndashedUuid;
import com.gaspoweredcake.client33.utils.misc.ISerializable;
import com.gaspoweredcake.client33.utils.misc.NbtException;
import com.gaspoweredcake.client33.utils.network.Client33Executor;
import com.gaspoweredcake.client33.utils.render.PlayerHeadTexture;
import com.gaspoweredcake.client33.utils.render.PlayerHeadUtils;
import net.minecraft.nbt.CompoundTag;

import static com.gaspoweredcake.client33.Client33.mc;

public class AccountCache implements ISerializable<AccountCache> {
    public String username = "";
    public String uuid = "";
    private PlayerHeadTexture headTexture;
    private volatile boolean loadingHead;

    public PlayerHeadTexture getHeadTexture() {
        return headTexture != null ? headTexture : PlayerHeadUtils.STEVE_HEAD;
    }

    public void loadHead() {
        loadHead(null);
    }

    public void loadHead(Runnable callback) {
        if (headTexture != null || uuid == null || uuid.isBlank()) {
            if (callback != null) mc.execute(callback);
            return;
        }

        if (loadingHead) return;

        loadingHead = true;

        String requestedUuid = uuid;
        Client33Executor.execute(() -> {
            byte[] downloaded = null;
            try {
                downloaded = PlayerHeadUtils.fetchHead(UndashedUuid.fromStringLenient(requestedUuid));
            } catch (RuntimeException failure) {
                com.gaspoweredcake.client33.Client33.LOG.warn("Could not load account head.", failure);
            }
            byte[] head = downloaded;
            mc.execute(() -> {
                try {
                    if (head != null && requestedUuid.equals(uuid)) headTexture = new PlayerHeadTexture(head, true);
                } finally {
                    loadingHead = false;
                    if (callback != null) callback.run();
                }
            });
        });
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        tag.putString("username", username);
        tag.putString("uuid", uuid);

        return tag;
    }

    @Override
    public AccountCache fromTag(CompoundTag tag) {
        if (tag.getString("username").isEmpty() || tag.getString("uuid").isEmpty()) throw new NbtException();

        username = tag.getString("username").get();
        uuid = tag.getString("uuid").get();
        loadHead();

        return this;
    }
}
