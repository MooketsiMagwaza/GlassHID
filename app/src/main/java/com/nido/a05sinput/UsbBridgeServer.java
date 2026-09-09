package com.nido.a05sinput;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cable-only loopback transport used through adb forward. */
final class UsbBridgeServer {
    interface Listener {
        void onHostLine(String line);
        void onStateChanged();
    }

    private static final int PORT = 27183;
    private final ExecutorService readers = Executors.newCachedThreadPool();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final Listener listener;
    private ServerSocket server;

    UsbBridgeServer(Listener listener) {
        this.listener = listener;
    }

    void start() {
        readers.execute(() -> {
            try {
                server = new ServerSocket(PORT, 4, InetAddress.getByName("127.0.0.1"));
                listener.onStateChanged();
                while (!server.isClosed()) {
                    Client client = new Client(server.accept());
                    if (client.write("HELLO A05S_INPUT 1")) {
                        clients.add(client);
                        readers.execute(() -> read(client));
                    } else {
                        client.close();
                    }
                    listener.onStateChanged();
                }
            } catch (IOException ignored) {
                listener.onStateChanged();
            }
        });
    }

    boolean isStarted() {
        return server != null && !server.isClosed();
    }

    boolean hasClients() {
        return !clients.isEmpty();
    }

    void send(String line) {
        writer.execute(() -> {
            for (Client client : new ArrayList<>(clients)) {
                if (!client.write(line)) {
                    clients.remove(client);
                    client.close();
                }
            }
            listener.onStateChanged();
        });
    }

    void close() {
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        for (Client client : clients) client.close();
        readers.shutdownNow();
        writer.shutdownNow();
    }

    private void read(Client client) {
        try {
            String line;
            while ((line = client.readLine()) != null) listener.onHostLine(line);
        } catch (IOException e) {
            Log.d("A05sInput", "Laptop battery link disconnected");
        } finally {
            clients.remove(client);
            client.close();
            listener.onStateChanged();
        }
    }

    private static final class Client {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        Client(Socket socket) throws IOException {
            this.socket = socket;
            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        String readLine() throws IOException {
            return reader.readLine();
        }

        synchronized boolean write(String line) {
            try {
                writer.write(line);
                writer.newLine();
                writer.flush();
                return true;
            } catch (IOException e) {
                Log.w("A05sInput", "USB client disconnected", e);
                return false;
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
