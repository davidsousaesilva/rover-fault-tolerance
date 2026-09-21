package udp;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.*;

import utils.*;

public class RoverHandlerML implements Runnable {

    private DatagramSocket socket;
    private final InetAddress ip;
    private final int porta;
    private final String idRover;

    private int timeout_millis;
    private int max_tentativas;

    private final Queue<String> filaPacotes = new LinkedList<>();
    private final Queue<String> filaACKs = new LinkedList<>();

    private final ReentrantLock lockPacotes = new ReentrantLock();
    private final Condition temPacotes = lockPacotes.newCondition();

    private final ReentrantLock lockAcks = new ReentrantLock();

    private final Map<String, String> missoes = new LinkedHashMap<>();
    private final Map<String, Set<String>> updatesRecebidos = new HashMap<>();
    private final Map<String, Set<String>> partesRecebidas = new HashMap<>();

    public RoverHandlerML(InetAddress ip, int porta, String idRover, Config config) {
        try {
            this.socket = new DatagramSocket();
        } catch (SocketException s) {
            s.printStackTrace();
        }
        this.ip = ip;
        this.porta = porta;
        this.idRover = idRover;

        this.timeout_millis = config.getTimeoutMillis();
        this.max_tentativas = config.getMaxRetries();
    }

    public void addMissao(String idMissao, String missaoCompleta) {
        this.missoes.put(idMissao, missaoCompleta);
    }

    public void adicionarPacote(String dados) {
        lockPacotes.lock();
        try {
            filaPacotes.add(dados);
            temPacotes.signal();
        } finally {
            lockPacotes.unlock();
        }
    }

    public void adicionarACK(String dados) {
        lockAcks.lock();
        try {
            if (dados.startsWith("ACK;")) {
                filaACKs.add(dados);
            }
        } finally {
            lockAcks.unlock();
        }
    }

    @Override
    public void run() {
        while (true) {
            String dados = null;
            lockPacotes.lock();
            try {
                while (filaPacotes.isEmpty()) {
                    temPacotes.await();
                }
                dados = filaPacotes.poll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                lockPacotes.unlock();
            }

            if (dados != null) processarPacote(dados);
        }
    }

    private void log(String msg) {
        Logger.log("nave_" + idRover + ".log", msg);
    }

    private void processarPacote(String conteudo) {

        if (conteudo.equals("PEDIR_MISSAO")) {
            System.out.println("Rover " + idRover + " pediu missão.");
            log("PEDIR_MISSAO recebido.");

            String missaoId = null;
            for (String id : missoes.keySet()) {
                missaoId = id;
                break;
            }

            if (missaoId != null) {
                boolean enviada = enviarMissao(missaoId);
                if (enviada) {
                    missoes.remove(missaoId);
                }
            } else {
                System.out.println("⚠️ Nenhuma missão disponível para enviar.");
                log("Nenhuma missão disponível.");
            }

        } else if (conteudo.startsWith("UPDATE;")) {

            String[] partes = conteudo.split(";", 4);
            if (partes.length >= 3) {
                String idMissao = partes[1];
                String num = partes[2];

                updatesRecebidos.putIfAbsent(idMissao, new HashSet<>());
                Set<String> recebidos = updatesRecebidos.get(idMissao);

                if (!recebidos.contains(num)) {
                    System.out.println("📡 Novo UPDATE do " + idRover + ": " + conteudo);
                    log("Novo UPDATE recebido: " + conteudo);
                    processarUpdate(idMissao, num, partes.length > 3 ? partes[3] : "");
                    recebidos.add(num);
                } else {
                    System.out.println("🔁 UPDATE repetido (" + num + ") de " + idRover + " — ignorado.");
                    log("UPDATE repetido (" + num + ")");
                }

                enviarACK("UPDATE", idMissao, num);
                missoes.remove(idMissao);
            }

        } else if (conteudo.startsWith("FILE_PART;")) {

            String[] partes = conteudo.split(";", 4);
            if (partes.length >= 4) {
                String idMissao = partes[1];
                String numParte = partes[2];

                partesRecebidas.putIfAbsent(idMissao, new HashSet<>());
                Set<String> recebidas = partesRecebidas.get(idMissao);

                if (!recebidas.contains(numParte)) {
                    System.out.println("📁 Nova parte de ficheiro recebida do " + idRover + ": " + idMissao + " parte " + numParte);
                    log("FILE_PART nova recebida: parte " + numParte);
                    processarParteFicheiro(idMissao, numParte, partes[3]);
                    recebidas.add(numParte);
                } else {
                    System.out.println("🔁 Parte de ficheiro repetida (" + numParte + ") de " + idRover + " — ignorada.");
                    log("FILE_PART repetido (" + numParte + ")");
                }

                enviarACK("FILE_PART", idMissao, numParte);

                missoes.remove(idMissao);
            }

        } else {
            System.out.println("❓ Pacote desconhecido de " + idRover + ": " + conteudo);
            log("Pacote desconhecido: " + conteudo);
        }
    }

    private boolean enviarMissao(String missaoId) {
        String missaoCompleta = missoes.get(missaoId);
        if (missaoCompleta == null) {
            System.out.println("⚠️ Missão " + missaoId + " não encontrada no mapa.");
            log("Missão inexistente: " + missaoId);
            return false;
        }

        try {
            String[] parts = missaoCompleta.split(";", -1);
            int id = Integer.parseInt(parts[1]);
            int x1 = Integer.parseInt(parts[2]);
            int y1 = Integer.parseInt(parts[3]);
            int x2 = Integer.parseInt(parts[4]);
            int y2 = Integer.parseInt(parts[5]);
            String desc = parts[6];
            int maximum = Integer.parseInt(parts[7]);
            int interval = Integer.parseInt(parts[8]);

            byte[] descBytes = desc.getBytes(StandardCharsets.UTF_8);

            ByteBuffer bb = ByteBuffer.allocate(1 + 4 * 7 + 4 + descBytes.length);
            bb.put((byte) 0x01);
            bb.putInt(id);
            bb.putInt(x1);
            bb.putInt(y1);
            bb.putInt(x2);
            bb.putInt(y2);
            bb.putInt(maximum);
            bb.putInt(interval);
            bb.putInt(descBytes.length);
            bb.put(descBytes);

            byte[] dados = bb.array();
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, ip, porta);

            if (enviarComACK("MISSAO;" + missaoId, pacote, max_tentativas)) {
                System.out.println("✅ Missão enviada e confirmada por " + idRover);
                log("Missão " + missaoId + " enviada com sucesso.");
                return true;
            } else {
                System.out.println("❌ Falha ao enviar missão para " + idRover);
                log("Falha ao enviar missão " + missaoId);
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            log("Erro ao montar missao: " + e.getMessage());
            return false;
        }
    }

    private boolean enviarComACK(String tipoEsperado, DatagramPacket pacote, int maxTentativas) {
        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            try {
                socket.send(pacote);
                log("Pacote enviado (" + tipoEsperado + "), tentativa " + tentativa);

                if (esperarACK(tipoEsperado, timeout_millis))
                    return true;

                System.out.println("⏳ Timeout (" + tentativa + ") — retransmitindo missão para " + idRover);
                log("Timeout à espera de ACK (" + tipoEsperado + ")");

            } catch (IOException e) {
                e.printStackTrace();
                log("Erro ao enviar pacote: " + e.getMessage());
            }
        }
        return false;
    }

    public boolean esperarACK(String tipoEsperado, long timeoutMillis) {
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeoutMillis) {
            lockAcks.lock();
            try {
                Iterator<String> it = filaACKs.iterator();
                while (it.hasNext()) {
                    String ack = it.next();

                    if (ack.contains(tipoEsperado)) {
                        it.remove();
                        log("ACK válido recebido: " + ack);
                        return true;
                    } else {
                        it.remove();
                        start = System.currentTimeMillis();
                        log("ACK descartado: " + ack);
                    }
                }
            } finally {
                lockAcks.unlock();
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }

        log("Timeout final à espera de ACK (" + tipoEsperado + ")");
        return false;
    }

    private void enviarACK(String tipo, String idMissao, String numero) {
        try {
            byte original = tipo.equals("UPDATE") ? 0x02 :
                            tipo.equals("FILE_PART") ? 0x03 :
                            (byte) 0x10;

            int id = Integer.parseInt(idMissao);
            int num = (numero == null) ? -1 : Integer.parseInt(numero);

            ByteBuffer bb = ByteBuffer.allocate(1 + 1 + 4 + 4);
            bb.put((byte) 0x04);
            bb.put(original);
            bb.putInt(id);
            bb.putInt(num);

            byte[] dados = bb.array();
            DatagramPacket ackPacket = new DatagramPacket(dados, dados.length, ip, porta);
            socket.send(ackPacket);

            log("ACK enviado (" + tipo + ", id=" + idMissao + ", num=" + numero + ")");

        } catch (IOException e) {
            e.printStackTrace();
            log("Erro ao enviar ACK: " + e.getMessage());
        }
    }

    private static final int TAM_LINHA_U = 16;

    private void processarUpdate(String idMissao, String numStr, String detalhesStr) {
        String filename = "updates_missao" + idMissao + ".log";
        int num = Integer.parseInt(numStr);
        int detalhes = Integer.parseInt(detalhesStr);

        log("Processar UPDATE: missão " + idMissao + " seq=" + num + " valor=" + detalhes);

        try (RandomAccessFile raf = new RandomAccessFile(filename, "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock(0, Long.MAX_VALUE, false)) {

            long pos = (long) (num - 1) * TAM_LINHA_U;

            if (raf.length() < pos) {
                raf.seek(raf.length());
                while (raf.length() < pos) {
                    raf.writeBytes(String.format("%-" + (TAM_LINHA_U - 1) + "s\n", "-1"));
                }
            }

            raf.seek(pos);
            String linha = String.format("%-" + (TAM_LINHA_U - 1) + "s\n", detalhes);
            raf.writeBytes(linha);

            log("UPDATE escrito na posição " + pos);

        } catch (IOException e) {
            System.err.println("Erro a escrever ficheiro: " + e.getMessage());
            log("Erro ao escrever UPDATE: " + e.getMessage());
        }
    }

    private static final int TAM_PARCELA = 1400;

    private void processarParteFicheiro(String idMissao, String numParteStr, String dadosStr) {
        String filename = "dados_missao" + idMissao + ".log";
        int numParte = Integer.parseInt(numParteStr);
        byte[] dados = dadosStr.getBytes(StandardCharsets.UTF_8);

        log("Processar FILE_PART: missão " + idMissao + ", parte " + numParte);

        try (RandomAccessFile raf = new RandomAccessFile(filename, "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock(0, Long.MAX_VALUE, false)) {

            long pos = (long) numParte * TAM_PARCELA;

            if (raf.length() < pos) {
                raf.seek(raf.length());
                long gap = pos - raf.length();
                for (long i = 0; i < gap; i++) {
                    raf.write((byte) '-');
                }
            }

            raf.seek(pos);
            raf.write(dados);

            log("FILE_PART escrita na posição " + pos);

        } catch (IOException e) {
            System.err.println("Erro a escrever ficheiro: " + e.getMessage());
            log("Erro ao escrever FILE_PART: " + e.getMessage());
        }
    }

}
