import com.sun.net.httpserver.*;
import org.knowm.xchart.*;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.Styler;


import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;

// Ejemplo de peticion del punto 1, una moneda graficada cierta cantidad de horas
//http://localhost:8081/grafico?crypto=bitcoin&horas=1

// Ejemplo de punto 4, comparacion de monedas con hora de inicio y fin
// Solo cambien la fecha porque solo permite mostrar 24 horas hacia atras
// http://localhost:8081/graficoCompara?cryptos=ethereum,ripple,solana,tron,dogecoin,cardano,hyperliquid,bitcoin_cash,chainlink&inicio=2025-06-27T15:00&fin=2025-06-27T16:00

public class GraficoService {

    // private static final String DB_URL =
    // "jdbc:mysql://localhost:3306/criptomonedas_db?user=root&password=&useSSL=false&serverTimezone=UTC";
    private static final String DB_URL = "jdbc:mysql://localhost:3308/criptomonedas_db?user=root&password=&useSSL=false&serverTimezone=UTC";
    private static final int PORT = 8081;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/grafico", GraficoService::handleGraficoRequest);
        server.createContext("/graficoCompara", GraficoService::handleGraficoComparaRequest);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("GraficoService escuchando en http://localhost:" + PORT + "/grafico");
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Punto 1, grafico individual
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void handleGraficoRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String crypto = params.getOrDefault("crypto", "").toLowerCase();
        int horas = Integer.parseInt(params.getOrDefault("horas", "3"));

        if (crypto.isEmpty() || horas <= 0 || horas > 24) {
            String error = "Parámetros inválidos. Usa ?crypto=bitcoin&horas=3";
            exchange.sendResponseHeaders(400, error.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(error.getBytes());
            }
            return;
        }

        try {
            List<Registro> registros = obtenerDatos(crypto, horas);
            if (registros.isEmpty()) {
                exchange.sendResponseHeaders(204, -1); // No Content
                return;
            }

            byte[] imageBytes = generarGraficoPNG(crypto, registros);
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, imageBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(imageBytes);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            String error = "Error al consultar datos de la base";
            exchange.sendResponseHeaders(500, error.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(error.getBytes());
            }
        }
    }

    private static List<Registro> obtenerDatos(String crypto, int horas) throws SQLException {
        List<Registro> datos = new ArrayList<>();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver JDBC no encontrado");
        }

        try (Connection conn = DriverManager.getConnection(DB_URL)) {

            if (horas == 1) {
                // Última hora exacta desde este momento (incluye minutos/segundos)
                String query = String.format("""
                            SELECT fecha_registro, precio FROM %s
                            WHERE fecha_registro >= NOW() - INTERVAL 1 HOUR
                            ORDER BY fecha_registro
                        """, crypto);

                try (PreparedStatement stmt = conn.prepareStatement(query);
                        ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        LocalDateTime fecha = rs.getTimestamp("fecha_registro").toLocalDateTime();
                        double precio = rs.getDouble("precio");
                        datos.add(new Registro(fecha, precio));
                    }
                }

            } else {
                // Para cada hora, obtener el último registro de esa hora
                String query = String.format("""
                            SELECT
                                DATE_FORMAT(fecha_registro, '%%Y-%%m-%%d %%H:00:00') AS hora,
                                MAX(fecha_registro) AS ultima_fecha,
                                SUBSTRING_INDEX(GROUP_CONCAT(precio ORDER BY fecha_registro DESC), ',', 1) AS precio
                            FROM %s
                            WHERE fecha_registro >= NOW() - INTERVAL ? HOUR
                            GROUP BY hora
                            ORDER BY hora ASC
                        """, crypto);

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, horas);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        String fechaStr = rs.getString("ultima_fecha");
                        double precio = rs.getDouble("precio");
                        LocalDateTime fecha = LocalDateTime.parse(fechaStr.replace(" ", "T"));
                        datos.add(new Registro(fecha, precio));
                    }
                }
            }

        }

        return datos;
    }

    private static byte[] generarGraficoPNG(String crypto, List<Registro> registros) throws IOException {
        List<java.util.Date> fechas = new ArrayList<>();
        List<Double> precios = new ArrayList<>();

        for (Registro r : registros) {
            fechas.add(java.util.Date.from(r.fecha.atZone(ZoneId.systemDefault()).toInstant()));
            precios.add(r.precio);
        }

        XYChart chart = new XYChartBuilder()
                .width(800).height(450)
                .title("Variación de precio - " + crypto.toUpperCase())
                .xAxisTitle("Hora")
                .yAxisTitle("USD")
                .build();

        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Area);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(new Color(245, 245, 245));
        chart.getStyler().setPlotGridLinesColor(new Color(220, 220, 220));
        chart.getStyler().setChartTitleFont(new Font("SansSerif", Font.BOLD, 18));
        chart.getStyler().setAxisTitleFont(new Font("SansSerif", Font.PLAIN, 14));
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setMarkerSize(5);
        chart.getStyler().setDecimalPattern("#,###.00");
        chart.getStyler().setXAxisTickMarkSpacingHint(75);
        chart.getStyler().setDatePattern("HH:mm");

        XYSeries series = chart.addSeries("Precio", fechas, precios);

        if (fechas.size() == 1) {
            series.setMarker(SeriesMarkers.CIRCLE);
            series.setLineStyle(SeriesLines.SOLID);
        } else {
            series.setMarker(new None());
        }

        series.setLineColor(new Color(52, 152, 219));
        series.setFillColor(new Color(52, 152, 219, 80));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, out, BitmapEncoder.BitmapFormat.PNG);
        return out.toByteArray();
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Graficas de comparacion
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    private static final int MAX_HORAS_CONSULTA = 24;
    
    // Colores para las diferentes criptomonedas
    private static final Map<String, Color> CRYPTO_COLORS = Map.of(
        "bitcoin", new Color(247, 147, 26),     // Naranja vibrante
        "ethereum", new Color(98, 126, 0),      // Verde caca
        "ripple", new Color(0, 162, 232),       // Azul cielo 
        "solana", new Color(191, 191, 191),     // Gris plateado
        "tron", new Color(0, 255, 0),           // Verde 
        "dogecoin", new Color(0, 0, 0),         // Negro
        "cardano", new Color(255, 204, 0),      // Amarrillo
        "hyperliquid", new Color(0, 0, 255),    // Azul
        "bitcoin_cash", new Color(103, 58, 183),// Púrpura intenso
        "chainlink", new Color(255, 0, 0)      // Rojo vivo
    );

    private static void handleGraficoComparaRequest(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String cryptosParam = params.getOrDefault("cryptos", "").toLowerCase();
            
            // Obtener el rango de tiempo permitido (últimas 24 horas)
            LocalDateTime ahora = LocalDateTime.now();
            LocalDateTime maxInicio = ahora.minusHours(MAX_HORAS_CONSULTA);
            
            // Parsear fechas o usar valores por defecto (últimas 3 horas)
            LocalDateTime inicio;
            LocalDateTime fin;
            
            try {
                String inicioStr = params.getOrDefault("inicio", "");
                String finStr = params.getOrDefault("fin", "");
                
                if (inicioStr.isEmpty() || finStr.isEmpty()) {
                    // Valores por defecto: últimas 3 horas
                    fin = ahora;
                    inicio = fin.minusHours(3);
                } else {
                    inicio = LocalDateTime.parse(inicioStr.replace(" ", "T"));
                    fin = LocalDateTime.parse(finStr.replace(" ", "T"));
                    
                    // Validar que las fechas estén dentro del rango permitido
                    if (inicio.isBefore(maxInicio)) {
                        String error = "La fecha de inicio no puede ser anterior a " + maxInicio;
                        enviarError(exchange, 400, error);
                        return;
                    }
                }
                
                if (inicio.isAfter(fin)) {
                    String error = "La fecha de inicio debe ser anterior a la fecha de fin";
                    enviarError(exchange, 400, error);
                    return;
                }
                
            } catch (Exception e) {
                String error = "Formato de fecha inválido. Use YYYY-MM-DDTHH:MM";
                enviarError(exchange, 400, error);
                return;
            }

            if (cryptosParam.isEmpty()) {
                String error = "Debe especificar al menos una criptomoneda. Ejemplo: ?cryptos=bitcoin";
                enviarError(exchange, 400, error);
                return;
            }

            List<String> cryptos = Arrays.asList(cryptosParam.split(","));
            
            try {
                Map<String, List<Registro>> datos = obtenerDatosMultiples(cryptos, inicio, fin);
                
                if (datos.isEmpty() || datos.values().stream().allMatch(List::isEmpty)) {
                    exchange.sendResponseHeaders(204, -1); // No Content
                    return;
                }

                byte[] imageBytes = generarGraficoComparaPNG(datos);
                
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, imageBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(imageBytes);
                }

            } catch (SQLException e) {
                e.printStackTrace();
                String error = "Error al consultar datos de la base: " + e.getMessage();
                enviarError(exchange, 500, error);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void enviarError(HttpExchange exchange, int code, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(code, message.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }


    private static Map<String, List<Registro>> obtenerDatosMultiples(List<String> cryptos, LocalDateTime inicio, LocalDateTime fin) throws SQLException {
        Map<String, List<Registro>> datos = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            //System.out.println("Obteniendo datos desde " + inicio + " hasta " + fin);

            for (String crypto : cryptos) {
                List<Registro> registros = new ArrayList<>();
                
                // Usar DateTimeFormatter para el formato correcto
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                
                String query = String.format("""
                    SELECT fecha_registro, precio FROM %s 
                    WHERE fecha_registro BETWEEN STR_TO_DATE(?, '%%Y-%%m-%%d %%H:%%i:%%s') 
                    AND STR_TO_DATE(?, '%%Y-%%m-%%d %%H:%%i:%%s')
                    ORDER BY fecha_registro
                """, crypto);

                //System.out.println("Ejecutando query: " + query);
                
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    // Formatear las fechas correctamente para MySQL
                    stmt.setString(1, inicio.format(formatter));
                    stmt.setString(2, fin.format(formatter));
                    
                    //System.out.println("Parámetros: " + inicio.format(formatter) + " - " + fin.format(formatter));
                    
                    ResultSet rs = stmt.executeQuery();
                    int count = 0;

                    while (rs.next()) {
                        LocalDateTime fecha = rs.getTimestamp("fecha_registro").toLocalDateTime().minusHours(6);
                        double precio = rs.getDouble("precio");
                        registros.add(new Registro(fecha, precio));
                        count++;
                    }
                }
                
                datos.put(crypto, registros);
            }
        }
        return datos;
    }

    private static byte[] generarGraficoComparaPNG(Map<String, List<Registro>> datos) throws IOException {
        XYChart chart = new XYChartBuilder()
                .width(800).height(450)
                .title("Comparación de precios de criptomonedas")
                .build();

        // Configuración del estilo del gráfico
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(new Color(245, 245, 245));
        chart.getStyler().setPlotGridLinesColor(new Color(220, 220, 220));
        chart.getStyler().setChartTitleFont(new Font("SansSerif", Font.BOLD, 18));
        chart.getStyler().setAxisTitleFont(new Font("SansSerif", Font.PLAIN, 14));
        chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
        chart.getStyler().setLegendLayout(Styler.LegendLayout.Vertical);
        chart.getStyler().setLegendSeriesLineLength(12);
        chart.getStyler().setMarkerSize(5);
        chart.getStyler().setDecimalPattern("#,###.00");
        chart.getStyler().setXAxisTickMarkSpacingHint(75);
        chart.getStyler().setDatePattern("HH:mm");
        chart.getStyler().setLegendFont(new Font("SansSerif", Font.PLAIN, 12));
        chart.getStyler().setAxisTickLabelsFont(new Font("SansSerif", Font.PLAIN, 10));

        chart.getStyler().setYAxisTicksVisible(false);
        chart.getStyler().setYAxisDecimalPattern("");

        // Agregar cada serie de datos al gráfico
        for (Map.Entry<String, List<Registro>> entry : datos.entrySet()) {
            String crypto = entry.getKey();
            List<Registro> registros = entry.getValue();
            
            if (registros.isEmpty()) continue;
            
            List<Date> fechas = new ArrayList<>();
            List<Double> precios = new ArrayList<>();
            
            for (Registro r : registros) {
                fechas.add(Date.from(r.fecha.atZone(ZoneId.systemDefault()).toInstant()));
                precios.add(r.precio);
            }
            
            Color color = CRYPTO_COLORS.getOrDefault(crypto.toLowerCase(), new Color(52, 152, 219));
            
            XYSeries series = chart.addSeries(crypto.toUpperCase(), fechas, precios);
            series.setLineColor(color);
            series.setMarkerColor(color);
            series.setLineStyle(SeriesLines.SOLID);
            series.setMarker(SeriesMarkers.NONE);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, out, BitmapEncoder.BitmapFormat.PNG);
        return out.toByteArray();
    }


    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Metodos genericos
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null)
            return params;
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    private static class Registro {
        LocalDateTime fecha;
        double precio;

        Registro(LocalDateTime fecha, double precio) {
            this.fecha = fecha;
            this.precio = precio;
        }
    }
}
