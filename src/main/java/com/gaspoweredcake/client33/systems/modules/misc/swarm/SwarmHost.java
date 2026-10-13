/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.misc.swarm;

import com.gaspoweredcake.client33.utils.player.ChatUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SwarmHost extends Thread {
    private ServerSocket socket;
    private final SwarmConnection[] clientConnections = new SwarmConnection[50];

    public SwarmHost(int port) {
        try {
            socket = new ServerSocket(port);
        } catch (IOException e) {
            socket = null;
            ChatUtils.errorPrefix("Swarm", "Couldn't start a server on port %s.", port);
            e.printStackTrace();
        }

        setDaemon(true);
        if (socket != null) start();
    }

    @Override
    public void run() {
        ChatUtils.infoPrefix("Swarm", "Listening for incoming connections on port %s.", socket.getLocalPort());

        while (!isInterrupted()) {
            try {
                Socket connection = socket.accept();
                assignConnectionToSubServer(connection);
            } catch (IOException e) {
                if (!isInterrupted() && !socket.isClosed()) ChatUtils.errorPrefix("Swarm", "Error accepting a worker connection.");
                break;
            }
        }
    }

    public synchronized void assignConnectionToSubServer(Socket connection) {
        if (isInterrupted() || socket == null || socket.isClosed()) {
            try { connection.close(); } catch (IOException ignored) { }
            return;
        }
        for (int i = 0; i < clientConnections.length; i++) {
            if (clientConnections[i] == null || !clientConnections[i].isAlive() || clientConnections[i].socket.isClosed()) {
                this.clientConnections[i] = new SwarmConnection(connection);
                return;
            }
        }
        try { connection.close(); } catch (IOException ignored) { }
    }

    public synchronized void disconnect() {
        interrupt();
        if (socket == null || socket.isClosed()) return;
        for (SwarmConnection connection : clientConnections) {
            if (connection != null) connection.disconnect();
        }

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ChatUtils.infoPrefix("Swarm", "Server closed on port %s.", socket.getLocalPort());

        interrupt();
    }

    public synchronized void sendMessage(String message) {
        for (SwarmConnection connection : clientConnections) {
            if (connection != null && connection.isAlive() && !connection.sendMessage(message)) {
                ChatUtils.warningPrefix("Swarm", "Worker queue is full or disconnected; command was not sent.");
            }
        }
    }

    public synchronized SwarmConnection[] getConnections() {
        return clientConnections.clone();
    }

    public synchronized int getConnectionCount() {
        int count = 0;

        for (SwarmConnection clientConnection : clientConnections) {
            if (clientConnection != null && clientConnection.isAlive() && !clientConnection.socket.isClosed()) count++;
        }

        return count;
    }
}
