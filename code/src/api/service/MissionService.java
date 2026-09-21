package api.service;

import api.info.MissionInfo;
import com.google.gson.Gson;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;

public class MissionService {

    public static List<MissionInfo> readMissions() throws IOException {

        List<MissionInfo> missions = new ArrayList<>();
        Path missoesPath = Paths.get("missoes.txt");

        try (RandomAccessFile raf = new RandomAccessFile(missoesPath.toFile(), "r");
                FileChannel channel = raf.getChannel();
                FileLock lock = channel.lock(0, Long.MAX_VALUE, true)) {

            List<String> lines = Files.readAllLines(missoesPath);
            Gson gson = new Gson();

            for (String line : lines) {
                MissionInfo m = gson.fromJson(line, MissionInfo.class);

                Path updatePath = Paths.get("updates_missao" + m.id + ".log");
                Path dadosPath = Paths.get("dados_missao" + m.id + ".log");

                if (Files.exists(dadosPath)) {
                    m.progresso = -2;
                } else if (Files.exists(updatePath)) {
                    try (RandomAccessFile updateFile = new RandomAccessFile(updatePath.toFile(), "r");
                            FileChannel updateChannel = updateFile.getChannel();
                            FileLock updateLock = updateChannel.lock(0, Long.MAX_VALUE, true)) {

                        long fileLength = updateFile.length();

                        if (fileLength == 0)
                            m.progresso = -1;
                        else {
                            long pointer = fileLength - 1;
                            StringBuilder sb = new StringBuilder();

                            while (pointer >= 0) {
                                updateFile.seek(pointer);
                                char c = (char) updateFile.read();
                                if (c == '\n' && sb.length() > 0)
                                    break;
                                sb.insert(0, c);
                                pointer--;
                            }

                            m.progresso = Integer.parseInt(sb.toString().trim());
                        }
                    }
                } else
                    m.progresso = -1;

                missions.add(m);
            }
        }
        return missions;
    }
}
