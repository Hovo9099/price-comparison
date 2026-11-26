package am.pricecompare.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Operational alerts for whoever runs the service. Delivered to a Telegram chat and/or a webhook (Slack- and
 * Discord-compatible payload) when configured; always logged. Delivery failures never propagate.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    private final String telegramToken;
    private final String telegramChatId;
    private final String webhookUrl;
    private final String telegramApi;

    public AlertService(ObjectMapper json,
                        @Value("${app.alerts.telegram-bot-token:}") String telegramToken,
                        @Value("${app.alerts.telegram-chat-id:}") String telegramChatId,
                        @Value("${app.alerts.webhook-url:}") String webhookUrl,
                        @Value("${app.alerts.telegram-api:https://api.telegram.org}") String telegramApi) {
        this.json = json;
        this.telegramToken = telegramToken.trim();
        this.telegramChatId = telegramChatId.trim();
        this.webhookUrl = webhookUrl.trim();
        this.telegramApi = telegramApi.replaceAll("/+$", "");
    }

    public boolean configured() {
        return (!telegramToken.isEmpty() && !telegramChatId.isEmpty()) || !webhookUrl.isEmpty();
    }

    public void alert(String text) {
        log.warn("ALERT: {}", text);
        if (!telegramToken.isEmpty() && !telegramChatId.isEmpty()) {
            post(telegramApi + "/bot" + telegramToken + "/sendMessage",
                    Map.of("chat_id", telegramChatId, "text", text, "disable_web_page_preview", true), "Telegram");
        }
        if (!webhookUrl.isEmpty()) {
            // "text" is read by Slack/Mattermost/Google Chat, "content" by Discord
            post(webhookUrl, Map.of("text", text, "content", text), "webhook");
        }
    }

    private void post(String url, Map<String, Object> body, String channel) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.warn("Alert via {} returned HTTP {}", channel, res.statusCode());
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // never log the URL: the Telegram one contains the bot token
            log.warn("Alert via {} failed: {}", channel, e.getClass().getSimpleName());
        }
    }
}
