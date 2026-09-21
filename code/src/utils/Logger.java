package utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Logger {

    //Lock para garantir que apenas uma thread escreve no ficheiro de cada vez.
    private static final Object lock = new Object();
    
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    //Criar a diretoria logs_persistentes/ caso esta ainda não exista
    static {
        new File("logs_persistentes/").mkdirs();
    }

    //Cria (ou abre) o ficheiro passado como parâmetro e escreve a mensagem passada como parâmetro com o timestamp
    public static void log(String filename, String msg) {
        synchronized (lock) {
            try (PrintWriter pw = new PrintWriter(new FileWriter("logs_persistentes/" + filename, true))) {
                pw.println("[" + LocalDateTime.now().format(FMT) + "] " + msg);
            } catch (IOException e) {
                System.err.println("Erro a escrever log: " + e.getMessage());
            }
        }
    }
}

