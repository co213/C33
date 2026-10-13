package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.files.ByteBufferUtils;
import com.gaspoweredcake.client33.utils.files.LogRetention;
import com.gaspoweredcake.client33.utils.misc.UnorderedArrayList;
import com.gaspoweredcake.client33.utils.misc.ValueComparableMap;
import com.gaspoweredcake.client33.utils.network.CheckBatch;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class InfrastructureRegression {
    public static void run() throws Exception {
        var list = new UnorderedArrayList<Integer>();
        list.addAll(List.of(1, 2, 3));
        check(list.remove(0) == 1 && list.equals(List.of(3, 2)), "Unordered removal swaps the last element");
        for (Runnable invalid : List.<Runnable>of(() -> list.get(2), () -> list.set(2, 9), () -> list.remove(2))) {
            try { invalid.run(); throw new AssertionError("Logical bounds ignored"); }
            catch (IndexOutOfBoundsException expected) { }
        }
        Iterator<Integer> old = list.iterator();
        list.removeIf(value -> value == 3);
        try { old.next(); throw new AssertionError("Structural change did not invalidate iterator"); }
        catch (ConcurrentModificationException expected) { }
        list.addAll(List.of(4, 6));
        List<Integer> before = List.copyOf(list);
        try {
            list.removeIf(value -> { if (value == 6) throw new IllegalStateException(); return true; });
            throw new AssertionError("Predicate failure ignored");
        } catch (IllegalStateException expected) { }
        check(list.equals(before), "Failed predicate cannot partially compact data");
        var map = new ValueComparableMap<String, Integer>(Comparator.naturalOrder());
        map.put("a", 3); map.put("b", 1); map.put("c", 2);
        check(new ArrayList<>(map.keySet()).equals(List.of("b", "c", "a")), "Search results sort by value");
        check(map.put("a", 0) == 3, "Replacing an entry returns its previous value");
        map.keySet().remove("b");
        check(!map.containsKey("b"), "View removal updates lookup");
        map.entrySet().iterator().next().setValue(10);
        check(new ArrayList<>(map.keySet()).equals(List.of("c", "a")), "Value changes update subsequent ordering");
        map.clear();
        check(map.isEmpty() && !map.containsKey("a"), "Clear releases all lookup state");
        var path = Files.createTempFile("client33-channel-", ".bin");
        try {
            Files.write(path, new byte[] {1, 2, 3, 4});
            try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
                channel.position(2);
                ByteBuffer remaining = ByteBufferUtils.readFully(channel, size -> ByteBuffer.allocate(size + 8));
                check(remaining.remaining() == 2 && remaining.get() == 3 && remaining.get() == 4, "Read starts at current channel position");
            }
        } finally { Files.delete(path); }
        try (ReadableByteChannel stalled = new ReadableByteChannel() {
            public int read(ByteBuffer dst) { return 0; }
            public boolean isOpen() { return true; }
            public void close() { }
        }) {
            try { ByteBufferUtils.readFully(stalled, ByteBuffer::allocate); throw new AssertionError("Silent truncated read"); }
            catch (IOException expected) { }
        }
        Map<Integer, AtomicInteger> attempts = new ConcurrentHashMap<>();
        List<Integer> checks = java.util.stream.IntStream.range(0, 100).boxed().toList();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var finished = executor.submit(() -> {
                try {
                    CheckBatch.run(checks, 8, 2, item -> attempts.computeIfAbsent(item, _ -> new AtomicInteger()).incrementAndGet() < 3);
                } catch (InterruptedException e) { throw new RuntimeException(e); }
            });
            try { finished.get(5, TimeUnit.SECONDS); }
            finally { finished.cancel(true); executor.shutdownNow(); }
        }
        check(attempts.size() == 100 && attempts.values().stream().allMatch(count -> count.get() == 3), "Concurrent retries complete without empty-queue deadlocks");
        var logs = Files.createTempDirectory("client33-retention-");
        try {
            var oldest = logs.resolve("packets-old.log");
            var newer = logs.resolve("packets-new.log");
            var active = logs.resolve("packets-active.log");
            for (var file : List.of(oldest, newer, active)) Files.writeString(file, "12345678");
            Files.setLastModifiedTime(oldest, java.nio.file.attribute.FileTime.fromMillis(1000));
            Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(2000));
            Files.writeString(logs.resolve("keep.txt"), "Unrelated user data");
            LogRetention.prune(logs, active, 16);
            check(!Files.exists(oldest) && Files.exists(newer) && Files.exists(active), "Retention deletes oldest logs first");
            LogRetention.prune(logs, active, 0);
            check(Files.exists(active) && Files.exists(logs.resolve("keep.txt")), "Retention protects active logs and unrelated files");
        } finally {
            try (var files = Files.list(logs)) { for (var file : files.toList()) Files.delete(file); }
            Files.delete(logs);
        }
        System.out.println("Collection, channel, concurrency and log retention regressions passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
