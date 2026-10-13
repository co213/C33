package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.systems.profiles.ProfileFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Real filesystem scenarios for profile imports, recovery and export selection. */
public final class ProfileFilesRegression {
    public static void main(String[] args) throws IOException {
        run();
    }

    public static void run() throws IOException {
        Path root = Files.createTempDirectory("client33-profiles-");
        try {
            Path profiles = root.resolve("profiles");
            check(ProfileFiles.discover(profiles).isEmpty(), "Missing profile index/folder is a normal first launch");
            for (String name : new String[] {"", " ", ".", "..", "../outside", "/tmp", "a/b", "a\\b", "CON", "Name "}) {
                try {
                    ProfileFiles.resolve(profiles, name);
                    throw new AssertionError("Unsafe profile name accepted: " + name);
                } catch (IllegalArgumentException expected) { }
            }
            check(ProfileFiles.validName("My Profile-2"), "Ordinary profile names remain supported");
            ProfileFiles.importFiles(profiles, "Saved", List.of("modules.nbt", "world.nbt"),
                (name, target) -> Files.writeString(target, "saved " + name));
            Path saved = profiles.resolve("Saved");
            check(Files.readString(saved.resolve("modules.nbt")).equals("saved modules.nbt"), "Import retains source data");
            check(ProfileFiles.discover(profiles).equals(List.of(saved)), "Recovery finds saved profile folders");
            check(ProfileFiles.dataFiles(saved).size() == 2, "Recovery recognizes component files");
            check(Files.readString(saved.resolve("modules.nbt")).equals("saved modules.nbt"), "Recovery must never save current settings");
            for (String name : List.of("Saved", "saved")) {
                try {
                    ProfileFiles.importFiles(profiles, name, List.of("modules.nbt"),
                        (_, target) -> Files.writeString(target, "overwritten"));
                    throw new AssertionError("Import overwrote an existing profile");
                } catch (IOException expected) { }
            }
            check(Files.readString(saved.resolve("modules.nbt")).equals("saved modules.nbt"), "Conflicts preserve existing data");
            IOException failure = new IOException("Simulated disk failure");
            try {
                ProfileFiles.importFiles(profiles, "Broken", List.of("hud.nbt", "modules.nbt"), (name, target) -> {
                    Files.writeString(target, "partial");
                    if (name.equals("modules.nbt")) throw failure;
                });
                throw new AssertionError("Failed import reported success");
            } catch (IOException expected) {
                check(expected == failure, "Original write failure is retained");
            }
            check(!Files.exists(profiles.resolve("Broken")), "Failed import cannot publish partial data");
            try (var entries = Files.list(profiles)) {
                check(entries.count() == 1, "Failed imports clean staging folders");
            }
            for (List<String> filenames : List.of(List.<String>of(), List.of("../escape.nbt"), List.of("nested/file.nbt"))) {
                try {
                    ProfileFiles.importFiles(profiles, "Invalid", filenames, (_, target) -> Files.writeString(target, "bad"));
                    throw new AssertionError("Invalid archive accepted");
                } catch (IOException expected) { }
            }
            for (String component : List.of("hud.nbt", "macros.nbt", "modules.nbt")) {
                check(!ProfileFiles.selected(component, false, false, false, true), "Waypoints-only export excludes " + component);
            }
            check(ProfileFiles.selected("world.nbt", false, false, false, true), "Waypoints-only export includes world data");
            check(ProfileFiles.selected("hud.nbt", true, false, false, false), "HUD export includes HUD");
            check(!ProfileFiles.selected("world.nbt", true, true, true, false), "Unchecked waypoints stay excluded");
            Files.writeString(saved.resolve("modules.nbt-old.backup.nbt"), "recovery backup");
            check(ProfileFiles.dataFiles(saved).size() == 2, "Recovery backups do not become waypoint data");
            Path outside = Files.createDirectory(root.resolve("outside"));
            Files.createSymbolicLink(profiles.resolve("Linked"), outside);
            Files.createSymbolicLink(saved.resolve("linked.nbt"), outside);
            check(ProfileFiles.discover(profiles).equals(List.of(saved)), "Recovery does not follow linked folders");
            check(ProfileFiles.dataFiles(saved).size() == 2, "Export does not follow linked files");
            try {
                ProfileFiles.resolve(profiles, "Linked");
                throw new AssertionError("Linked folder accepted for filesystem operations");
            } catch (IllegalArgumentException expected) { }
            ProfileFiles.rename(profiles, "Saved", "Renamed");
            check(!Files.exists(saved), "Renaming removes the old folder");
            check(Files.readString(profiles.resolve("Renamed/modules.nbt")).equals("saved modules.nbt"),
                "Renaming preserves saved component data");
            Files.createDirectory(profiles.resolve("Taken"));
            try {
                ProfileFiles.rename(profiles, "Renamed", "taken");
                throw new AssertionError("Rename accepted a case-insensitive conflict");
            } catch (IOException expected) { }
            check(Files.isDirectory(profiles.resolve("Renamed")), "Failed rename preserves the original profile");
            ProfileFiles.rename(profiles, "Metadata only", "New metadata");
            check(!Files.exists(profiles.resolve("New metadata")), "Metadata-only profiles can be renamed without creating data");
            System.out.println("Profile filesystem regressions passed.");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
