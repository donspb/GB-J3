package ru.geekbrains.j_two.chat.server.core;

import ru.geekbrains.j_two.chat.library.Protocol;
import ru.geekbrains.j_two.network.ServerSocketThread;
import ru.geekbrains.j_two.network.ServerSocketThreadListener;
import ru.geekbrains.j_two.network.SocketThread;
import ru.geekbrains.j_two.network.SocketThreadListener;

import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer implements ServerSocketThreadListener, SocketThreadListener {

    private ServerSocketThread server;
    private final DateFormat DATE_FORMAT = new SimpleDateFormat("[HH:mm:ss] ");
    private ChatServerListener listener;
    private Vector<SocketThread> clients;
    private ExecutorService executorService;

    public ChatServer(ChatServerListener listener) {
        this.listener = listener;
        clients = new Vector<>();
    }

    public void start(int port) {
        if (server != null && server.isAlive()) {
            putLog("Server already started");
        } else {
            server = new ServerSocketThread(this,"Server", port, 2000);
        }
        executorService = Executors.newCachedThreadPool();
    }

    public void stop() {
        if (server == null || !server.isAlive()) {
            putLog("Server is not running");
        } else {
            server.interrupt();
        }
    }

    private void putLog(String msg) {
        msg = DATE_FORMAT.format(System.currentTimeMillis()) + Thread.currentThread().getName() + ": " + msg;
        listener.onChatServerMessage(msg);
    }

    /**
     * Server Socket Listener implementation
     *
     */

    @Override
    public void onServerStart(ServerSocketThread thread) {
        putLog("Server socket thread started");
        SqlClient.connect();
    }

    @Override
    public void onServerStop(ServerSocketThread thread) {
        putLog("Server socket thread stopped");
        SqlClient.disconnect();
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).close();
        }
        executorService.shutdown();
    }

    @Override
    public void onServerSocketCreated(ServerSocketThread thread, ServerSocket server) {
        putLog("Server socket created");
    }

    @Override
    public void onServerTimeout(ServerSocketThread thread, ServerSocket server) {
        //    putLog("Server socket thread timeout");
    }

    @Override
    public void onSocketAccepted(ServerSocketThread thread, ServerSocket server, Socket socket) {
        putLog("Client connected");
        String name= "SocketThread" + socket.getInetAddress() + ":" + socket.getPort();
        new ClientThread(this, name, socket, executorService);
    }

    @Override
    public void onServerException(ServerSocketThread thread, Throwable exception) {
        exception.printStackTrace();
    }

    /**
     * Socket Thread Listener implementation
     *
     */

    @Override
    public synchronized void onSocketStart(SocketThread thread, Socket socket) {
        putLog("Socket created");
    }

    @Override
    public synchronized void onSocketStop(SocketThread thread) {
        ClientThread client = (ClientThread) thread;
        clients.remove(thread);
        if (client.isAuthorized() && !client.isReconnecting()) {
            sendToAllAuthorizedClients(Protocol.getTypeBroadcast("Server", client.getNickname() + " disconnected"));
        }
        sendToAllAuthorizedClients(Protocol.getUserList(getUsers()));
    }

    @Override
    public synchronized void onSocketReady(SocketThread thread, Socket socket) {
        clients.add(thread);
    }

    @Override
    public synchronized void onReceiveString(SocketThread thread, Socket socket, String msg) {
        ClientThread client = (ClientThread) thread;
        if (client.isAuthorized()) {
            handleAuthClientMessage(client, msg);
        } else {
            handleNonAuthClientMessage(client, msg);
        }
    }

    private void handleAuthClientMessage(ClientThread client, String msg) {
        String[] arr = msg.split(Protocol.DELIMITER);
        String msgType = arr[0];
        switch (msgType) {
            case Protocol.USER_BROADCAST:
                sendToAllAuthorizedClients(Protocol.getTypeBroadcast(client.getNickname(), arr[1]));
                break;
            case Protocol.USER_CHANGENAME:
                try {
                    String oldname = client.getNickname();
                    SqlClient.changeNickname(arr[1], oldname);
                    client.setNickname(arr[1]);
                    sendToAllAuthorizedClients(Protocol.getUserRenamed(oldname, arr[1]));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                sendToAllAuthorizedClients(Protocol.getUserList(getUsers()));
                break;
            default:
                client.msgFormatError(msg);
        }
    }

    private void handleNonAuthClientMessage(ClientThread client, String msg) {
        String[] arr = msg.split(Protocol.DELIMITER);
        if (arr.length != 3 || !arr[0].equals(Protocol.AUTH_REQUEST)) {
            client.msgFormatError(msg);
            return;
        }
        String login = arr[1];
        String password = arr[2];
        String nickname = null;
        try {
            nickname = SqlClient.getNickname(login, password);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (nickname == null) {
            putLog("Invalid credentials attempt for login = " + login);
            client.authFail();
            return;
        } else {
            ClientThread oldClient = findClientByNickname(nickname);
            client.authAccept(nickname);
            if (oldClient == null) {
                sendToAllAuthorizedClients(Protocol.getTypeBroadcast("Server", nickname + " connected"));
            } else {
                oldClient.reconnect();
                clients.remove(oldClient);
            }
        }
        sendToAllAuthorizedClients(Protocol.getUserList(getUsers()));
    }

    @Override
    public synchronized void onSocketException(SocketThread thread, Exception exception) {
        exception.printStackTrace();
    }

    private void sendToAllAuthorizedClients(String msg) {
//        String[] arr = msg.split(Protocol.DELIMITER);
//        msg = "\n" + arr[1] + " " + arr[2] + ":\n  " + arr[3];

        for (int i = 0; i < clients.size(); i++) {
            ClientThread recepient = (ClientThread) clients.get(i);
            if (!recepient.isAuthorized()) continue;
            recepient.sendMessage(msg);
        }

    }

    private String getUsers() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clients.size(); i++) {
            ClientThread client = (ClientThread) clients.get(i);
            if (!client.isAuthorized()) continue;
            sb.append(client.getNickname()).append(Protocol.DELIMITER);
        }
        return sb.toString();
    }

    private synchronized ClientThread findClientByNickname(String nickname) {
        for (int i = 0; i < clients.size(); i++) {
            ClientThread client = (ClientThread) clients.get(i);
            if (!client.isAuthorized()) continue;
            if (client.getNickname().equals(nickname)) return client;
        }
        return null;
    }
}