import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

    //Corri xampp en el puerto 3308 porque es el que tenia por defecto MySQL Worbench
    //Si se quiere probar el proyecto en local correrlo de esta forma.
// javac -cp ".;lib/mysql-connector-j-9.3.0.jar" RecolectorPrecios.java
// java -cp ".;lib/mysql-connector-j-9.3.0.jar" RecolectorPrecios

public class RecolectorPrecios {
    private static final String URL_API = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,ripple,solana,tron,dogecoin,cardano,hyperliquid,bitcoin-cash,chainlink&vs_currencies=usd";
    //private static final String DB_URL = "jdbc:mysql://<YOUR_INSTANCE_CONNECTION_NAME>/criptomonedas_db?user=<YOUR_DB_USER>&password=<YOUR_DB_PASSWORD>";
    private static final String DB_URL = "jdbc:mysql://localhost:3308/criptomonedas_db?user=root&password=&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) {
        Timer temporizador = new Timer();
        temporizador.schedule(new TareaObtenerPrecios(), 0, 60_000); // Cada 60 segundos
    }

    static class TareaObtenerPrecios extends TimerTask {
        private final HttpClient cliente = HttpClient.newHttpClient();

        @Override
        public void run() {
            HttpRequest solicitud = HttpRequest.newBuilder()
                    .uri(URI.create(URL_API))
                    .GET()
                    .build();

            cliente.sendAsync(solicitud, HttpResponse.BodyHandlers.ofString())
                   .thenApply(HttpResponse::body)
                   .thenAccept(this::transformarYGuardarPrecios)
                   .exceptionally(error -> {
                       System.err.println("Error al obtener precios: " + error.getMessage());
                       return null;
                   });
        }

        private void transformarYGuardarPrecios(String jsonOriginal) {
            try {
                // Mapeo de IDs a nombres, símbolos y nombres de tabla
                Map<String, String[]> criptos = Map.of(
                    "bitcoin", new String[]{"Bitcoin", "BTC", "bitcoin"},
                    "ethereum", new String[]{"Ethereum", "ETH", "ethereum"},
                    "ripple", new String[]{"XRP", "XRP", "ripple"},
                    "solana", new String[]{"Solana", "SOL", "solana"},
                    "tron", new String[]{"TRON", "TRX", "tron"},
                    "dogecoin", new String[]{"Dogecoin", "DOGE", "dogecoin"},
                    "cardano", new String[]{"Cardano", "ADA", "cardano"},
                    "hyperliquid", new String[]{"Hyperliquid", "HYPE", "hyperliquid"},
                    "bitcoin-cash", new String[]{"Bitcoin Cash", "BCH", "bitcoin_cash"},
                    "chainlink", new String[]{"Chainlink", "LINK", "chainlink"}
                );

                // Procesamiento manual del JSON
                StringBuilder jsonTransformado = new StringBuilder("[");
                jsonOriginal = jsonOriginal.substring(1, jsonOriginal.length() - 1); // Elimina llaves externas {}

                String[] monedas = jsonOriginal.split(",(?=\\\"[a-z])"); // Divide por comas seguidas de comillas
                
                try (Connection conexion = DriverManager.getConnection(DB_URL)) {
                    for (int i = 0; i < monedas.length; i++) {
                        String moneda = monedas[i].trim();
                        String id = moneda.split(":")[0].replace("\"", "").trim();
                        String precioStr = moneda.split(":")[2].replace("}", "").trim();
                        double precio = Double.parseDouble(precioStr);

                        String[] info = criptos.get(id);
                        
                        // Insertar en la base de datos
                        String tabla = info[2];
                        String sql = "INSERT INTO " + tabla + " (precio) VALUES (?)";
                        
                        try (PreparedStatement pstmt = conexion.prepareStatement(sql)) {
                            pstmt.setDouble(1, precio);
                            pstmt.executeUpdate();
                            System.out.println("Datos insertados en tabla " + tabla + ": precio=" + precio);
                        }

                        // Construir JSON para archivo (opcional)
                        jsonTransformado.append(String.format(
                            "{\"name\":\"%s\",\"symbol\":\"%s\",\"price\":%.2f}",
                            info[0], info[1], precio
                        ));

                        if (i < monedas.length - 1) jsonTransformado.append(",");
                    }
                    jsonTransformado.append("]");

                    // Guardar en archivo (opcional)
                    try (FileWriter archivo = new FileWriter("precios.json", false)) {
                        archivo.write(jsonTransformado.toString());
                        System.out.println("Precios guardados en archivo: " + jsonTransformado);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error al procesar datos: " + e.getMessage());
            }
        }
    }
}