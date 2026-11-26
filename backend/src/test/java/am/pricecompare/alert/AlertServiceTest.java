package am.pricecompare.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class AlertServiceTest {

    HttpServer server;
    final List<String> received = new CopyOnWriteArrayList<>();
    String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            received.add(ex.getRequestURI().getPath() + " " + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsToTelegramAndWebhook() {
        AlertService s = new AlertService(new ObjectMapper(), "TESTTOKEN", "42", base + "/hook", base);
        assertThat(s.configured()).isTrue();
        s.alert("Crawl failed");
        assertThat(received).hasSize(2);
        assertThat(received).anySatisfy(r -> assertThat(r).startsWith("/botTESTTOKEN/sendMessage ")
                .contains("\"chat_id\":\"42\"").contains("Crawl failed"));
        assertThat(received).anySatisfy(r -> assertThat(r).startsWith("/hook ")
                .contains("\"text\":\"Crawl failed\"").contains("\"content\":\"Crawl failed\""));
    }

    @Test
    void unconfiguredOnlyLogsAndUnreachableNeverThrows() {
        AlertService none = new AlertService(new ObjectMapper(), "", "", "", base);
        assertThat(none.configured()).isFalse();
        none.alert("x");
        assertThat(received).isEmpty();
        new AlertService(new ObjectMapper(), "", "", "http://127.0.0.1:1/unreachable", base).alert("x");
    }
}
