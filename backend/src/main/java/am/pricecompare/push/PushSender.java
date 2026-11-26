package am.pricecompare.push;

import java.util.List;
import java.util.Set;

public interface PushSender {

    /**
     * Sends the messages and returns the push tokens the push service reported as no longer registered
     * (app uninstalled / notifications revoked), so callers can forget them.
     */
    Set<String> send(List<PushMessage> messages);
}
