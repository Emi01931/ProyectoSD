import com.sun.net.httpserver.*;
import org.knowm.xchart.*;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.Styler;

import java.awt.*;
import java.awt.image.BufferedImage;
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
import javax.imageio.ImageIO;

// Ejemplo de peticion del punto 1, una moneda graficada cierta cantidad de horas
// http://localhost:8081/grafico?crypto=bitcoin&horas=1

// Ejemplo del punto 3, comparar monedas
//  http://localhost:8081/graficos-todas?hora=horas=1

// Ejemplo de punto 4, comparacion de monedas con hora de inicio y fin
// Solo cambien la fecha porque solo permite mostrar 24 horas hacia atras
// http://localhost:8081/graficoCompara?cryptos=ethereum,ripple,solana,tron,dogecoin,cardano,hyperliquid,bitcoin_cash,chainlink&inicio=2025-06-27T15:00&fin=2025-06-27T16:00


public class GraficoService {

    //private static final String DB_URL = "jdbc:mysql://localhost:3308/criptomonedas_db?user=root&password=&useSSL=false";
    private static final String DB_URL = "jdbc:mysql://10.23.176.2:3306/criptomonedas_db?user=root&password=root&useSSL=true";
    private static final int PORT = 8081;

    // Mapeo de criptomonedas con sus nombres y símbolos
    private static final Map<String, String[]> CRIPTOS = Map.of(
        "bitcoin", new String[]{"Bitcoin", "BTC"},
        "ethereum", new String[]{"Ethereum", "ETH"},
        "ripple", new String[]{"XRP", "XRP"},
        "solana", new String[]{"Solana", "SOL"},
        "tron", new String[]{"TRON", "TRX"},
        "dogecoin", new String[]{"Dogecoin", "DOGE"},
        "cardano", new String[]{"Cardano", "ADA"},
        "hyperliquid", new String[]{"Hyperliquid", "HYPE"},
        "bitcoin_cash", new String[]{"Bitcoin Cash", "BCH"},
        "chainlink", new String[]{"Chainlink", "LINK"}
    );

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/grafico", GraficoService::handleGraficoRequest);
        server.createContext("/graficoCompara", GraficoService::handleGraficoComparaRequest);
        server.createContext("/graficos-todas", GraficoService::handleGraficosTodas);
        server.createContext("/regresion", GraficoService::handleRegresionLineal);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("GraficoService escuchando en http://localhost:" + PORT + "/grafico");
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Punto 1, grafico individual
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void handleGraficoRequest(HttpExchange exchange) throws IOException {

        setCORSHeaders(exchange); // <-- Agrega esta línea
    
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleOptionsRequest(exchange);
            return;
        }

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
    /// Punto 3 - Todas las criptomonedas
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void handleGraficosTodas(HttpExchange exchange) throws IOException {

        setCORSHeaders(exchange); // <-- Agrega esta línea
    
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleOptionsRequest(exchange);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        int horas = Integer.parseInt(params.getOrDefault("horas", "24"));

        if (horas <= 0 || horas > 24) {
            String error = "Parámetros inválidos. Usa ?horas=24 (1-24)";
            exchange.sendResponseHeaders(400, error.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(error.getBytes());
            }
            return;
        }

        try {
            byte[] imageBytes = generarGraficosTodasCriptos(horas);
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

    private static byte[] generarGraficosTodasCriptos(int horas) throws SQLException, IOException {
        final int graficosAncho = 400;
        final int graficosAlto = 300;
        final int filas = 5;
        final int columnas = 2;
        final int margen = 20;
        
        int imagenAncho = columnas * graficosAncho + (columnas + 1) * margen;
        int imagenAlto = filas * graficosAlto + (filas + 1) * margen + 50;
        
        BufferedImage imagenCompuesta = new BufferedImage(imagenAncho, imagenAlto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = imagenCompuesta.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, imagenAncho, imagenAlto);
        
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();
        String titulo = "Variación de Precios - Últimas " + horas + " horas";
        int tituloX = (imagenAncho - fm.stringWidth(titulo)) / 2;
        g2d.drawString(titulo, tituloX, 30);
        
        List<String> criptosOrdenadas = new ArrayList<>(CRIPTOS.keySet());
        
        for (int i = 0; i < criptosOrdenadas.size(); i++) {
            String crypto = criptosOrdenadas.get(i);
            List<Registro> registros = obtenerDatos(crypto, horas);
            
            if (!registros.isEmpty()) {
                XYChart chart = crearGraficoIndividual(crypto, registros, graficosAncho, graficosAlto);
                BufferedImage graficoImg = BitmapEncoder.getBufferedImage(chart);
                
                int fila = i / columnas;
                int columna = i % columnas;
                int x = margen + columna * (graficosAncho + margen);
                int y = 60 + margen + fila * (graficosAlto + margen);
                
                g2d.drawImage(graficoImg, x, y, null);
            }
        }
        
        g2d.dispose();
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(imagenCompuesta, "PNG", out);
        return out.toByteArray();
    }

    private static XYChart crearGraficoIndividual(String crypto, List<Registro> registros, int ancho, int alto) {
        String[] info = CRIPTOS.get(crypto);
        String nombre = info[0];
        String simbolo = info[1];
        
        List<java.util.Date> fechas = new ArrayList<>();
        List<Double> precios = new ArrayList<>();

        for (Registro r : registros) {
            fechas.add(java.util.Date.from(r.fecha.atZone(ZoneId.systemDefault()).toInstant()));
            precios.add(r.precio);
        }

        XYChart chart = new XYChartBuilder()
                .width(ancho).height(alto)
                .title(nombre + " (" + simbolo + ")")
                .xAxisTitle("Hora")
                .yAxisTitle("USD")
                .build();

        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(new Color(245, 245, 245));
        chart.getStyler().setPlotGridLinesColor(new Color(220, 220, 220));
        chart.getStyler().setChartTitleFont(new Font("SansSerif", Font.BOLD, 12));
        chart.getStyler().setAxisTitleFont(new Font("SansSerif", Font.PLAIN, 10));
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setMarkerSize(3);
        chart.getStyler().setDecimalPattern("#,###.00");
        chart.getStyler().setDatePattern("HH:mm");

        XYSeries series = chart.addSeries("Precio", fechas, precios);
        series.setMarker(new None());
        series.setLineColor(getColorForCrypto(crypto));
        series.setLineWidth(2);

        return chart;
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

            setCORSHeaders(exchange); // <-- Agrega esta línea
    
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptionsRequest(exchange);
                return;
            }

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
                        LocalDateTime fecha = rs.getTimestamp("fecha_registro").toLocalDateTime();
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
                .width(800).height(480)
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
    /// Punto 5 - Regresión lineal
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void handleRegresionLineal(HttpExchange exchange) throws IOException {

        setCORSHeaders(exchange); // <-- Agrega esta línea
    
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleOptionsRequest(exchange);
            return;
        }


        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String crypto = params.getOrDefault("crypto", "").toLowerCase();
        int horaInicio = Integer.parseInt(params.getOrDefault("inicio", "10"));
        int horaFin = Integer.parseInt(params.getOrDefault("fin", "12"));

        if (crypto.isEmpty() || !CRIPTOS.containsKey(crypto) || 
            horaInicio < 0 || horaInicio > 23 || horaFin <= horaInicio || horaFin > 24) {
            String error = "Parámetros inválidos. Usa ?crypto=bitcoin&inicio=10&fin=12";
            exchange.sendResponseHeaders(400, error.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(error.getBytes());
            }
            return;
        }

        try {
            byte[] imageBytes = generarGraficoRegresion(crypto, horaInicio, horaFin);
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

    private static byte[] generarGraficoRegresion(String crypto, int horaInicio, int horaFin) 
            throws SQLException, IOException {
        
        List<Registro> registros = obtenerDatosRango(crypto, horaInicio, horaFin);
        
        if (registros.isEmpty()) {
            throw new SQLException("No hay datos suficientes para el rango especificado");
        }

        String[] info = CRIPTOS.get(crypto);
        String nombre = info[0];
        String simbolo = info[1];

        List<Double> tiempos = new ArrayList<>();
        List<Double> precios = new ArrayList<>();
        
        for (int i = 0; i < registros.size(); i++) {
            tiempos.add((double) i);
            precios.add(registros.get(i).precio);
        }

        RegresionLineal regresion = calcularRegresionLineal(tiempos, precios);
        
        List<Double> lineaX = new ArrayList<>();
        List<Double> lineaY = new ArrayList<>();
        
        for (double i = 0; i < registros.size(); i++) {
            lineaX.add(i);
            lineaY.add(regresion.pendiente * i + regresion.interseccion);
        }

        XYChart chart = new XYChartBuilder()
                .width(800).height(500)
                .title("Regresión Lineal - " + nombre + " (" + simbolo + ")")
                .xAxisTitle("Tiempo (intervalos)")
                .yAxisTitle("Precio (USD)")
                .build();

        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(new Color(245, 245, 245));
        chart.getStyler().setPlotGridLinesColor(new Color(220, 220, 220));
        chart.getStyler().setChartTitleFont(new Font("SansSerif", Font.BOLD, 18));
        chart.getStyler().setAxisTitleFont(new Font("SansSerif", Font.PLAIN, 14));
        chart.getStyler().setDecimalPattern("#,###.00");
        chart.getStyler().setMarkerSize(6);

        XYSeries puntosOriginales = chart.addSeries("Datos reales", tiempos, precios);
        puntosOriginales.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        puntosOriginales.setMarker(SeriesMarkers.CIRCLE);
        puntosOriginales.setMarkerColor(new Color(231, 76, 60));

        XYSeries lineaRegresion = chart.addSeries("Línea de regresión", lineaX, lineaY);
        lineaRegresion.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        lineaRegresion.setLineColor(new Color(52, 152, 219));
        lineaRegresion.setLineWidth(3);
        lineaRegresion.setMarker(new None());

        String ecuacion = String.format("y = %.2fx + %.2f", regresion.pendiente, regresion.interseccion);
        String r2 = String.format("R² = %.4f", regresion.coeficienteDeterminacion);
        
        chart.setTitle(chart.getTitle() + "\n" + ecuacion + " | " + r2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, out, BitmapEncoder.BitmapFormat.PNG);
        return out.toByteArray();
    }

    private static List<Registro> obtenerDatosRango(String crypto, int horaInicio, int horaFin) 
            throws SQLException {
        List<Registro> datos = new ArrayList<>();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver JDBC no encontrado");
        }

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String query = String.format("""
                SELECT fecha_registro, precio FROM %s
                WHERE DATE(fecha_registro) = CURDATE()
                AND HOUR(fecha_registro) >= ? AND HOUR(fecha_registro) < ?
                ORDER BY fecha_registro ASC
            """, crypto);

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, horaInicio);
                stmt.setInt(2, horaFin);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    LocalDateTime fecha = rs.getTimestamp("fecha_registro").toLocalDateTime();
                    double precio = rs.getDouble("precio");
                    datos.add(new Registro(fecha, precio));
                }
            }
        }

        return datos;
    }

    private static RegresionLineal calcularRegresionLineal(List<Double> x, List<Double> y) {
        int n = x.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += x.get(i);
            sumY += y.get(i);
            sumXY += x.get(i) * y.get(i);
            sumX2 += x.get(i) * x.get(i);
            sumY2 += y.get(i) * y.get(i);
        }

        double pendiente = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double interseccion = (sumY - pendiente * sumX) / n;
        
        double mediaY = sumY / n;
        double ssTot = 0, ssRes = 0;
        
        for (int i = 0; i < n; i++) {
            double yPred = pendiente * x.get(i) + interseccion;
            ssTot += Math.pow(y.get(i) - mediaY, 2);
            ssRes += Math.pow(y.get(i) - yPred, 2);
        }
        
        double r2 = 1 - (ssRes / ssTot);

        return new RegresionLineal(pendiente, interseccion, r2);
    }

    private static Color getColorForCrypto(String crypto) {
        return CRYPTO_COLORS.getOrDefault(crypto, new Color(52, 152, 219));
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

    private static class RegresionLineal {
        double pendiente;
        double interseccion;
        double coeficienteDeterminacion;

        RegresionLineal(double pendiente, double interseccion, double r2) {
            this.pendiente = pendiente;
            this.interseccion = interseccion;
            this.coeficienteDeterminacion = r2;
        }
    }

    private static void setCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void handleOptionsRequest(HttpExchange exchange) throws IOException {
        setCORSHeaders(exchange);
        exchange.sendResponseHeaders(204, -1); // No Content
    }
}