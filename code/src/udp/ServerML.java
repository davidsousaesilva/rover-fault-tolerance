package udp;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import utils.*;

public class ServerML {

    private final Map<String, RoverHandlerML> rovers = new HashMap<>();
    private final List<String> todasMissoes = new ArrayList<>();
    private final Config config;

    public ServerML(Config config) {
        this.config = config;

        // agora as missões usam o formato:
        // MISSAO;<ID>;<x1>;<y1>;<x2>;<y2>;<descrição>;<maximum>;<interval>
        todasMissoes.add("MISSAO;1;-200;200;-170;230;Fotografia aérea de pontos de interesse;300;5");
        todasMissoes.add("MISSAO;2;-220;0;-180;30;Detecção de humidade do solo;1200;6");
        todasMissoes.add("MISSAO;3;-100;-150;-70;-120;Medição de temperatura do solo;1300;4");
        todasMissoes.add("MISSAO;4;0;100;30;130;Análise química de amostras de solo;400;5");
        todasMissoes.add("MISSAO;5;50;-50;90;-10;Medição do campo magnético;600;6");
        todasMissoes.add("MISSAO;6;100;150;140;180;Detecção de partículas de poeira;1200;5");
        todasMissoes.add("MISSAO;7;150;-200;180;-170;Análise atmosférica local;360;4");
        todasMissoes.add("MISSAO;8;-150;-100;-120;-70;Medição de radiação UV;710;5");
        todasMissoes.add("MISSAO;9;0;-200;30;-170;Fotografia em sequência;620;5");
    }

    public void start(String host, int porta) throws IOException {
        try (DatagramSocket socket = new DatagramSocket(porta, InetAddress.getByName(host))) {
            System.out.println("🛰️ Nave pronta e a escutar em " + host + ":" + porta);

            byte[] buffer = new byte[1413]; // (MAXIMO DE CABEÇALHO (13) + MAXIMO DADOS (1400)) < 1500 - (CABEÇALHOS IP E UDP)
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] dados = Arrays.copyOfRange(buffer, 0, packet.getLength());

                InetAddress ip = packet.getAddress();
                int portaCliente = packet.getPort();
                String idRover = ip.getHostAddress() + ":" + portaCliente;

                RoverHandlerML handler = rovers.get(idRover);
                if (handler == null) {
                    handler = new RoverHandlerML(ip, portaCliente, idRover, config);
                    // atribui 3 missões iniciais a cada handler (se houver)
                    for (int i = 0; i < 3 && !todasMissoes.isEmpty(); i++) {
                        String missaoCompleta = todasMissoes.remove(0);
                        // novo formato: MISSAO;<ID>;...
                        String[] partes = missaoCompleta.split(";", 3);
                        String idMissao = partes[1]; // agora o ID é o segundo campo
                        handler.addMissao(idMissao, missaoCompleta);
                    }
                    rovers.put(idRover, handler);
                    new Thread(handler, "RoverHandler-" + idRover).start();
                }

                // DETEÇÃO: se tiver um tipo binário (primeiro byte) então decodifica
                if (dados.length > 0 && isBinaryType(dados[0])) {
                    String decoded = decodeBinaryPacketToString(dados);
                    if (decoded != null) {
                        if (decoded.startsWith("ACK;")) {
                            handler.adicionarACK(decoded);
                        } else {
                            handler.adicionarPacote(decoded);
                        }
                    } else {
                        System.out.println("⚠️ Pacote binário inválido recebido de " + idRover);
                    }
                }
            }
        }
    }
    
    private boolean isBinaryType(byte b) {
        return b == 0x01 || b == 0x02 || b == 0x03 || b == 0x04 || b == 0x0;
    }

    /**
     * Decodifica um array de bytes para uma string que o RoverHandlerML espera.
     * 
     *
     * Formatos:
     * 0x0 -> PEDIR_MISSAO
     * 0x01 -> MISSAO [server->client]
     * 0x02 -> UPDATE [client->server]
     * 0x03 -> FILE_PART [client->server]
     * 0x04 -> ACK [cliente->server ou server->cliente dependendo do fluxo]
     */
    private String decodeBinaryPacketToString(byte[] dados) {
        if (dados == null || dados.length == 0) return null;
        try {
            ByteBuffer bb = ByteBuffer.wrap(dados);
            byte tipo = bb.get();
            switch (tipo) {
                case 0x0: // PEDIR_MISSAO (apenas 1 byte)
                    return "PEDIR_MISSAO";

                case 0x02: { // UPDATE
                    int id = bb.getInt();
                    int seq = bb.getInt();
                    int progress = bb.getInt();
                    return String.format("UPDATE;%d;%d;%d", id, seq, progress);
                }
                case 0x03: { // FILE_PART
                    int id = bb.getInt();
                    int parte = bb.getInt();
                    int dataLen = bb.getInt();
                    if (dataLen < 0) {
                        System.out.println("⚠️ FILE_PART inválido: dataLen negativo (" + dataLen + ").");
                        return null;
                    }
                    if (dataLen > bb.remaining()) {
                        System.out.println("⚠️ FILE_PART inválido: dataLen (" + dataLen + ") maior que remaining (" + bb.remaining() + ").");
                        return null;
                    }
                    byte[] pb = new byte[dataLen];
                    if (dataLen > 0) bb.get(pb);
                    // confirmaste que payload é ASCII 'A' => UTF-8 é seguro aqui
                    String payload = new String(pb, StandardCharsets.UTF_8);
                    return String.format("FILE_PART;%d;%d;%s", id, parte, payload);
                }
                case 0x04: { // ACK
                    byte origType = bb.get(); //ServerML recebe apenas ACK de missão
                    int id = bb.getInt();
                    String tipoStr = "MISSAO";
                    return String.format("ACK;%s;%d", tipoStr, id);
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}

