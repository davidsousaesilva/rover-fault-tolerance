package api.service;

import api.info.RoverInfo;
import com.google.gson.Gson;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;

public class RoverService {

    public static List<RoverInfo> readAllRovers() throws IOException {
        List<RoverInfo> result = new ArrayList<>();
        Gson gson = new Gson();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "telemetria_rover*.log")) {
            for (Path p : stream) {
                RoverInfo info = readLastTelemetry(p, gson);
                if (info != null)
                    result.add(info);
            }
        }

        return result;
    }

    private static RoverInfo readLastTelemetry(Path path, Gson gson) throws IOException {

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
                FileChannel channel = raf.getChannel();
                FileLock lock = channel.lock(0, Long.MAX_VALUE, true)) {

            long fileLength = raf.length();
            if (fileLength == 0)
                return null;

            long pointer = fileLength - 1;
            StringBuilder sb = new StringBuilder();

            while (pointer >= 0) {
                raf.seek(pointer);
                char c = (char) raf.read();
                if (c == '\n' && sb.length() > 0)
                    break;
                sb.insert(0, c);
                pointer--;
            }

            return gson.fromJson(sb.toString(), RoverInfo.class);
        }
    }
}
