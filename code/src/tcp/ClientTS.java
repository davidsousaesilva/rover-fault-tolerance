package tcp;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

import model.Rover;

public class ClientTS {

    public void start(String serverIP, int port, Rover rover)
            throws UnknownHostException, IOException, InterruptedException {
        
        while (true) {
            try (Socket socket = new Socket(serverIP, port)) {
                System.out.println("[Client] Ligado ao servidor " + serverIP + ":" + port);
                OutputStream out = socket.getOutputStream();

                while (true) {
                    String mensagem = rover.getTelemetry();

                    int id = extrairInt(mensagem, "\"id\":");
                    int deltaX = extrairInt(mensagem, "\"deltaX\":");
                    int deltaY = extrairInt(mensagem, "\"deltaY\":");
                    int deltaZ = extrairInt(mensagem, "\"deltaZ\":");
                    byte battery = extrairByte(mensagem, "\"battery\":");
                    byte state = extrairByte(mensagem, "\"state\":");
                    byte erroPosicao = extrairByte(mensagem, "\"erroPosicao\":");
                    byte erroBateria = extrairByte(mensagem, "\"erroBateria\":");
                    byte erroEstado = extrairByte(mensagem, "\"erroEstado\":");

                    // Montar buffer com os campos
                    ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + 4 + 1 + 1 + 1 + 1 + 1);
                    buffer.putInt(id);
                    buffer.putInt(deltaX);
                    buffer.putInt(deltaY);
                    buffer.putInt(deltaZ);
                    buffer.put(battery);
                    buffer.put(state);
                    buffer.put(erroPosicao);
                    buffer.put(erroBateria);
                    buffer.put(erroEstado);

                    byte[] data = buffer.array();

                    // Enviar tamanho
                    out.write(ByteBuffer.allocate(4).putInt(data.length).array());

                    // Enviar dados
                    out.write(data);
                    out.flush();

                    System.out.println("Enviado pacote de telemetria (" + data.length + " bytes)");

                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                System.out.println("[Client] Handshake inicial falhado, a tentar novamente");
            }
        }
    }

    // Métodos auxiliares
    private int extrairInt(String json, String campo) {
        try {
            int start = json.indexOf(campo) + campo.length();
            int end = json.indexOf(",", start);
            if (end == -1)
                end = json.indexOf("}", start);
            return Integer.parseInt(json.substring(start, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private byte extrairByte(String json, String campo) {
        try {
            int start = json.indexOf(campo) + campo.length();
            int end = json.indexOf(",", start);
            if (end == -1)
                end = json.indexOf("}", start);
            return Byte.parseByte(json.substring(start, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
