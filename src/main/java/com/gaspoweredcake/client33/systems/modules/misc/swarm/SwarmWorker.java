/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.misc.swarm;

import com.gaspoweredcake.client33.commands.Commands;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.utils.player.ChatUtils;
import net.minecraft.world.level.block.Block;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.InetSocketAddress;
import static com.gaspoweredcake.client33.Client33.mc;

public class SwarmWorker extends Thread {
    private Socket socket;
    public Block target;

    public SwarmWorker(String ip, int port) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), 5000);
        } catch (Exception e) {
            if (socket != null) try { socket.close(); } catch (IOException ignored) { }
            socket = null;
            ChatUtils.warningPrefix("Swarm", "Server not found at %s on port %s.", ip, port);
            e.printStackTrace();
        }

        setDaemon(true);
        if (socket != null) start();
    }

    @Override
    public void run() {
        ChatUtils.infoPrefix("Swarm", "Connected to Swarm host on at %s on port %s.", getIp(socket.getInetAddress().getHostAddress()), socket.getPort());

        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {


            while (!isInterrupted()) {
                String read = in.readUTF();

                if (read.startsWith("swarm")) {
                    ChatUtils.infoPrefix("Swarm", "Received command: (highlight)%s", read);

                    var world = mc.level;
                    mc.execute(() -> {
                        if (isInterrupted() || mc.level != world || mc.getConnection() == null) return;
                        try {
                            Commands.dispatch(read);
                        } catch (Exception e) {
                            ChatUtils.error("Error executing swarm command.");
                        }
                    });
                }
            }


        } catch (IOException e) {
            ChatUtils.errorPrefix("Swarm", "Error in connection to host.");
            e.printStackTrace();
            disconnect();
        }
    }

    public void disconnect() {
        interrupt();
        if (socket == null || socket.isClosed()) return;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mc.execute(() -> PathManagers.get().stop());

        ChatUtils.infoPrefix("Swarm", "Disconnected from host.");

        interrupt();
    }

    public void tick() {
        if (target == null) return;

        PathManagers.get().stop();
        PathManagers.get().mine(target);

        target = null;
    }

    public String getConnection() {
        return getIp(socket.getInetAddress().getHostAddress()) + ":" + socket.getPort();
    }

    private String getIp(String ip) {
        return ip.equals("127.0.0.1") ? "localhost" : ip;
    }
}
