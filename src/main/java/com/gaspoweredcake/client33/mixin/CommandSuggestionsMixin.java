/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.gaspoweredcake.client33.commands.Commands;
import com.gaspoweredcake.client33.systems.config.Config;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

import static com.gaspoweredcake.client33.Client33.mc;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    private @Nullable ParseResults<ClientSuggestionProvider> currentParse;

    @Shadow
    @Final
    private EditBox input;

    @Shadow
    private CommandSuggestions.SuggestionsList suggestions;

    @Shadow
    private boolean keepSuggestions;

    @Shadow
    private @Nullable CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    protected abstract void updateUsageInfo(ParseResults<ClientSuggestionProvider> currentParse, Suggestions suggestions);

    @Inject(method = "updateCommandInfo",
        at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;canRead()Z", remap = false),
        cancellable = true
    )
    public void onRefresh(CallbackInfo ci, @Local(name = "reader") StringReader reader) {
        String prefix = Config.get().prefix.get();
        int length = prefix.length();

        if (mc.getConnection() == null) return;

        if (reader.canRead(length) && reader.getString().startsWith(prefix, reader.getCursor())) {
            reader.setCursor(reader.getCursor() + length);

            if (this.currentParse == null) {
                this.currentParse = Commands.DISPATCHER.parse(reader, mc.getConnection().getSuggestionsProvider());
            }

            int cursor = input.getCursorPosition();
            if (cursor >= length && (this.suggestions == null || !this.keepSuggestions)) {
                var parse = this.currentParse;
                String text = input.getValue();
                var future = Commands.DISPATCHER.getCompletionSuggestions(parse, cursor);
                this.pendingSuggestions = future;
                future.whenComplete((result, failure) -> mc.execute(() -> {
                    if (failure == null && this.pendingSuggestions == future && this.currentParse == parse
                        && input.getValue().equals(text) && input.getCursorPosition() == cursor) {
                        this.updateUsageInfo(parse, result);
                    }
                }));
            }

            ci.cancel();
        }
    }
}
