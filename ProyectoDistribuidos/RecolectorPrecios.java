import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Timer;
import java.util.TimerTask;

    //Corri xampp en el puerto 3308 porque es el que tenia por defecto MySQL Worbench
    //Si se quiere probar el proyecto en local correrlo de esta forma.
// javac -cp ".;lib/*" RecolectorPrecios.java
// java -cp ".;lib/*" RecolectorPrecios

public class RecolectorPrecios {
    private static final String URL_API = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,ripple,solana,tron,dogecoin,cardano,hyperliquid,bitcoin-cash,chainlink&vs_currencies=usd";
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
                // Nombres de tabla

                String[] criptos = {"bitcoin", "ethereum", "ripple", "solana", "tron", 
                        "dogecoin", "cardano", "hyperliquid", "bitcoin_cash", "chainlink"};

                jsonOriginal = jsonOriginal.substring(1, jsonOriginal.length() - 1); // Elimina llaves externas {}
                String[] monedas = jsonOriginal.split(",(?=\\\"[a-z])"); // Divide por comas seguidas de comillas
                
                System.out.println("\n");

                try (Connection conexion = DriverManager.getConnection(DB_URL)) {
                    for (int i = 0; i < monedas.length; i++) {
                        String moneda = monedas[i].trim();
                        String precioStr = moneda.split(":")[2].replace("}", "").trim();
                        double precio = Double.parseDouble(precioStr);
                        
                        // Insertar en la base de datos
                        String tabla = criptos[i];
                        String sql = "INSERT INTO " + tabla + " (precio) VALUES (?)";
                        
                        try (PreparedStatement pstmt = conexion.prepareStatement(sql)) {
                            pstmt.setDouble(1, precio);
                            pstmt.executeUpdate();
                            System.out.println(tabla + ": precio=" + precio);
                        }

                    }
                }
            } catch (Exception e) {
                System.err.println("Error al procesar datos: " + e.getMessage());
            }
        }
    }
}