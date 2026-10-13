/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.misc;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.utils.files.LogRetention;
import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@NullMarked
public class PacketLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOutput = settings.createGroup("Output");

    private final Setting<Set<PacketType<? extends Packet<?>>>> s2cPackets = sgGeneral.add(new PacketListSetting.Builder()
        .name("S2C-packets")
        .description("Server-to-client packets to log.")
        .clientbound()
        .build()
    );

    private final Setting<Set<PacketType<? extends Packet<?>>>> c2sPackets = sgGeneral.add(new PacketListSetting.Builder()
        .name("C2S-packets")
        .description("Client-to-server packets to log.")
        .serverbound()
        .build()
    );

    private final Setting<Boolean> showTimestamp = sgOutput.add(new BoolSetting.Builder()
        .name("show-timestamp")
        .description("Show timestamp for each logged packet.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showPacketData = sgOutput.add(new BoolSetting.Builder()
        .name("show-packet-data")
        .description("Show the packet's toString() data for debugging.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showCount = sgOutput.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Show how many times each packet type has been logged.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showSummary = sgOutput.add(new BoolSetting.Builder()
        .name("show-summary")
        .description("Show final packet count summary when module is deactivated.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logToChat = sgOutput.add(new BoolSetting.Builder()
        .name("log-to-chat")
        .description("Log packets to chat.")
        .defaultValue(true)
        .build()
    );

    // File logging settings

    private final Setting<Boolean> logToFile = sgOutput.add(new BoolSetting.Builder()
        .name("log-to-file")
        .description("Save packet logs to a file in the client33 folder.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> flushInterval = sgOutput.add(new IntSetting.Builder()
        .name("flush-interval")
        .description("How often to flush logs to disk (in seconds).")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .visible(logToFile::get)
        .build()
    );

    private final Setting<Integer> maxFileSizeMB = sgOutput.add(new IntSetting.Builder()
        .name("max-file-size-mb")
        .description("Maximum size per log file in MB. Creates new file when exceeded.")
        .defaultValue(10)
        .min(1)
        .sliderMax(100)
        .visible(logToFile::get)
        .build()
    );

    private final Setting<Integer> maxTotalLogsMB = sgOutput.add(new IntSetting.Builder()
        .name("max-total-logs-mb")
        .description("Maximum total disk space for all packet logs in MB. Deletes oldest when exceeded.")
        .defaultValue(50)
        .min(1)
        .sliderMax(500)
        .visible(logToFile::get)
        .build()
    );

    private static final Path PACKET_LOGS_DIR = Client33.FOLDER.toPath().resolve("packet-logs");
    private static final int LINE_SEPARATOR_BYTES = System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private final Object2IntOpenHashMap<PacketType<? extends Packet<?>>> packetCounts = new Object2IntOpenHashMap<>();
    private @Nullable BufferedWriter fileWriter;
    private @Nullable Path currentLogFile;
    private long lastFlushMs;
    private long currentFileSizeBytes;
    private int currentFileIndex;
    private @Nullable LocalDateTime sessionStartTime;

    public PacketLogger() {
        super(Categories.Misc, "packet-logger", "Allows you to log certain packets.");
        runInMainMenu = true;
    }

    @Override
    public synchronized void onActivate() {
        closeFileWriter();

        packetCounts.clear();
        lastFlushMs = System.currentTimeMillis();
        sessionStartTime = LocalDateTime.now();
        currentFileIndex = 0;
        currentFileSizeBytes = 0;

        if (logToFile.get()) {
            try {
                Files.createDirectories(PACKET_LOGS_DIR);
                cleanupOldLogs();
                openNewLogFile();
            } catch (IOException e) {
                error("Failed to initialize packet logging: %s", e.getMessage());
                fileWriter = null;
            }
        }
    }

    @Override
    public synchronized void onDeactivate() {
        if (showSummary.get() && !packetCounts.isEmpty()) {
            logSummary();
        }
        closeFileWriter();
    }

    private synchronized void logPacket(String direction, Packet<?> packet) {
        if (!isActive() || (!logToChat.get() && !logToFile.get())) return;

        // Update count
        packetCounts.addTo(packet.type(), 1);

        // Build log message
        StringBuilder msg = new StringBuilder(128);
        if (showTimestamp.get()) msg.append("[").append(LocalDateTime.now().format(TIME_FORMATTER)).append("] ");
        msg.append(direction).append(" ").append(packet.type());
        if (showCount.get()) msg.append(" (#").append(packetCounts.getInt(packet.type())).append(")");
        if (showPacketData.get()) msg.append("\n  Data: ").append(packet);

        // Log to chat and/or file
        String line = msg.toString();
        if (logToChat.get()) info(line);
        if (logToFile.get()) writeLine(line);
    }

    private void logSummary() {
        int totalPackets = packetCounts.values().intStream().sum();

        List<String> lines = new ArrayList<>();
        lines.add("--- SUMMARY ---");
        lines.add("Final packet counts (total " + totalPackets + "):");

        packetCounts.object2IntEntrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()))
            .forEach(e -> lines.add("  %s: %d".formatted(e.getKey().toString(), e.getIntValue())));

        for (String line : lines) {
            if (logToChat.get()) info(line);
            if (logToFile.get()) writeLine(line);
        }
    }

    private void writeLine(String line) {
        if (fileWriter == null) return;

        try {
            int lineBytes = line.getBytes(StandardCharsets.UTF_8).length + LINE_SEPARATOR_BYTES;
            if (currentFileSizeBytes + lineBytes > Math.min(maxFileSizeMB.get(), maxTotalLogsMB.get()) * 1024L * 1024L) openNewLogFile();

            fileWriter.write(line);
            fileWriter.newLine();
            currentFileSizeBytes += lineBytes;

            long now = System.currentTimeMillis();
            if (now - lastFlushMs >= flushInterval.get() * 1000L) {
                fileWriter.flush();
                cleanupOldLogs();
                lastFlushMs = now;
            }
        } catch (IOException e) {
            error("Failed to write to packet log file: %s. File logging disabled.", e.getMessage());
            closeFileWriter();
        }
    }

    private void openNewLogFile() throws IOException {
        closeFileWriter();
        if (sessionStartTime == null) sessionStartTime = LocalDateTime.now();
        while (true) {
            String name = "packets-%s-%d.log".formatted(sessionStartTime.format(FILE_NAME_FORMATTER), currentFileIndex++);
            Path path = PACKET_LOGS_DIR.resolve(name);
            try {
                fileWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                currentLogFile = path;
                break;
            } catch (java.nio.file.FileAlreadyExistsException ignored) { }
        }
        currentFileSizeBytes = 0;
        cleanupOldLogs();
    }

    private void closeFileWriter() {
        if (fileWriter != null) {
            try {
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException _) {
                // Safe to ignore on close or rotation
            }
            fileWriter = null;
            currentLogFile = null;
        }
    }

    /**
     * Cleans up old log files if total size exceeds the maximum limit.
     * Deletes oldest files first.
     */
    private void cleanupOldLogs() throws IOException {
        LogRetention.prune(PACKET_LOGS_DIR, currentLogFile, maxTotalLogsMB.get() * 1024L * 1024L);
    }


    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (s2cPackets.get().contains(event.packet.type())) logPacket("<- S2C", event.packet);
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onSendPacket(PacketEvent.Send event) {
        if (c2sPackets.get().contains(event.packet.type())) logPacket("-> C2S", event.packet);
    }
}
