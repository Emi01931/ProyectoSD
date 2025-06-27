import com.sun.net.httpserver.*;
import org.knowm.xchart.*;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.knowm.xchart.style.lines.SeriesLines;

import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;

import org.knowm.xchart.style.Styler;

// Como lo corro
//javac -cp ".;lib/*" GraficoService.java
//java -cp ".;lib/*" GraficoService

// Ejemplo de peticion
//http://localhost:8082/grafico?crypto=hyperliquid,chainlink&inicio=15:00&fin=17:00

public class GraficoCompara {

    // private static final String DB_URL =
    // "jdbc:mysql://localhost:3306/criptomonedas_db?user=root&password=&useSSL=false&serverTimezone=UTC";
    private static final String DB_URL = "jdbc:mysql://localhost:3308/criptomonedas_db?user=root&password=&useSSL=false&serverTimezone=UTC";
    private static final int PORT = 8082;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/grafico", GraficoCompara::handleGraficoRequest);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("GraficoService escuchando en http://localhost:" + PORT + "/grafico");
    }

    private static void handleGraficoRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String[] cryptos = params.getOrDefault("crypto", "").toLowerCase().split(",");
        int horas = Integer.parseInt(params.getOrDefault("horas", "3"));

        if (cryptos.length == 0 || horas <= 0 || horas > 24) {
            String error = "Parámetros inválidos. Usa ?crypto=bitcoin,ethereum&horas=3";
            exchange.sendResponseHeaders(400, error.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(error.getBytes());
            }
            return;
        }

        try {
            List<List<Registro>> todosRegistros = new ArrayList<>();
            for (String crypto : cryptos) {
                List<Registro> registros = obtenerDatos(crypto.trim(), horas);
                if (!registros.isEmpty()) {
                    todosRegistros.add(registros);
                }
            }

            if (todosRegistros.isEmpty()) {
                exchange.sendResponseHeaders(204, -1); // No Content
                return;
            }

            byte[] imageBytes = generarGraficoPNG(cryptos, todosRegistros);
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

    private static byte[] generarGraficoPNG(String[] cryptos, List<List<Registro>> todosRegistros) throws IOException {
        XYChart chart = new XYChartBuilder()
                .width(800).height(450)
                .xAxisTitle("Hora")
                .yAxisTitle("")
                .build();

        // Configuración del estilo
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(new Color(245, 245, 245));
        chart.getStyler().setPlotGridLinesColor(new Color(220, 220, 220));
        chart.getStyler().setChartTitleFont(new Font("SansSerif", Font.BOLD, 18));
        chart.getStyler().setAxisTitleFont(new Font("SansSerif", Font.PLAIN, 14));
        chart.getStyler().setLegendVisible(true);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(5);
        chart.getStyler().setDecimalPattern("#,###.00");
        chart.getStyler().setXAxisTickMarkSpacingHint(75);
        chart.getStyler().setDatePattern("HH:mm");
        
        // Ocultar valores del eje Y
        //chart.getStyler().setYAxisTicksVisible(false);
        //chart.getStyler().setYAxisDecimalPattern("");

        // Colores para las diferentes criptomonedas
        Color[] colores = {
            new Color(52, 152, 219),   // Azul
            new Color(155, 89, 182),   // Morado
            new Color(46, 204, 113),   // Verde
            new Color(241, 196, 15),    // Amarillo
            new Color(231, 76, 60),     // Rojo
            new Color(26, 188, 156),   // Turquesa
            new Color(52, 73, 94),     // Gris oscuro
            new Color(230, 126, 34)    // Naranja
        };

        for (int i = 0; i < cryptos.length && i < todosRegistros.size(); i++) {
            List<Registro> registros = todosRegistros.get(i);
            List<java.util.Date> fechas = new ArrayList<>();
            List<Double> precios = new ArrayList<>();

            for (Registro r : registros) {
                fechas.add(java.util.Date.from(r.fecha.atZone(ZoneId.systemDefault()).toInstant()));
                precios.add(r.precio);
            }

            XYSeries series = chart.addSeries(cryptos[i].toUpperCase(), fechas, precios);
            series.setLineColor(colores[i % colores.length]);
            series.setMarker(new None());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, out, BitmapEncoder.BitmapFormat.PNG);
        return out.toByteArray();
    }

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
