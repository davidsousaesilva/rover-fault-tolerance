package apps;

import java.io.IOException;
import udp.ClientML;
import tcp.ClientTS;
import utils.Config;
import model.Rover;

public class MainRover {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java MainRover <RoverId> <ServerIP> <Port>");
            return;
        }

        int roverId = Integer.parseInt(args[0]);
        String serverIP = args[1];
        int port = Integer.parseInt(args[2]);

        // Ler configuração
        Config config;
        try {
            config = new Config("config.txt");
        } catch (IOException e) {
            System.err.println("Erro a ler config.txt: " + e.getMessage());
            return;
        }

        Rover rover = new Rover(roverId);

        ClientTS clientTS = new ClientTS();
        ClientML clientML = new ClientML();

        // Start clientTS in a thread
        new Thread(() -> {
            try {
                clientTS.start(serverIP, port, rover);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        // Start clientML in a thread, passando Config
        new Thread(() -> {
            try {
                clientML.start(serverIP, port, rover, config);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
