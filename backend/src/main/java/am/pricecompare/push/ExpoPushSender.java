package am.pricecompare.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends notifications through the Expo push service (https://docs.expo.dev/push-notifications/sending-notifications/).
 * Disabled with {@code app.push.enabled=false}; failures are logged and never break the caller.
 */
@Component
public class ExpoPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);
    private static final int CHUNK = 100;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    private final boolean enabled;
    private final URI endpoint;
    private final String accessToken;

    public ExpoPushSender(ObjectMapper json,
                          @Value("${app.push.enabled:true}") boolean enabled,
                          @Value("${app.push.expo-url:https://exp.host/--/api/v2/push/send}") String endpoint,
                          @Value("${app.push.expo-access-token:}") String accessToken) {
        this.json = json;
        this.enabled = enabled;
        this.endpoint = URI.create(endpoint);
        this.accessToken = accessToken;
    }

    @Override
    public Set<String> send(List<PushMessage> messages) {
        Set<String> unregistered = new HashSet<>();
        if (!enabled || messages.isEmpty()) {
            return unregistered;
        }
        for (int i = 0; i < messages.size(); i += CHUNK) {
            List<PushMessage> chunk = messages.subList(i, Math.min(messages.size(), i + CHUNK));
            try {
                unregistered.addAll(sendChunk(chunk));
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Expo push failed for {} messages: {}", chunk.size(), e.toString());
            }
        }
        return unregistered;
    }

    private Set<String> sendChunk(List<PushMessage> chunk) throws Exception {
        List<Map<String, Object>> payload = chunk.stream().map(m -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("to", m.to());
            o.put("title", m.title());
            o.put("body", m.body());
            o.put("data", m.data());
            o.put("sound", "default");
            o.put("channelId", "price-drops");
            return o;
        }).toList();
        HttpRequest.Builder req = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
        if (!accessToken.isBlank()) {
            req.header("Authorization", "Bearer " + accessToken);
        }
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            log.warn("Expo push returned HTTP {}: {}", res.statusCode(), res.body());
            return Set.of();
        }
        // Tickets come back in request order.
        Set<String> unregistered = new HashSet<>();
        JsonNode tickets = json.readTree(res.body()).path("data");
        for (int i = 0; i < tickets.size() && i < chunk.size(); i++) {
            JsonNode t = tickets.get(i);
            if ("error".equals(t.path("status").asText())) {
                String error = t.path("details").path("error").asText();
                if ("DeviceNotRegistered".equals(error)) {
                    unregistered.add(chunk.get(i).to());
                } else {
                    log.warn("Expo push error for one message: {} {}", error, t.path("message").asText());
                }
            }
        }
        return unregistered;
    }
}
