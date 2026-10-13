/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.accounts;

import com.gaspoweredcake.client33.systems.System;
import com.gaspoweredcake.client33.systems.Systems;
import com.gaspoweredcake.client33.systems.accounts.types.CrackedAccount;
import com.gaspoweredcake.client33.systems.accounts.types.MicrosoftAccount;
import com.gaspoweredcake.client33.systems.accounts.types.SessionAccount;
import com.gaspoweredcake.client33.systems.accounts.types.TheAlteningAccount;
import com.gaspoweredcake.client33.utils.misc.NbtException;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Accounts extends System<Accounts> implements Iterable<Account<?>> {
    private List<Account<?>> accounts = new ArrayList<>();

    public Accounts() {
        super("accounts");
    }

    public static Accounts get() {
        return Systems.get(Accounts.class);
    }

    public void add(Account<?> account) {
        accounts.add(account);
        save();
    }

    public boolean exists(Account<?> account) {
        return accounts.contains(account);
    }

    public void remove(Account<?> account) {
        if (accounts.remove(account)) {
            save();
        }
    }

    public int size() {
        return accounts.size();
    }

    @Override
    public @NonNull Iterator<Account<?>> iterator() {
        return accounts.iterator();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        tag.put("accounts", NbtUtils.listToTag(accounts));

        return tag;
    }

    @Override
    public Accounts fromTag(CompoundTag tag) {
        accounts = NbtUtils.listFromTag(tag.getListOrEmpty("accounts"), tag1 -> {
            CompoundTag t = (CompoundTag) tag1;
            if (!t.contains("type")) return null;

            try {
                AccountType type = AccountType.valueOf(t.getStringOr("type", ""));
                return switch (type) {
                    case Cracked -> new CrackedAccount(null).fromTag(t);
                    case Microsoft -> new MicrosoftAccount(null).fromTag(t);
                    case TheAltening -> new TheAlteningAccount(null).fromTag(t);
                    case Session -> new SessionAccount(null).fromTag(t);
                };
            } catch (NbtException | IllegalArgumentException _) {
                return null;
            }
        });

        return this;
    }
}
