package apps;

import api.info.RoverInfo;
import api.info.MissionInfo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;

public class MainGC extends Application {

    private static String ip;
    private static String port;

    private static final int MAP_SIZE = 500; // mapa lógico (-250..250)
    
    private final Gson gson = new Gson();
    private List<RoverInfo> rovers;
    private List<MissionInfo> missoes;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Mapa de Missões - Ground Control");

        // --- MAPA (Canvas)
        Canvas canvas = new Canvas(800, 800);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        StackPane mapaPane = new StackPane(canvas);
        mapaPane.setStyle("-fx-background-color: black;");

        canvas.widthProperty().bind(mapaPane.widthProperty());
        canvas.heightProperty().bind(mapaPane.heightProperty());

        // --- TABELAS À DIREITA
        TableView<RoverInfo> tabelaRovers = criarTabelaRovers();
        TableView<MissionInfo> tabelaMissoes = criarTabelaMissoes();

        VBox painelDireito = new VBox(15, tabelaRovers, tabelaMissoes);
        painelDireito.setPadding(new Insets(10));
        painelDireito.setPrefWidth(450);
        painelDireito.setStyle("-fx-background-color: #222;");

        VBox.setVgrow(tabelaRovers, Priority.ALWAYS);
        VBox.setVgrow(tabelaMissoes, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setCenter(mapaPane);
        root.setRight(painelDireito);

        Scene scene = new Scene(root, 1200, 800);
        stage.setScene(scene);
        stage.show();

        // Atualização periódica 
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            atualizarDados();
            desenhar(gc);
            atualizarTabelas(tabelaRovers, tabelaMissoes);
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void atualizarDados() {
        try {
            String roversJson = sendGet("http://" + ip + ":" + port + "/rovers");
            String missoesJson = sendGet("http://" + ip + ":" + port + "/missoes");

            Type roverListType = new TypeToken<List<RoverInfo>>() {}.getType();
            Type missionListType = new TypeToken<List<MissionInfo>>() {}.getType();

            rovers = gson.fromJson(roversJson, roverListType);
            missoes = gson.fromJson(missoesJson, missionListType);

        } catch (Exception e) {
            System.err.println("Erro ao atualizar dados: " + e.getMessage());
        }
    }

    /** Calcula escala dinâmica para caber todas as missões e rovers */
    private double getZoom(double canvasSize) {
        return canvasSize / MAP_SIZE;
    }

    /** Converte coordenadas do mapa (-MAP_SIZE/2..MAP_SIZE/2) para pixels do canvas */
    private double mapX(double x, double canvasWidth) {
        double zoom = getZoom(canvasWidth);
        return (x + MAP_SIZE / 2) * zoom;
    }

    private double mapY(double y, double canvasHeight) {
        double zoom = getZoom(canvasHeight);
        return (MAP_SIZE / 2 - y) * zoom;
    }

    private void desenhar(GraphicsContext gc) {

        double width = gc.getCanvas().getWidth();
        double height = gc.getCanvas().getHeight();

        // fundo
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, width, height);

        // grid leve (10x10)
        gc.setStroke(Color.color(1, 1, 1, 0.1));
        for (int i = 0; i <= 10; i++) {
            double posX = i * (width / 10.0);
            double posY = i * (height / 10.0);
            gc.strokeLine(posX, 0, posX, height);
            gc.strokeLine(0, posY, width, posY);
        }

        // desenhar missões
        if (missoes != null) {
            for (MissionInfo m : missoes) {

                double x1 = mapX(m.x1, width);
                double y1 = mapY(m.y1, height);
                double x2 = mapX(m.x2, width);
                double y2 = mapY(m.y2, height);

                double w = Math.abs(x2 - x1);
                double h = Math.abs(y2 - y1);
                double rx = Math.min(x1, x2);
                double ry = Math.min(y1, y2);

                Color color;
                if (m.progresso == -1)
                    color = Color.GRAY;
                else if (m.progresso == 0)
                    color = Color.ORANGE;
                else if (m.progresso > 0)
                    color = Color.YELLOW;
                else 
                    color = Color.GREEN;

                gc.setFill(color.deriveColor(1, 1, 1, 0.5));
                gc.fillRect(rx, ry, w, h);

                gc.setStroke(Color.WHITE);
                gc.strokeRect(rx, ry, w, h);

                gc.setFill(Color.WHITE);
                gc.fillText("M" + m.id, rx, ry);
            }
        }

        // desenhar rovers
        if (rovers != null) {
            for (RoverInfo r : rovers) {

                double px = mapX(r.deltaX, width);
                double py = mapY(r.deltaY, height);

                Color color = Color.WHEAT;

                if (r.state == 0)
                    color = Color.GREEN;
                else if (r.state == 1)
                    color = Color.YELLOW;
                else if (r.state == 2)
                    color = Color.CYAN;
                else if (r.state == 3)
                    color = Color.RED;

                gc.setFill(color);
                gc.fillOval(px - 5, py - 5, 10, 10);

                gc.setFill(Color.WHITE);
                gc.fillText("R" + r.id, px + 6, py - 6);
            }
        }
    }

    private TableView<RoverInfo> criarTabelaRovers() {
        TableView<RoverInfo> table = new TableView<>();
        table.setPrefHeight(250);
        table.setStyle("-fx-background-color: #333; -fx-text-fill: white;");

        TableColumn<RoverInfo, String> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(r -> new SimpleStringProperty("" + r.getValue().id));

        TableColumn<RoverInfo, String> colPos = new TableColumn<>("Posição");
        colPos.setCellValueFactory(
                r -> new SimpleStringProperty("(" + r.getValue().deltaX + ", " + r.getValue().deltaY + ")"));

        TableColumn<RoverInfo, String> colBat = new TableColumn<>("Bateria (%)");
        colBat.setCellValueFactory(r -> new SimpleStringProperty("" + r.getValue().battery));

        TableColumn<RoverInfo, String> colState = new TableColumn<>("Estado");
        colState.setCellValueFactory(r -> new SimpleStringProperty("" + estadoRoverDes(r.getValue().state)));

        colId.setPrefWidth(50);
        colPos.setPrefWidth(120);
        colBat.setPrefWidth(90);
        colState.setPrefWidth(120);
        table.getColumns().addAll(colId, colPos, colBat, colState);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private String estadoRoverDes(int state) {
        if (state == 0) 
            return "Pronto";
        else if (state == 1)
            return "A carregar";
        else if (state == 2)
            return "Em deslocação";
        else
            return "A executar missão";
    }

    private TableView<MissionInfo> criarTabelaMissoes() {
        TableView<MissionInfo> table = new TableView<>();
        table.setPrefHeight(450);
        table.setStyle("-fx-background-color: #333; -fx-text-fill: white;");

        TableColumn<MissionInfo, String> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(m -> new SimpleStringProperty("" + m.getValue().id));

        TableColumn<MissionInfo, String> colDesc = new TableColumn<>("Descrição");
        colDesc.setCellValueFactory(m -> new SimpleStringProperty(m.getValue().descricao));

        TableColumn<MissionInfo, String> colProg = new TableColumn<>("Estado");
        colProg.setCellValueFactory(m -> new SimpleStringProperty("" + progressoDesc(m.getValue().progresso)));

        TableColumn<MissionInfo, String> colPerc = new TableColumn<>("Progresso(%)");
        colPerc.setCellValueFactory(m -> new SimpleStringProperty(progressoPercentual(m.getValue())));

        colId.setPrefWidth(25);
        colDesc.setPrefWidth(200);
        colProg.setPrefWidth(90);
        colPerc.setPrefWidth(90);
        table.getColumns().addAll(colId, colDesc, colProg, colPerc);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private String progressoDesc(int progresso) {
        if (progresso == -2)
            return "Concluída";
        else if (progresso == -1)
            return "Não iniciada";
        else if (progresso == 0)
            return "Atribuída";
        else
            return "Em progresso";
    }

    private double areaMissao(MissionInfo m) {
        double comprimento = Math.abs(m.x2 - m.x1);
        double largura = Math.abs(m.y2 - m.y1);
        return comprimento * largura;
    }

    private String progressoPercentual(MissionInfo m) {
        if (m.progresso == -2)
            return "100%";
        if (m.progresso <= 0) return "0%";
        double areaTotal = areaMissao(m);
        double perc = (m.progresso / areaTotal) * 100.0;
        if (perc > 100) perc = 100;
        return String.format("%.1f%%", perc);
    }

    private void atualizarTabelas(TableView<RoverInfo> roverTable, TableView<MissionInfo> missionTable) {
        if (rovers != null) {
            roverTable.setItems(FXCollections.observableArrayList(rovers));
        }
        if (missoes != null) {
            missionTable.setItems(FXCollections.observableArrayList(missoes));
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

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java MainGC <IP> <PORTA>");
            return;
        }

        ip = args[0];
        port = args[1];

        launch(args);
    }
}
