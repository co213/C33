package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class SettingsRegression {
    public static void run() {
        AtomicInteger changes = new AtomicInteger();
        var vector = new Vector3dSetting.Builder().range(-5, 5).defaultValue(1, 2, 3)
            .onChanged(_ -> changes.incrementAndGet()).build();
        check(!vector.set(9, 2, 3), "Out-of-range vector is rejected");
        check(vector.get().equals(new Vector3d(1, 2, 3)) && changes.get() == 0, "Rejected vector preserves old value and callbacks");
        check(!vector.set(Double.NaN, 0, 0), "NaN rejected");
        check(!new Vector3dSetting.Builder().build().set(Double.POSITIVE_INFINITY, 0, 0), "Unbounded settings reject infinity");
        check(vector.parse(" 2  3\t4 "), "Whitespace-separated vector accepted");
        check(!vector.parse("1 2 3 4"), "Extra coordinates rejected");
        Vector3d source = new Vector3d(1, 1, 1);
        vector.set(source);
        source.x = 100;
        check(vector.get().x == 1, "Caller mutation cannot bypass validation");
        CompoundTag positionTag = new CompoundTag();
        var position = new BlockPosSetting.Builder().defaultValue(new BlockPos(4, 5, 6)).build();
        for (int length : new int[] {0, 1, 2, 4}) {
            positionTag.putIntArray("value", new int[length]);
            position.fromTag(positionTag);
            check(position.get().equals(new BlockPos(4, 5, 6)), "Malformed position array preserves previous setting");
        }
        check(position.parse("1, 2, 3"), "Spaced block coordinates accepted");
        check(!position.parse("1,2,3,"), "Trailing coordinate rejected");
        var addresses = new StringListSetting.Builder().filter(Utils::ipFilter).build();
        check(addresses.parse("localhost:25565,example.net"), "Address filters allow a single colon");
        addresses.get().add("another.net");
        check(!addresses.set(List.of("localhost:1:2")), "Filters reject repeated separators");
        var serialized = addresses.toTag();
        addresses.set(List.of("temporary.net"));
        addresses.fromTag(serialized);
        check(addresses.get().equals(List.of("localhost:25565", "example.net", "another.net")), "Lists stay mutable and reloadable after command parsing");
        ArrayList<String> original = new ArrayList<>(List.of("example.net"));
        addresses.set(original);
        original.clear();
        check(addresses.get().size() == 1, "Caller lists cannot mutate stored settings");
        var address = new StringSetting.Builder().filter(Utils::ipFilter).build();
        check(address.set("localhost:25565") && !address.set("localhost:1:2"), "Scalar filter follows incremental typing semantics");
        ListTag malformed = new ListTag();
        malformed.add(StringTag.valueOf("invalid"));
        check(NbtUtils.listFromTag(malformed, tag -> (CompoundTag) tag).isEmpty(), "Serialized object lists skip noncompound entries");
        System.out.println("Settings boundary regressions passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
