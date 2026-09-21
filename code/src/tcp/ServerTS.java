package tcp;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class ServerTS {

    public void start(String ip, int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(ip, port));
            System.out.println("[Server] Servidor a escutar em " + ip + ":" + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] Novo cliente ligado: " + clientSocket.getRemoteSocketAddress());

                new Thread(() -> {
                    try {
                        handleClient(clientSocket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        }

    }

    private void handleClient(Socket clientSocket) throws IOException {
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        while (true) {
            int length;
            try {
                length = in.readInt(); // lê o tamanho
            } catch (EOFException e) {
                System.out.println("[Server] Cliente desconectado: " + clientSocket.getRemoteSocketAddress());
                break;
            }

            byte[] data = new byte[length];
            in.readFully(data);

            ByteBuffer buffer = ByteBuffer.wrap(data);
            int id = buffer.getInt();
            int deltaX = buffer.getInt();
            int deltaY = buffer.getInt();
            int deltaZ = buffer.getInt();
            byte battery = buffer.get();
            byte state = buffer.get();
            byte erroPosicao = buffer.get();
            byte erroBateria = buffer.get();
            byte erroEstado = buffer.get();

            String telemetria = String.format(
                    "{\"id\":%d,\"deltaX\":%d,\"deltaY\":%d,\"deltaZ\":%d,\"battery\":%d,\"state\":%d,"
                            + "\"erroPosicao\":%d,\"erroBateria\":%d,\"erroEstado\":%d}",
                    id, deltaX, deltaY, deltaZ, battery, state,
                    erroPosicao, erroBateria, erroEstado);

            guardarTelemetria(id, telemetria);
            System.out.println("Recebida telemetria do rover " + id);
        }
    }

    private void guardarTelemetria(int id, String mensagem) {
        if (id < 0)
            return;
        String filename = "telemetria_rover" + id + ".log";
        try (FileWriter fw = new FileWriter(filename, true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw)) {
            out.println(mensagem);
        } catch (IOException e) {
            System.err.println("Erro a escrever ficheiro: " + e.getMessage());
        }
    }
}
