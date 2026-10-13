/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.utils.render.postprocess;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.textures.FilterMode;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.game.ResourcePacksReloadedEvent;
import com.gaspoweredcake.client33.renderer.MeshRenderer;
import com.gaspoweredcake.client33.renderer.Client33RenderPipelines;
import com.gaspoweredcake.client33.renderer.Texture;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.render.Chams;
import com.gaspoweredcake.client33.utils.PostInit;
import com.gaspoweredcake.client33.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import static com.gaspoweredcake.client33.Client33.mc;

public class ChamsShader extends EntityShader {
    private static final String[] FILE_FORMATS = {"png", "jpg"};

    private static Texture IMAGE_TEX;
    private static Chams chams;

    public ChamsShader() {
        super(Client33RenderPipelines.POST_IMAGE);
        Client33.EVENT_BUS.subscribe(ChamsShader.class);
    }

    @PostInit
    public static void load() {
        try {
            for (String fileFormat : FILE_FORMATS) {
                Optional<Resource> resource = mc.getResourceManager().getResource(Client33.identifier("textures/chams." + fileFormat));
                if (resource.isEmpty()) continue;
                try (var in = resource.get().open()) {
                    Texture replacement = Texture.read(in, true, FilterMode.NEAREST);
                    Texture previous = IMAGE_TEX;
                    IMAGE_TEX = replacement;
                    if (previous != null) previous.close();
                }
                return;
            }
        } catch (IOException e) {
            Client33.LOG.error("Error loading the chams shader", e);
        }
    }

    @EventHandler
    private static void onResourcePacksReloaded(ResourcePacksReloadedEvent event) {
        load();
    }

    @Override
    protected void setupPass(MeshRenderer renderer) {
        Color color = chams.shaderColor.get();

        renderer.uniform("ImageData", UNIFORM_STORAGE.writeUniform(new UniformData(
            color.r / 255f, color.g / 255f, color.b / 255f, color.a / 255f
        )));

        if (chams.isShader() && chams.shader.get() == Chams.Shader.Image && IMAGE_TEX != null) {
            renderer.sampler("u_TextureI", IMAGE_TEX.getTextureView(), IMAGE_TEX.getSampler());
        }
    }

    @Override
    protected boolean shouldDraw() {
        if (chams == null) chams = Modules.get().get(Chams.class);
        return chams.isShader();
    }

    @Override
    public boolean shouldDraw(Entity entity) {
        if (!shouldDraw()) return false;
        return chams.entities.get().contains(entity.getType()) && (entity != mc.player || !chams.ignoreSelfDepth.get());
    }

    // Uniforms

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec4()
        .get();

    private static final DynamicUniformStorage<UniformData> UNIFORM_STORAGE = new DynamicUniformStorage<>("Client33 - Image UBO", UNIFORM_SIZE, 16);

    public static void flipFrame() {
        UNIFORM_STORAGE.endFrame();
    }

    private record UniformData(float r, float g, float b, float a) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(@NonNull ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec4(r, g, b, a);
        }
    }
}
