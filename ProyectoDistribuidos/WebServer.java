import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.*;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//  Para correr el proyecto de manera local
// javac -cp ".;lib/*" WebServer.java
// java -cp ".;lib/*" WebServer

//gsutil cp gs://fontend-bucket/index.html  /home/master117mm

public class WebServer {
    private static final String STATUS_ENDPOINT = "/status";
    private static final String PRECIOS_ENDPOINT = "/precios";
    //private static final String DB_URL = "jdbc:mysql://localhost:3308/criptomonedas_db?user=root&password=&useSSL=false&serverTimezone=UTC";
    private static final String DB_URL = "jdbc:mysql://10.23.176.2:3306/criptomonedas_db?user=root&password=root&useSSL=true";


    private final int port;
    private HttpServer server;

    public static void main(String[] args) {
        int serverPort = 8080;
        if (args.length == 1) {
            serverPort = Integer.parseInt(args[0]);
        }

        WebServer webServer = new WebServer(serverPort);
        webServer.startServer();

        System.out.println("Servidor escuchando en el puerto " + serverPort);
    }

    public WebServer(int port) {
        this.port = port;
    }

    public void startServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        HttpContext statusContext = server.createContext(STATUS_ENDPOINT);
        HttpContext preciosContext = server.createContext(PRECIOS_ENDPOINT);

        statusContext.setHandler(this::handleStatusCheckRequest);
        preciosContext.setHandler(this::handlePreciosRequest);

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    private void handleStatusCheckRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.close();
            return;
        }

        String responseMessage = "El servidor está vivo\n";
        sendResponse(responseMessage.getBytes(), exchange, "text/plain");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    /// Obtener precios de la base de datos
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handlePreciosRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.close();
            return;
        }

        try {
            List<CryptoPrice> precios = obtenerUltimosPreciosDeBD();
            String jsonResponse = generarJsonManual(precios);
            sendResponse(jsonResponse.getBytes(), exchange, "application/json");
        } catch (SQLException e) {
            e.printStackTrace();
            String error = "{\"error\": \"Error al consultar la base de datos\"}";
            sendResponse(error.getBytes(), exchange, "application/json");
        }
    }

    private String generarJsonManual(List<CryptoPrice> precios) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < precios.size(); i++) {
            CryptoPrice precio = precios.get(i);
            json.append(String.format(
                    "{\"name\":\"%s\",\"symbol\":\"%s\",\"price\":%.2f}",
                    escapeJson(precio.name),
                    escapeJson(precio.symbol),
                    precio.price));
            if (i < precios.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    private String escapeJson(String input) {
        return input.replace("\"", "\\\"")
                .replace("\\", "\\\\")
                .replace("/", "\\/")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private List<CryptoPrice> obtenerUltimosPreciosDeBD() throws SQLException {
        List<CryptoPrice> precios = new ArrayList<>();

        try {
            // 👇 ESTA LÍNEA ES OBLIGATORIA si el driver no se autoregistra
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new SQLException("Driver JDBC no encontrado");
        }

        // Mapeo de tablas
        Map<String, String[]> criptos = Map.of(
                "bitcoin", new String[] { "Bitcoin", "BTC" },
                "ethereum", new String[] { "Ethereum", "ETH" },
                "ripple", new String[] { "XRP", "XRP" },
                "solana", new String[] { "Solana", "SOL" },
                "tron", new String[] { "TRON", "TRX" },
                "dogecoin", new String[] { "Dogecoin", "DOGE" },
                "cardano", new String[] { "Cardano", "ADA" },
                "hyperliquid", new String[] { "Hyperliquid", "HYPE" },
                "bitcoin_cash", new String[] { "Bitcoin Cash", "BCH" },
                "chainlink", new String[] { "Chainlink", "LINK" });

        try (Connection conexion = DriverManager.getConnection(DB_URL)) {
            for (Map.Entry<String, String[]> entry : criptos.entrySet()) {
                String tabla = entry.getKey();
                String nombre = entry.getValue()[0];
                String simbolo = entry.getValue()[1];

                String sql = "SELECT precio FROM " + tabla + " ORDER BY fecha_registro DESC LIMIT 1";

                try (PreparedStatement pstmt = conexion.prepareStatement(sql);
                        ResultSet rs = pstmt.executeQuery()) {

                    if (rs.next()) {
                        double precio = rs.getDouble("precio");
                        precios.add(new CryptoPrice(nombre, simbolo, precio));
                    }
                }
            }
        }

        return precios;
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    /// Metodos genericos
    //////////////////////////////////////////////////////////////////////////////////////////

    private void sendResponse(byte[] responseBytes, HttpExchange exchange, String contentType) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", contentType);
        headers.add("Access-Control-Allow-Origin", "*");

        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
        exchange.close();
    }

    // Clase interna para representar los datos de precios
    private static class CryptoPrice {
        final String name;
        final String symbol;
        final double price;

        public CryptoPrice(String name, String symbol, double price) {
            this.name = name;
            this.symbol = symbol;
            this.price = price;
        }
    }
}