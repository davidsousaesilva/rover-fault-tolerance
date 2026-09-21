package utils;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class Config {

    private final Properties props = new Properties();

    public Config(String caminhoFicheiro) throws IOException {
        try (FileReader reader = new FileReader(caminhoFicheiro)) {
            props.load(reader);
        }
    }

    public int getTimeoutMillis() {
        String val = props.getProperty("timeout_millis");
        if (val == null) throw new IllegalArgumentException("timeout_millis não definido no config.txt");
        return Integer.parseInt(val.trim());
    }

    public int getMaxRetries() {
        String val = props.getProperty("max_tentativas");
        if (val == null) throw new IllegalArgumentException("max_tentativas não definido no config.txt");
        return Integer.parseInt(val.trim());
    }
}
