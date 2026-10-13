/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.utils.misc;

import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import com.gaspoweredcake.client33.Client33;
import net.minecraft.nbt.*;

import java.util.*;
import org.jspecify.annotations.Nullable;

import static com.gaspoweredcake.client33.Client33.mc;

public class NbtUtils {
    private static final int MAX_CLIPBOARD_CHARS = 8 * 1024 * 1024;
    private static final long MAX_CLIPBOARD_NBT_BYTES = 16L * 1024 * 1024;
    public static <T extends ISerializable<?>> ListTag listToTag(Iterable<T> list) {
        ListTag tag = new ListTag();
        for (T item : list) tag.add(item.toTag());
        return tag;
    }

    public static <T> List<T> listFromTag(ListTag tag, ToValue<T> toItem) {
        List<T> list = new ArrayList<>(tag.size());
        for (Tag itemTag : tag) {
            if (!(itemTag instanceof CompoundTag)) continue;
            T value = toItem.toValue(itemTag);
            if (value != null) list.add(value);
        }
        return list;
    }

    public static <K, V extends ISerializable<?>> CompoundTag mapToTag(Map<K, V> map) {
        CompoundTag tag = new CompoundTag();
        for (var entry : map.entrySet()) tag.put(entry.getKey().toString(), entry.getValue().toTag());
        return tag;
    }

    public static <K, V> Map<K, V> mapFromTag(CompoundTag tag, ToKey<K> toKey, ToValue<V> toValue) {
        Map<K, V> map = HashMap.newHashMap(tag.size());
        for (String key : tag.keySet()) map.put(toKey.toKey(key), toValue.toValue(tag.get(key)));
        return map;
    }

    public static boolean toClipboard(ISerializable<?> serializable) {
        return toClipboard(serializable.toTag());
    }

    public static boolean toClipboard(CompoundTag tag) {
        String preClipboard = mc.keyboardHandler.getClipboard();
        try {
            var byteArrayOutputStream = new FastByteArrayOutputStream();
            NbtIo.writeCompressed(tag, byteArrayOutputStream);
            mc.keyboardHandler.setClipboard(Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray()));
            return true;
        } catch (Exception e) {
            Client33.LOG.error("Error copying NBT to clipboard!", e);
            mc.keyboardHandler.setClipboard(preClipboard);
            return false;
        }
    }

    public static boolean fromClipboard(ISerializable<?> serializable) {
        CompoundTag tag = fromClipboard();
        if (tag == null) return false;

        CompoundTag sourceTag = serializable.toTag();
        for (String key : sourceTag.keySet()) {
            if (!tag.contains(key)) return false;
        }

        serializable.fromTag(tag);
        return true;
    }

    public static @Nullable CompoundTag fromClipboard() {
        try {
            String encoded = mc.keyboardHandler.getClipboard().trim();
            if (encoded.length() > MAX_CLIPBOARD_CHARS) throw new IllegalArgumentException("Clipboard NBT exceeds size limit");
            byte[] data = Base64.getDecoder().decode(encoded);
            var bis = new FastByteArrayInputStream(data);
            return NbtIo.readCompressed(bis, NbtAccounter.create(MAX_CLIPBOARD_NBT_BYTES));
        } catch (Exception e) {
            Client33.LOG.error("Invalid NBT data pasted!", e);
            return null;
        }
    }

    public interface ToKey<T> {
        T toKey(String string);
    }

    public interface ToValue<T> {
        T toValue(Tag tag);
    }
}
