package am.pricecompare.crawler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PoliteHttpClientGzipTest {

    @Test
    void decodesGzipFilesServedWithoutContentEncoding() throws Exception {
        String xml = "<urlset><url><loc>https://test.invalid/Կաթ/</loc></url></urlset>";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(xml.getBytes(StandardCharsets.UTF_8));
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sitemap.xml.gz", ex -> {
            ex.getResponseHeaders().add("Content-Type", "application/x-gzip"); // no Content-Encoding header
            ex.sendResponseHeaders(200, bytes.size());
            ex.getResponseBody().write(bytes.toByteArray());
            ex.close();
        });
        server.createContext("/plain", ex -> {
            byte[] b = xml.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        try {
            CrawlerProperties props = new CrawlerProperties();
            props.setRespectRobotsTxt(false);
            props.setMinDelayMs(0);
            PoliteHttpClient http = new PoliteHttpClient(props);
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThat(http.get(base + "/sitemap.xml.gz")).isEqualTo(xml);
            assertThat(http.get(base + "/plain")).isEqualTo(xml);
        } finally {
            server.stop(0);
        }
    }
}
