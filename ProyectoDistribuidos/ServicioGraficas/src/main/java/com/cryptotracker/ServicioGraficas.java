package com.cryptotracker;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpContext;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import java.net.InetSocketAddress;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;

public class ServicioGraficas {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        HttpContext context = server.createContext("/grafica");
        context.setHandler(ServicioGraficas::handleGraficaRequest);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("Microservicio de gráficas activo en puerto 8081");
    }

    private static void handleGraficaRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String cripto = "bitcoin";
        int horas = 3;

        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    if (pair[0].equalsIgnoreCase("cripto")) cripto = pair[1];
                    if (pair[0].equalsIgnoreCase("horas")) {
                        try {
                            horas = Integer.parseInt(pair[1]);
                            if (horas < 1 || horas > 24) horas = 3; // límites seguros
                        } catch (NumberFormatException e) {
                            horas = 3;
                        }
                    }
                }
            }
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (int i = 1; i <= horas; i++) {
            double precio = 1000 + Math.random() * 500; // Precio simulado
            dataset.addValue(precio, cripto, i + "h");
        }

        JFreeChart chart = ChartFactory.createLineChart(
            "Precio de " + cripto.toUpperCase(),
            "Tiempo (horas)",
            "Precio (USD)",
            dataset
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChartUtils.writeChartAsPNG(out, chart, 600, 400);
        byte[] imagen = out.toByteArray();

        exchange.getResponseHeaders().add("Content-Type", "image/png");
        exchange.sendResponseHeaders(200, imagen.length);
        OutputStream os = exchange.getResponseBody();
        os.write(imagen);
        os.close();
    }
}
