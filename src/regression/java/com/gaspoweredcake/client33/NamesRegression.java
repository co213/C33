package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.events.game.ResourcePacksReloadedEvent;
import com.gaspoweredcake.client33.utils.misc.Names;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Actual name lookups with Minecraft registries and synthetic languages; no game window. */
public final class NamesRegression {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        Language original = Language.getInstance();
        TestLanguage first = new TestLanguage(Map.of("block.minecraft.stone", "§aStone A", "enchantment.minecraft.sharpness", "§bSharp A"));
        TestLanguage second = new TestLanguage(Map.of("block.minecraft.stone", "Pierre B", "enchantment.minecraft.sharpness", "Tranchant B"));
        try {
            Language.inject(first);
            check(Names.get(Blocks.STONE).equals("Stone A"), "Translated built-in names strip formatting");
            var sharpness = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("minecraft:sharpness"));
            var unbound = Holder.Reference.createStandAlone(new HolderOwner<Enchantment>() {}, sharpness);
            check(Names.get(unbound).equals("Sharp A"), "Unbound holders fall back without calling value");
            check(Names.get(sharpness).equals("Sharp A"), "Disconnected enchantment keys use translations");
            Language.inject(second);
            check(Names.get(Blocks.STONE).equals("Pierre B"), "Language replacement invalidates built-in caches");
            check(Names.get(sharpness).equals("Tranchant B") && Names.get(unbound).equals("Tranchant B"), "Enchantment keys and holders do not retain previous language names");
            second.values.put("block.minecraft.stone", "Stone after reload");
            var reload = Names.class.getDeclaredMethod("onResourcePacksReloaded", ResourcePacksReloadedEvent.class);
            reload.setAccessible(true); reload.invoke(null, ResourcePacksReloadedEvent.get());
            check(Names.get(Blocks.STONE).equals("Stone after reload"), "Resource reload invalidates cache even when language identity is unchanged");
            var missing = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("example:missing"));
            check(Names.get(missing).equals("example:missing"), "Missing translations preserve namespaced identifiers");
            // Content equality alone cannot detect a translation whose text happens to equal the key.
            second.values.put("enchantment.example.echo", new String("enchantment.example.echo"));
            check(Names.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("example:echo"))).equals("enchantment.example.echo"), "Translation existence uses the language catalog");
            ItemStack stack = new ItemStack(Holder.direct(Items.STONE, DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).set(DataComponents.ITEM_NAME, Component.literal("Stone")).build()));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("§6First building"));
            check(Names.get(stack).equals("First building"), "Custom item stack names are plain text");
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Second building"));
            check(Names.get(stack).equals("Second building"), "Mutable stack names are never cached by item identity");
            check(Names.getSoundName(Identifier.parse("example:ambient.forest_birds")).equals("Ambient forest birds"), "Missing sound subtitles have readable fallback names");
        } finally { Language.inject(original); }
        System.out.println("Names language/reload invalidation, enchantment fallback and mutable stack regressions passed.");
    }

    private static final class TestLanguage extends Language {
        final Map<String, String> values = new ConcurrentHashMap<>();
        TestLanguage(Map<String, String> values) { this.values.putAll(values); }
        @Override public String getOrDefault(String key, String fallback) { return values.getOrDefault(key, fallback); }
        @Override public boolean has(String key) { return values.containsKey(key); }
        @Override public boolean isDefaultRightToLeft() { return false; }
        @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return Language.DEFAULT_INSTANCE.getVisualOrder(text); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
