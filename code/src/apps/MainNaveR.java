package apps;

import java.io.IOException;

import udp.ServerML;
import tcp.ServerTS;
import utils.Config;

public class MainNaveR {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java MainNaveR <IP> <Port>");
            return;
        }

        String ip = args[0];
        int port = Integer.parseInt(args[1]);

        //Ler configuração
        Config config;
        try {
            config = new Config("config.txt");
        } catch (IOException e) {
            System.err.println("Erro a ler config.txt: " + e.getMessage());
            return;
        }

        ServerTS serverTS = new ServerTS();
        ServerML serverML = new ServerML(config);

        // Start serverTS in a thread
        new Thread(() -> {
            try {
                serverTS.start(ip, port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Start serverML in a thread
        new Thread(() -> {
            try {
                serverML.start(ip, port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

    }
}
