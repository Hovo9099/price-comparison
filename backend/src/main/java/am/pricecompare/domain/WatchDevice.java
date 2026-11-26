package am.pricecompare.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** An anonymous app install that watches prices. The device id is a random UUID known only to that install. */
@Entity
@Table(name = "watch_devices")
@Getter
@Setter
public class WatchDevice {
    @Id
    private String deviceId;
    /** Expo push token; null when the user did not allow notifications (watches still work in the app). */
    private String pushToken;
    private String language = "hy";
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
