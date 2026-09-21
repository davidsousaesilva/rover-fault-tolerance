package apps;

import static spark.Spark.*; // SparkJava
import com.google.gson.Gson; // Gson

import java.io.IOException;
import java.util.ArrayList;

import api.service.RoverService;
import api.info.MissionInfo;
import api.info.RoverInfo;
import api.service.MissionService;

public class MainNaveGC {

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.out.println("Uso: java MainNaveGC <IP> <PORTA>");
            return;
        }

        String ip = args[0];
        int portNumber = Integer.parseInt(args[1]);

        ipAddress(ip);
        port(portNumber);

        Gson gson = new Gson();

        get("/rovers", (req, res) -> {
            res.type("application/json");
            try {
                return gson.toJson(RoverService.readAllRovers());
            } catch (IOException e) {
                return gson.toJson(new ArrayList<RoverInfo>());
            }
        });

        get("/missoes", (req, res) -> {
            res.type("application/json");
            try {
                return gson.toJson(MissionService.readMissions());
            } catch (IOException e) {
                return gson.toJson(new ArrayList<MissionInfo>());
            }
        });

        System.out.println("Servidor SparkJava a rodar em http://" + ip + ":" + portNumber);
    }
}
