package udp;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import model.Rover;
import utils.*;

public class ClientML {

    private DatagramSocket socket;
    private InetAddress ipNave;
    private int portaNave;
    private int missaoAExecutar;
    private Rover rover;

    private int timeout_millis;
    private int max_tentativas;

    //Método auxiliar para log
    private void log(String msg) {
        int id = rover.getId();
        Logger.log("rover_" + id + ".log", msg);
    }

    public void start(String host, int porta, Rover rover, Config config) throws IOException, InterruptedException {
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(timeout_millis);

        this.ipNave = InetAddress.getByName(host);
        this.portaNave = porta;
        this.rover = rover;

        this.timeout_millis = config.getTimeoutMillis();
        this.max_tentativas = config.getMaxRetries();

        log("Cliente iniciado. Host=" + host + ", Porta=" + porta);

        while (true) {
            this.missaoAExecutar = 0;

            if (!rover.isReadyforMission()) {
                System.out.println("🔋 Vou carregar");
                log("Bateria baixa → a carregar…");
                rover.charge();
                System.out.println("🔋 Bateria carregada");
                log("Bateria carregada.");
            }

            pedirMissao();

            this.socket.setSoTimeout(max_tentativas * timeout_millis);
            String[] res = receberMissao();
            String idMissao = res[0];
            String interval = res[1];

            this.socket.setSoTimeout(timeout_millis);

            if (idMissao == null || idMissao.equals("")) {
                log("Missão não recebida → voltando a pedir.");
                continue;
            }

            enviarAtualizacoes(idMissao, interval);
            enviarFicheiroFinal(idMissao);

            System.out.println("✅ Missão concluída — a pedir nova...");
            log("Missão " + idMissao + " concluída.");

            Thread.sleep(5000);
        }
    }

    private void pedirMissao() throws IOException {
        byte[] dados = new byte[]{ (byte) 0x0 };
        socket.send(new DatagramPacket(dados, dados.length, ipNave, portaNave));
        System.out.println("📨 Pedido de missão enviado.");
        log("Pedido de missão enviado.");
    }

    private String[] receberMissao() throws IOException {
        byte[] buffer = new byte[1413];
        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);

        log("À espera de missão...");

        while (true) {
            try {
                socket.receive(pacote);
                byte[] dados = Arrays.copyOf(pacote.getData(), pacote.getLength());

                if (dados.length > 0 && dados[0] == 0x01) {
                    ByteBuffer bb = ByteBuffer.wrap(dados);
                    bb.get();
                    int idMissao = bb.getInt();
                    int x1 = bb.getInt();
                    int y1 = bb.getInt();
                    int x2 = bb.getInt();
                    int y2 = bb.getInt();
                    int maximum = bb.getInt();
                    int interval = bb.getInt();
                    int descLen = bb.getInt();

                    String desc = "";
                    if (descLen > 0 && descLen <= bb.remaining()) {
                        byte[] db = new byte[descLen];
                        bb.get(db);
                        desc = new String(db, StandardCharsets.UTF_8);
                    }

                    String missaoCompleta = "MISSAO;" + idMissao + ";" + x1 + ";" + y1 + ";" + x2 + ";" + y2 + ";" +
                            desc.replace(";", ",") + ";" + maximum + ";" + interval;

                    System.out.println("🛰️ Missão recebida (binária): " + missaoCompleta);
                    log("Missão recebida: " + missaoCompleta);

                    this.missaoAExecutar = idMissao;

                    enviarACK("MISSAO", Integer.toString(idMissao), null);

                    new Thread(() -> {
                        try {
                            log("Rover iniciou execução da missão " + idMissao);
                            rover.moveTarget(x1, y1, x2, y2);
                            rover.mission(x1, y1, x2, y2, maximum);
                        } catch (InterruptedException e) {
                            log("Erro interno ao executar missão: " + e.getMessage());
                        }
                    }).start();

                    return new String[]{ Integer.toString(idMissao), Integer.toString(interval) };
                }

            } catch (SocketTimeoutException e) {
                System.out.println("⏳ Timeout à espera da missão.");
                log("Timeout à espera de missão.");
                return new String[]{"",""};
            }
        }
    }

    private void enviarAtualizacoes(String idMissao, String inter) throws IOException, InterruptedException {
        Thread.sleep(500);
        int interval = Integer.parseInt(inter);
        int seqNumber = 1;
        int progress;

        log("Início do envio de updates da missão " + idMissao);

        while ((progress = this.rover.getProgress()) != -1) {

            ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4);
            bb.put((byte) 0x02);
            bb.putInt(Integer.parseInt(idMissao));
            bb.putInt(seqNumber);
            bb.putInt(progress);

            byte[] dados = bb.array();

            if (!enviarComACK(dados, "UPDATE;" + idMissao + ";" + seqNumber, max_tentativas)) {
                System.out.println("❌ Falha ao enviar atualização " + seqNumber + ".");
                log("Falha ao enviar UPDATE seq=" + seqNumber);
            } else {
                log("UPDATE enviado com sucesso seq=" + seqNumber + " valor=" + progress);
            }

            Thread.sleep(interval * 1000);
            seqNumber++;
        }

        log("Updates da missão " + idMissao + " concluídos.");
    }

    private void enviarFicheiroFinal(String idMissao) throws IOException {

        int tamanhoParte = 1400;
        int tamanhoMin = tamanhoParte + 1;
        int tamanhoMax = tamanhoParte * 20;

        int tamanhoAleatorio = tamanhoMin + new Random().nextInt(tamanhoMax - tamanhoMin + 1);

        byte[] ficheiro = new byte[tamanhoAleatorio];
        Arrays.fill(ficheiro, (byte) 'A');

        int total = (int) Math.ceil(ficheiro.length / (double) tamanhoParte);

        log("Início do envio do ficheiro final (" + total + " partes) da missão " + idMissao);

        for (int i = 0; i < total; i++) {

            int inicio = i * tamanhoParte;
            int fim = Math.min(inicio + tamanhoParte, ficheiro.length);

            byte[] parte = Arrays.copyOfRange(ficheiro, inicio, fim);

            ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + parte.length);
            bb.put((byte) 0x03);
            bb.putInt(Integer.parseInt(idMissao));
            bb.putInt(i);
            bb.putInt(parte.length);
            bb.put(parte);

            byte[] dados = bb.array();

            if (!enviarComACK(dados, "FILE_PART;" + idMissao + ";" + i, max_tentativas)) {
                System.out.println("❌ Falha ao enviar parte do ficheiro (" + i + ").");
                log("Falha ao enviar FILE_PART " + i);
            } else {
                log("FILE_PART enviada com sucesso parte=" + i + " (" + parte.length + " bytes)");
            }
        }

        System.out.println("📁 Ficheiro final enviado em " + total + " partes.");
        log("Ficheiro final enviado com sucesso (" + total + " partes).");
    }

    private boolean enviarComACK(byte[] dados, String tipoEsperado, int maxTentativas) throws IOException {
        DatagramPacket pacote = new DatagramPacket(dados, dados.length, ipNave, portaNave);

        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {

            socket.send(pacote);
            log("Enviado pacote (" + tipoEsperado + "), tentativa " + tentativa);

            if (esperarACK(tipoEsperado)) return true;

            System.out.println("⏳ Timeout (" + tentativa + ") — retransmitindo " + tipoEsperado + "...");
            log("Timeout à espera de ACK para " + tipoEsperado);
        }

        log("Falha total — sem ACK após " + maxTentativas + " tentativas para " + tipoEsperado);
        return false;
    }

    private boolean esperarACK(String tipoEsperado) throws IOException {
        byte[] buffer = new byte[1413];
        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);


        while (true) {
            try {
                socket.receive(pacote);
                byte[] dados = Arrays.copyOf(pacote.getData(), pacote.getLength());

                if (dados.length > 0 && dados[0] == 0x04) {

                    ByteBuffer bb = ByteBuffer.wrap(dados);
                    bb.get();
                    byte tipoOrig = bb.get();
                    int id = bb.getInt();
                    int num = bb.getInt();

                    String tipoStr = tipoOrig == 0x02 ? "UPDATE" :
                                     tipoOrig == 0x03 ? "FILE_PART" : "??";

                    String ackStr = "ACK;" + tipoStr + ";" + id + ";" + num;

                    if (ackStr.contains(tipoEsperado)) {
                        System.out.println("✅ ACK binário recebido para " + tipoEsperado);
                        log("ACK recebido correto → " + ackStr);
                        return true;
                    } else {
                        System.out.println("⚠️ ACK binário recebido mas não corresponde → " + ackStr);
                        log("ACK inesperado descartado: " + ackStr);
                    }

                } else if (dados.length > 0 && dados[0] == 0x01) {

                    ByteBuffer bb = ByteBuffer.wrap(dados);
                    bb.get();
                    int idMissao = bb.getInt();

                    //Verificar se é a missão ativa, caso seja reenviar ack, caso não seja, ignorar.
                    if (idMissao == this.missaoAExecutar) {
                        enviarACK("MISSAO", Integer.toString(idMissao), null);
                        System.out.println("🔁 Recebido pacote de MISSAO repetido. Reenviado ACK.");
                        log("MISSAO repetida recebida → reenviado ACK");
                    }
                    else {
                        System.out.println("🔁 Recebido pacote de MISSAO inesperado. Ignorar.");
                        log("MISSAO inesperada recebida → ignorar");
                    }
                } else {
                    System.out.println("⚠️ Pacote inesperado — ignorado.");
                    log("Pacote inesperado recebido e descartado.");
                }

            } catch (SocketTimeoutException e) {
                return false;
            }
        }
    }

    private void enviarACK(String tipo, String idMissao, String numero) throws IOException {

        byte original = 0x01;

        int id = Integer.parseInt(idMissao);

        ByteBuffer bb = ByteBuffer.allocate(1 + 1 + 4);
        bb.put((byte) 0x04);
        bb.put(original);
        bb.putInt(id);

        byte[] dados = bb.array();

        socket.send(new DatagramPacket(dados, dados.length, ipNave, portaNave));

        log("ACK enviado para tipo=" + tipo + " missão=" + idMissao);
    }
}
