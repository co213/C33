package com.gaspoweredcake.client33.systems.profiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/** Filesystem operations shared by profile recovery, import and export. */
public final class ProfileFiles {
    private ProfileFiles() {}

    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.equals(name.strip())
            && name.matches("[a-zA-Z0-9_ -]+")
            && !name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");
    }

    public static Path resolve(Path root, String name) {
        if (!validName(name)) throw new IllegalArgumentException("Invalid profile name: " + name);
        Path folder = root.toAbsolutePath().normalize().resolve(name);
        if (Files.isSymbolicLink(folder)) throw new IllegalArgumentException("Profile folders cannot be symbolic links.");
        return folder;
    }

    public static boolean isDataFile(String name) {
        return name.endsWith(".nbt") && !name.startsWith(".") && !name.endsWith(".backup.nbt")
            && !name.contains("/") && !name.contains("\\") && !name.contains(":");
    }

    public static boolean selected(String name, boolean hud, boolean macros, boolean modules, boolean waypoints) {
        if (!isDataFile(name)) return false;
        return switch (name) {
            case "hud.nbt" -> hud;
            case "macros.nbt" -> macros;
            case "modules.nbt" -> modules;
            default -> waypoints;
        };
    }

    public static List<Path> dataFiles(Path folder) throws IOException {
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(folder)) throw new IOException("Profile folders cannot be symbolic links.");
        try (var files = Files.list(folder)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> isDataFile(path.getFileName().toString())).sorted().toList();
        }
    }

    public static List<Path> discover(Path root) throws IOException {
        if (!Files.exists(root)) return List.of();
        try (var folders = Files.list(root)) {
            return folders.filter(path -> validName(path.getFileName().toString()))
                .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        }
    }

    public static boolean exists(Path root, String name) throws IOException {
        if (!Files.exists(root)) return false;
        try (var entries = Files.list(root)) {
            return entries.anyMatch(path -> path.getFileName().toString().equalsIgnoreCase(name));
        }
    }

    public static void rename(Path root, String original, String name) throws IOException {
        Path source = resolve(root, original);
        Path destination = resolve(root, name);
        if (original.equals(name)) return;
        if (exists(root, name)) throw new IOException("Profile folder already exists.");
        // Metadata-only profiles need no folder until a component is saved.
        if (Files.exists(source)) Files.move(source, destination);
    }

    @FunctionalInterface
    public interface Writer {
        void write(String name, Path destination) throws IOException;
    }

    /** Publish a new folder only after every imported file has been written successfully. */
    public static void importFiles(Path root, String name, List<String> filenames, Writer writer) throws IOException {
        Path target = resolve(root, name);
        if (filenames.isEmpty() || filenames.stream().anyMatch(filename -> !isDataFile(filename))) {
            throw new IOException("Imported file does not contain valid profile data.");
        }
        Files.createDirectories(target.getParent());
        if (exists(root, name)) throw new IOException("A profile named '" + name + "' already exists.");
        Path staging = Files.createTempDirectory(target.getParent(), ".import-");
        try {
            for (String filename : filenames) writer.write(filename, staging.resolve(filename));
            // No REPLACE_EXISTING: an import must never replace another profile.
            Files.move(staging, target);
        } finally {
            if (Files.exists(staging)) {
                try (var files = Files.list(staging)) {
                    for (Path file : files.toList()) Files.deleteIfExists(file);
                }
                Files.deleteIfExists(staging);
            }
        }
    }
}
