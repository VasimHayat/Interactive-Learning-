import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class HttpLocal {
    public static void main(String[] args) throws Exception {
        // port 0 = any free port; the real port is never printed, so the output is stable
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stock/BOLT-7", exchange -> {
            byte[] body = "{\"sku\":\"BOLT-7\",\"onHand\":42}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest ok = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/stock/BOLT-7"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> sync = client.send(ok, HttpResponse.BodyHandlers.ofString());
            System.out.println("sync  status = " + sync.statusCode());
            System.out.println("sync  body   = " + sync.body());
            System.out.println("sync  type   = " + sync.headers().firstValue("Content-Type").orElse("?"));

            CompletableFuture<String> async = client.sendAsync(ok, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> body.contains("\"onHand\":42") ? "42 on hand" : "unexpected");
            System.out.println("async result = " + async.get(5, TimeUnit.SECONDS));

            HttpRequest missing = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/stock/NOPE"))
                    .timeout(Duration.ofSeconds(5)).build();
            System.out.println("miss  status = " + client.send(missing, HttpResponse.BodyHandlers.discarding()).statusCode());
        } finally {
            server.stop(0);
        }
        System.out.println("server stopped");
    }
}