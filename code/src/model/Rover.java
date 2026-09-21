package model;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class Rover {
    private final int id; // argumento do programa MainRover
    private int deltaX; // metros relativo a NM (aleatório)
    private int deltaY; // metros relativo a NM (aleatório)
    private final int deltaZ; // metros relativo a NM (fixo, atua numa planície)
    private BigDecimal battery; // percentagem (aleatório)
    private int state; // 0 -> disponível, 1 -> a carregar, 2 -> em deslocação, 3 -> a executar missão
    private int progress; // usada para registar progresso de uma missão (número de m2 varridos)
    private ReentrantLock lock; // lock para bloquear o acesso a apenas 1 thread de cada vez

    public Rover(int id) {
        Random random = new Random();
        BigDecimal randomFloat = new BigDecimal(random.nextFloat());
        BigDecimal minBattery = new BigDecimal("5.00");
        BigDecimal maxBattery = new BigDecimal("100.00");

        this.id = id;
        this.deltaX = random.nextInt(501) - 250; // -250 a 250 m
        this.deltaY = random.nextInt(501) - 250; // -250 a 250 m
        this.deltaZ = -5000000; // 500 km de distancia da nave
        this.battery = minBattery.add(randomFloat.multiply(maxBattery.subtract(minBattery)))
                .setScale(2, RoundingMode.HALF_UP); // 5.00 a 100.00
        this.state = 0;
        this.progress = -1;
        this.lock = new ReentrantLock();
    }

    public String getTelemetry() {
        this.lock.lock();/*
                          * método de READ: necessário lock para impedir o getTelemetry() de ver estado
                          * inconsistente e valores desatualizados
                          */
        try {
            // Limites válidos
            final int MIN_X = -250, MAX_X = 250;
            final int MIN_Y = -250, MAX_Y = 250;
            final int FIXED_Z = -5000000;
            final BigDecimal MIN_BATTERY = new BigDecimal("0.00");
            final BigDecimal MAX_BATTERY = new BigDecimal("100.00");

            // Verificações
            boolean posicaoInvalida = (deltaX < MIN_X || deltaX > MAX_X ||
                    deltaY < MIN_Y || deltaY > MAX_Y ||
                    deltaZ != FIXED_Z);
            boolean bateriaInvalida = (battery.compareTo(MIN_BATTERY) < 0 ||
                    battery.compareTo(MAX_BATTERY) > 0);
            boolean estadoInvalido = (state < 0 || state > 3);

            int erroPosicao = 0;
            int erroBateria = 0;
            int erroEstado = 0;

            if (posicaoInvalida)
                erroPosicao = 1;
            if (bateriaInvalida)
                erroBateria = 1;
            if (estadoInvalido)
                erroEstado = 1;

            return String.format(
                    "{\"id\":%d,\"deltaX\":%d,\"deltaY\":%d,\"deltaZ\":%d,\"battery\":%d,\"state\":%d,\"erroPosicao\":%d,\"erroBateria\":%d,\"erroEstado\":%d}",
                    id, deltaX, deltaY, deltaZ, battery.intValue(), state,
                    erroPosicao, erroBateria, erroEstado);

        } finally {
            this.lock.unlock();
        }
    }

    // um rover so "perde" bateria ao mover-se para a missão
    // durante a missão é autosustentável
    // no pior caso anda 1000m a deslocar-se para a missão (varX + varY = 1000m)
    // | 1m -> 0.02% | 50m -> 1% | 1000m -> 20% |
    public boolean isReadyforMission() { // THREAD PRICIPAL (lock para ler não é necessário)
        return this.state == 0 &&
                this.battery.compareTo(new BigDecimal("25.00")) >= 0; // >= 25%
    }

    // carrega a bateria
    public void charge() throws InterruptedException { // THREAD PRINCIPAL (lock para ler não é necessário)
        BigDecimal increment = new BigDecimal("0.02");
        BigDecimal maxBattery = new BigDecimal("100.00");

        this.lock.lock(); // aqui usar volatile era suficiente, mas assim é coerente
        try {
            this.state = 1;
        } finally {
            this.lock.unlock();
        }

        while (true) {
            this.lock.lock(); /*
                               * método de WRITE: necessário lock para impedir o getTelemetry() de ver estado
                               * inconsistente (por exemplo, bateria a 100 e a carregar ainda) e valores
                               * desatualizados
                               */
            try {
                if (this.battery.compareTo(maxBattery) >= 0) {
                    this.battery = maxBattery; // garante não ultrapassar 100%
                    this.state = 0;
                    break; // terminou o carregamento
                }

                this.battery = this.battery.add(increment) // carrega dos 0 aos 100 em 50seg
                        .setScale(2, RoundingMode.HALF_UP);

            } finally {
                this.lock.unlock();
            }

            Thread.sleep(10); // sleep fora do lock, não bloqueia getTelemetry()
        }
    }

    // desloca-se 1m e perde bateria
    public boolean move(boolean isX, boolean direction) { // THREAD PRINCIPAL (lock para ler não é necessário)
        this.lock.lock(); /*
                           * método de WRITE: necessário lock para impedir o getTelemetry() de ver estado
                           * inconsistente (por exemplo ver posição mudada e bateria igual) e valores
                           * desatualizados
                           */
        try {
            // Atualiza o delta correspondente
            if (isX) {
                this.deltaX += direction ? 1 : -1;
            } else {
                this.deltaY += direction ? 1 : -1;
            }

            // Consumo de bateria (0.02% por metro → 1% a cada 50 m)
            this.battery = this.battery.subtract(new BigDecimal("0.02"))
                    .setScale(2, RoundingMode.HALF_UP);

            // Correu mal (nunca deverá acontecer)
            if (this.battery.compareTo(new BigDecimal("5.00")) <= 0) {
                this.battery = new BigDecimal("5.00");
                return false; // limite mínimo atingido
            }

            // Correu bem
            return true;

        } finally {
            this.lock.unlock();
        }
    }

    // move se para o vértice mais perto da área da missão
    public void moveTarget(int x1, int y1, int x2, int y2) throws InterruptedException { // THREAD PRICIPAL (lock para
                                                                                         // ler não é necessário)
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        this.lock.lock(); // aqui usar volatile era suficiente, mas assim é coerente
        try {
            this.state = 2; // em deslocação
            this.progress = 0; // missão "iniciada"
        } finally {
            this.lock.unlock();
        }

        int currentX = this.deltaX;
        int currentY = this.deltaY;

        // Calcula a distância a cada vértice do retângulo
        int[][] corners = {
                { minX, minY },
                { minX, maxY },
                { maxX, minY },
                { maxX, maxY }
        };

        int nearestX = corners[0][0];
        int nearestY = corners[0][1];
        int minDistance = Math.abs(currentX - nearestX) + Math.abs(currentY - nearestY);

        for (int i = 1; i < 4; i++) {
            int distance = Math.abs(currentX - corners[i][0]) + Math.abs(currentY - corners[i][1]);
            if (distance < minDistance) {
                minDistance = distance;
                nearestX = corners[i][0];
                nearestY = corners[i][1];
            }
        }

        /*
         * método de WRITE: nos ciclos não necessita de lock pois chama um método que já
         * se preocupa com isso
         */

        // --- Move-se até ao canto mais próximo ---
        while (this.deltaX != nearestX) {
            boolean direction = this.deltaX < nearestX;
            this.move(true, direction);
            Thread.sleep(100); // 10m/2
        }

        while (this.deltaY != nearestY) {
            boolean direction = this.deltaY < nearestY;
            this.move(false, direction);
            Thread.sleep(100); // 10m/2
        }

    }

    // simula uma missão (zig-zag)
    public void mission(int x1, int y1, int x2, int y2, int time) throws InterruptedException { // THREAD PRINCIPAL
                                                                                                // (lock para ler não é
                                                                                                // necessário)
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        long startTime = System.currentTimeMillis();
        long maxDuration = time * 1000L; // converter segundos para milissegundos

        this.lock.lock();
        try {
            this.state = 3; // a executar missão
        } finally {
            this.lock.unlock();
        }

        // Determina o ponto de partida
        int currentX = this.deltaX;
        int currentY = this.deltaY;

        boolean leftToRight;
        boolean goingUp;

        // Define a direção inicial com base no vértice em que o rover está
        if (currentY == minY) {
            goingUp = true; // está na parte de baixo da área
        } else {
            goingUp = false; // está na parte de cima da área
        }

        if (currentX == minX) {
            leftToRight = true; // começa à esquerda
        } else {
            leftToRight = false; // começa à direita
        }

        // --- Ciclo de exploração em zig-zag ---
        int y = currentY;

        while (y >= minY && y <= maxY) {

            // verifica tempo a cada linha
            if (System.currentTimeMillis() - startTime >= maxDuration) {
                break;
            }

            int targetX = leftToRight ? maxX : minX;

            // percorre horizontalmente
            while (true) {
                if (System.currentTimeMillis() - startTime >= maxDuration) {
                    break;
                }

                this.lock.lock();
                try {
                    if (this.deltaX == targetX) {
                        break; // linha concluída
                    }

                    // movimenta 1 metro (sem consumo)
                    this.deltaX += (this.deltaX < targetX) ? 1 : -1;
                    this.progress++; // varreu mais um m2

                } finally {
                    this.lock.unlock();
                }

                Thread.sleep(1000); // simula deslocamento horizontal (1 m/s)
            }

            // muda de linha (verticalmente)
            this.lock.lock(); // aqui usar volatile era suficiente, mas assim é coerente
            try {
                if (goingUp && y < maxY) {
                    this.deltaY += 1; // sobe 1 metro
                    y++;
                } else if (!goingUp && y > minY) {
                    this.deltaY -= 1; // desce 1 metro
                    y--;
                } else {
                    break; // chegou ao limite da área
                }
            } finally {
                this.lock.unlock();
            }

            Thread.sleep(1000); // simula deslocamento vertical (1 m/s)

            leftToRight = !leftToRight; // inverte direção para zig-zag
        }

        // missão concluída
        this.lock.lock();
        try {
            this.state = 0; // disponível
            this.progress = -1; // progresso volta a ser inválido
        } finally {
            this.lock.unlock();
        }
    }

    public int getProgress() { // THREAD SECUNDÁRIA 2 (reports ML, udp)
        this.lock.lock(); /*
                           * método de READ: necessário lock para impedir o getStateAndProgress() de ver
                           * ler valores desatualizados
                           */
        try {
            return this.progress;
        } finally {
            this.lock.unlock();
        }
    }

    public int getId() {
        return this.id;
    }

}
