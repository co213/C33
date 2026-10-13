/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.utils.misc;

import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.game.ResourcePacksReloadedEvent;
import com.gaspoweredcake.client33.utils.PreInit;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Plain display names. Cache only stable built-in objects, never server registry holders or stacks. */
public final class Names {
    private static volatile Caches caches = new Caches(Language.getInstance());

    private Names() {}

    private static final class Caches {
        final Language language;
        final Map<MobEffect, String> effects = new ConcurrentHashMap<>();
        final Map<Item, String> items = new ConcurrentHashMap<>();
        final Map<Block, String> blocks = new ConcurrentHashMap<>();
        final Map<EntityType<?>, String> entities = new ConcurrentHashMap<>();
        final Map<ParticleType<?>, String> particles = new ConcurrentHashMap<>();
        // Sound subtitles can change independently of the language on resource reload.
        final Map<Identifier, String> sounds = new ConcurrentHashMap<>();

        Caches(Language language) { this.language = language; }
    }

    private static Caches currentCaches() {
        Caches current = caches;
        Language language = Language.getInstance();
        if (current.language == language) return current;
        synchronized (Names.class) {
            if (caches.language != language) caches = new Caches(language);
            return caches;
        }
    }

    @PreInit
    public static void init() {
        Client33.EVENT_BUS.subscribe(Names.class);
    }

    @EventHandler
    private static synchronized void onResourcePacksReloaded(ResourcePacksReloadedEvent event) {
        // Publish a new generation; an in-flight lookup cannot refill the new cache with old data.
        caches = new Caches(Language.getInstance());
    }

    public static String get(MobEffect effect) {
        return currentCaches().effects.computeIfAbsent(effect, value -> translated(value.getDescriptionId()));
    }

    public static String get(Item item) {
        return currentCaches().items.computeIfAbsent(item, value -> translated(value.getDescriptionId()));
    }

    public static String get(Block block) {
        return currentCaches().blocks.computeIfAbsent(block, value -> translated(value.getDescriptionId()));
    }

    public static String get(ResourceKey<Enchantment> enchantment) {
        // Resolve against the current connection every time: datapacks can redefine the same key.
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getConnection() != null) {
            var entry = client.getConnection().registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.get(enchantment.identifier()));
            if (entry.isPresent()) return get(entry.get());
        }
        return enchantmentFallback(enchantment);
    }

    private static String enchantmentFallback(ResourceKey<Enchantment> enchantment) {
        String key = "enchantment." + enchantment.identifier().toLanguageKey();
        return Language.getInstance().has(key) ? translated(key) : enchantment.identifier().toString();
    }

    public static String get(Holder<Enchantment> enchantment) {
        if (!enchantment.isBound()) return enchantment.unwrapKey().map(Names::enchantmentFallback).orElse("Unknown enchantment");
        return plain(enchantment.value().description().getString());
    }

    public static String get(EntityType<?> entityType) {
        return currentCaches().entities.computeIfAbsent(entityType, value -> translated(value.getDescriptionId()));
    }

    public static String get(ParticleType<?> type) {
        return currentCaches().particles.computeIfAbsent(type, value -> {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(value);
            return id == null ? "Unknown particle" : readablePath(id);
        });
    }

    public static String getSoundName(Identifier id) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return readablePath(id); // Do not cache a pre-startup fallback.
        Caches current = currentCaches();
        WeighedSoundEvents sounds = client.getSoundManager().getSoundEvent(id);
        if (sounds == null || sounds.getSubtitle() == null) return readablePath(id);
        return current.sounds.computeIfAbsent(id, ignored -> plain(sounds.getSubtitle().getString()));
    }

    public static String get(ItemStack stack) {
        // Custom names and data components are mutable, so resolve the stack on each call.
        return plain(stack.getHoverName().getString());
    }

    private static String translated(String key) { return plain(I18n.get(key)); }
    private static String plain(String value) { return StringUtil.stripColor(value); }
    private static String readablePath(Identifier id) {
        return StringUtils.capitalize(id.getPath().replace('_', ' ').replace('.', ' ').replace('/', ' '));
    }
}
