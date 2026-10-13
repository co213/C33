/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.misc.swarm;

import com.gaspoweredcake.client33.utils.player.ChatUtils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SwarmConnection extends Thread {
    public final Socket socket;
    private final BlockingQueue<String> messages = new ArrayBlockingQueue<>(128);

    public SwarmConnection(Socket socket) {
        this.socket = socket;
        setDaemon(true);
        start();
    }

    @Override
    public void run() {
        ChatUtils.infoPrefix("Swarm", "New worker connected on %s.", getIp(socket.getInetAddress().getHostAddress()));

        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            while (!isInterrupted()) {
                out.writeUTF(messages.take());
                out.flush();
            }
        } catch (InterruptedException e) {
            interrupt();
        } catch (IOException e) {
            if (!socket.isClosed()) ChatUtils.errorPrefix("Swarm", "Connection to worker closed.");
        } finally {
            disconnect();
        }
    }

    public boolean sendMessage(String message) {
        return !socket.isClosed() && !isInterrupted() && messages.offer(message);
    }

    public void disconnect() {
        interrupt();
        if (socket.isClosed()) return;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ChatUtils.infoPrefix("Swarm", "Worker disconnected on ip: %s.", socket.getInetAddress().getHostAddress());

        interrupt();
    }

    public String getConnection() {
        return getIp(socket.getInetAddress().getHostAddress()) + ":" + socket.getPort();
    }

    private String getIp(String ip) {
        return ip.equals("127.0.0.1") ? "localhost" : ip;
    }
}
