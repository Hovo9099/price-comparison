package am.pricecompare.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Thumbnails of product images. Store images are often 1-2 MB PNGs; this endpoint downloads an image from a
 * known store host only (not an open proxy), scales it down to a JPEG and caches it on disk.
 */
@RestController
@RequestMapping("/api/images")
@Tag(name = "Images", description = "Downscaled product image thumbnails")
public class ImageProxyController {

    private static final Logger log = LoggerFactory.getLogger(ImageProxyController.class);
    private static final Set<String> ALLOWED_HOSTS = Set.of("www.sas.am", "sas.am", "media.yerevan-city.am", "yerevan-city.am",
            "carrefour.am", "www.carrefour.am", "supermarket.am", "www.supermarket.am",
            "static.parma.am", "parma.am");
    private static final long MAX_SOURCE_BYTES = 8_000_000;

    private final Path cacheDir;
    private final String userAgent;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public ImageProxyController(@Value("${app.image-cache-dir:${java.io.tmpdir}/wic-image-cache}") String cacheDir,
                                @Value("${crawler.user-agent:WhereIsItCheapestBot/0.1}") String userAgent) throws IOException {
        this.cacheDir = Files.createDirectories(Path.of(cacheDir));
        this.userAgent = userAgent;
    }

    @GetMapping
    @Operation(summary = "A JPEG thumbnail (max width/height w px) of a product image hosted by a known store")
    public ResponseEntity<byte[]> thumbnail(@RequestParam String url, @RequestParam(defaultValue = "256") int w) throws Exception {
        int size = Math.max(64, Math.min(w, 800));
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (uri.getHost() == null || !ALLOWED_HOSTS.contains(uri.getHost().toLowerCase()) || !"https".equalsIgnoreCase(uri.getScheme())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Path cached = cacheDir.resolve(sha1(size + "|" + url) + ".jpg");
        if (Files.exists(cached)) {
            return ok(Files.readAllBytes(cached));
        }
        HttpResponse<byte[]> resp = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("User-Agent", userAgent).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200 || resp.body().length > MAX_SOURCE_BYTES) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(resp.body()));
        if (src == null) {
            // Format ImageIO cannot decode (e.g. WebP): pass the original through.
            String type = resp.headers().firstValue("Content-Type").orElse("image/webp");
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS)).body(resp.body());
        }
        byte[] jpeg = scale(src, size);
        Files.write(cached, jpeg);
        return ok(jpeg);
    }

    static byte[] scale(BufferedImage src, int max) throws IOException {
        double f = Math.min(1.0, (double) max / Math.max(src.getWidth(), src.getHeight()));
        int tw = Math.max(1, (int) Math.round(src.getWidth() * f));
        int th = Math.max(1, (int) Math.round(src.getHeight() * f));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE); // transparent PNG backgrounds become white
        g.fillRect(0, 0, tw, th);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, tw, th, null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bos);
        return bos.toByteArray();
    }

    private static ResponseEntity<byte[]> ok(byte[] body) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS)).body(body);
    }

    private static String sha1(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
