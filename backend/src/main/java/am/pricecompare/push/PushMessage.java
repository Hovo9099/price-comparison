package am.pricecompare.push;

import java.util.Map;

/** One notification to one device. */
public record PushMessage(String to, String title, String body, Map<String, Object> data) {
}
