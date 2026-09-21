package apps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * MainGC é um cliente simples que periodicamente consulta os endpoints
 * /rovers e /missoes de um servidor SparkJava.
 */
public class MainGCTerminal {

    public static void main(String[] args) {

        // Verifica se os argumentos IP e PORTA foram fornecidos
        if (args.length < 2) {
            System.out.println("Uso: java MainGCTerminal <IP> <PORTA>");
            return;
        }

        String ip = args[0];
        String port = args[1];

        // URL base para as requisições
        String baseURL = "http://" + ip + ":" + port;

        // Gson com formatação bonita para JSON
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Loop infinito, faz requisições a cada 1 segundo
        while (true) {
            try {
                // 1️⃣ Requisição para /rovers
                String roversResponse = sendGet(baseURL + "/rovers");
                System.out.println("===== Rovers =====");
                // Formata JSON para ficar legível
                System.out.println("Rovers: " + roversResponse);
                // 2️⃣ Requisição para /missoes
                String missoesResponse = sendGet(baseURL + "/missoes");
                System.out.println("===== Missões =====");
                System.out.println("Missões: " + missoesResponse);

                // Espera 1 segundo antes do próximo ciclo
                Thread.sleep(1000);

            } catch (Exception e) {
                // Captura qualquer exceção (problemas de rede, JSON inválido, etc)
                System.err.println("Erro na requisição: " + e.getMessage());
                // Continua o loop mesmo após erro
            }
        }
    }

    /**
     * Faz uma requisição HTTP GET para a URL fornecida e retorna o corpo da
     * resposta.
     *
     * @param urlStr URL completa para a requisição
     * @return Conteúdo da resposta como String
     * @throws Exception Se ocorrer qualquer erro de rede ou I/O
     */
    private static String sendGet(String urlStr) throws Exception {
        URL url = new URI(urlStr).toURL();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET"); // Define método GET

        // Obtém o código HTTP da resposta
        int status = con.getResponseCode();

        // Escolhe fluxo de leitura: inputStream para sucesso, errorStream para erro
        BufferedReader in = new BufferedReader(new InputStreamReader(
                status >= 200 && status < 300 ? con.getInputStream() : con.getErrorStream()));

        // Lê todas as linhas da resposta
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }

        // Fecha o fluxo e a conexão
        in.close();
        con.disconnect();

        return content.toString();
    }
}
