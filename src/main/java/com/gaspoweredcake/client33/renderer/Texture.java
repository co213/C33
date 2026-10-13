/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.renderer;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jspecify.annotations.NonNull;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Texture extends AbstractTexture {
    public Texture(int width, int height, GpuFormat format, FilterMode min, FilterMode mag) {
        texture = RenderSystem.getDevice().createTexture("", 15, format, width, height, 1, 1);
        sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, min, mag, false);

        textureView = RenderSystem.getDevice().createTextureView(texture);
    }

    public int getWidth() {
        return getTexture().getWidth(0);
    }

    public int getHeight() {
        return getTexture().getHeight(0);
    }

    public void upload(byte[] bytes) {
        upload(BufferUtils.createByteBuffer(bytes.length).put(bytes));
    }

    public void upload(ByteBuffer buffer) {
        ByteBuffer pixels = buffer.duplicate().rewind();
        long expected = (long) getWidth() * getHeight() * (texture.getFormat() == GpuFormat.R8_UNORM ? 1 : 4);
        if (!pixels.isDirect() || pixels.remaining() != expected) {
            throw new IllegalArgumentException("Texture upload requires a direct buffer of exactly " + expected + " bytes.");
        }
        try (var image = getImage()) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), image.getPointer(), expected);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
        }
    }

    private @NonNull NativeImage getImage() {
        NativeImage.Format imageFormat = switch (texture.getFormat()) {
            case RGBA8_UNORM -> NativeImage.Format.RGBA;
            case R8_UNORM -> NativeImage.Format.LUMINANCE;
            default -> throw new IllegalArgumentException("Unsupported texture format: " + texture.getFormat());
        };

        // Workaround for writeToTexture(IntBuffer) overload comparing width * height to the size of the int buffer.
        // And since we are working with pixels which are only one byte in size, the sizes don't match
        return new NativeImage(imageFormat, getWidth(), getHeight(), false);
    }

    public static Texture readResource(String path, boolean flipY, FilterMode filter) {
        try (var in = Texture.class.getResourceAsStream(path)) {
            if (in == null) return null;

            return read(in, flipY, filter);
        } catch (IOException e) {
            return null;
        }
    }

    /** The caller owns the stream; decoded native buffers are always released here. */
    public static Texture read(java.io.InputStream in, boolean flipY, FilterMode filter) throws IOException {
        ByteBuffer data = TextureUtil.readResource(in).rewind();
        ByteBuffer image = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load_thread(flipY ? 1 : 0);
            image = STBImage.stbi_load_from_memory(data, width, height, comp, 4);
            if (image == null) throw new IOException("Could not decode texture: " + STBImage.stbi_failure_reason());
            Texture result = new Texture(width.get(0), height.get(0), GpuFormat.RGBA8_UNORM, filter, filter);
            try {
                result.upload(image);
                return result;
            } catch (RuntimeException | Error failure) {
                result.close();
                throw failure;
            }
        } finally {
            STBImage.stbi_image_free(image);
            STBImage.stbi_set_flip_vertically_on_load_thread(0);
            MemoryUtil.memFree(data);
        }
    }
}
