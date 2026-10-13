package com.gaspoweredcake.client33.utils.navigation;

import java.util.LinkedHashSet;
import java.util.List;

/** Validates task forms before replacing a running pathfinder task. No chat prefix is required. */
public final class BaritoneTasks {
    private BaritoneTasks() { }

    public static String goTo(int x, int y, int z, boolean ignoreY) {
        coordinates(x, z);
        return "goto " + x + (ignoreY ? "" : " " + y) + " " + z;
    }

    public static String mine(List<String> blocks, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Quantity must be zero or positive.");
        var unique = new LinkedHashSet<String>();
        for (String block : blocks) {
            if (!block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Choose valid block identifiers.");
            unique.add(block);
        }
        if (unique.isEmpty()) throw new IllegalArgumentException("Select at least one block to mine.");
        return "mine " + (quantity == 0 ? "" : quantity + " ") + String.join(" ", unique);
    }

    public static String follow(String player) {
        player = player.trim();
        if (!player.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Enter a Minecraft player name.");
        return "follow player " + player;
    }

    public static String explore(int x, int z) {
        coordinates(x, z);
        return "explore " + x + " " + z;
    }

    public static String farm(int radius) {
        if (radius < 0) throw new IllegalArgumentException("Farm radius must be zero or positive.");
        return radius == 0 ? "farm" : "farm " + radius;
    }

    public static String tunnel(int height, int width, int length) {
        if (height < 2 || width < 1 || length < 1) throw new IllegalArgumentException("Tunnel needs height 2+, width 1+ and length 1+.");
        return "tunnel " + height + " " + width + " " + length;
    }

    public static String build(String file, int x, int y, int z) {
        coordinates(x, z);
        file = file.trim();
        if (!file.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}") || file.contains(".."))
            throw new IllegalArgumentException("Use a schematic filename without spaces or folders.");
        return "build " + file + " " + x + " " + y + " " + z;
    }

    public static String command(String input, String prefix) {
        input = input.trim();
        if (!prefix.isEmpty() && input.startsWith(prefix)) input = input.substring(prefix.length()).trim();
        else if (input.startsWith("#")) input = input.substring(1).trim();
        if (input.isEmpty()) throw new IllegalArgumentException("Enter a Baritone command.");
        if (input.length() > 2048 || input.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Enter one command on one line.");
        return input;
    }

    private static void coordinates(int x, int z) {
        if (Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000)
            throw new IllegalArgumentException("Coordinates exceed Minecraft's world limits.");
    }
}
