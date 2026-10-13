package com.gaspoweredcake.client33.utils.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

/** Prunes oldest packet logs while preserving the currently open file. */
public final class LogRetention {
    private LogRetention() {}

    public static void prune(Path directory, Path activeFile, long maxBytes) throws IOException {
        if (maxBytes < 0) throw new IllegalArgumentException("Log budget must be nonnegative");
        var logs = new ArrayList<Entry>();
        try (var entries = Files.list(directory)) {
            for (Path path : entries.toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith("packets-") || !name.endsWith(".log") || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    logs.add(new Entry(path, Files.size(path), Files.getLastModifiedTime(path).toMillis()));
                } catch (NoSuchFileException ignored) { }
            }
        }
        logs.sort(Comparator.comparingLong(Entry::modified).thenComparing(entry -> entry.path.toString()));
        long total = logs.stream().mapToLong(Entry::size).sum();
        Path active = activeFile == null ? null : activeFile.toAbsolutePath().normalize();
        for (Entry entry : logs) {
            if (total <= maxBytes) break;
            if (entry.path.toAbsolutePath().normalize().equals(active)) continue;
            Files.deleteIfExists(entry.path);
            total -= entry.size;
        }
    }

    private record Entry(Path path, long size, long modified) {}
}
