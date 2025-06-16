import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class RecolectorPrecios {
    private static final String URL_API = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,tether,binancecoin,solana,usd-coin,ripple,dogecoin,cardano,avalanche&vs_currencies=usd";

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
                // Mapeo de IDs a nombres y símbolos
                Map<String, String[]> criptos = Map.of(
                    "bitcoin", new String[]{"Bitcoin", "BTC"},
                    "ethereum", new String[]{"Ethereum", "ETH"},
                    "tether", new String[]{"Tether", "USDT"},
                    "binancecoin", new String[]{"BNB", "BNB"},
                    "solana", new String[]{"Solana", "SOL"},
                    "usd-coin", new String[]{"USDC", "USDC"},
                    "ripple", new String[]{"XRP", "XRP"},
                    "dogecoin", new String[]{"Dogecoin", "DOGE"},
                    "cardano", new String[]{"Cardano", "ADA"},
                    "avalanche", new String[]{"Avalanche", "AVAX"}
                );

                // Procesamiento manual del JSON
                StringBuilder jsonTransformado = new StringBuilder("[");
                jsonOriginal = jsonOriginal.substring(1, jsonOriginal.length() - 1); // Elimina llaves externas {}

                String[] monedas = jsonOriginal.split(",(?=\\\"[a-z])"); // Divide por comas seguidas de comillas
                for (int i = 0; i < monedas.length; i++) {
                    String moneda = monedas[i].trim();
                    String id = moneda.split(":")[0].replace("\"", "").trim();
                    String precioStr = moneda.split(":")[2].replace("}", "").trim();
                    double precio = Double.parseDouble(precioStr);

                    String[] info = criptos.get(id);
                    jsonTransformado.append(String.format(
                        "{\"name\":\"%s\",\"symbol\":\"%s\",\"price\":%.2f}",
                        info[0], info[1], precio
                    ));

                    if (i < monedas.length - 1) jsonTransformado.append(",");
                }
                jsonTransformado.append("]");

                // Guardar en archivo
                try (FileWriter archivo = new FileWriter("precios.json", false)) {
                    archivo.write(jsonTransformado.toString());
                    System.out.println("Precios guardados: " + jsonTransformado);
                }
            } catch (Exception e) {
                System.err.println("Error al transformar JSON: " + e.getMessage());
            }
        }
    }
}