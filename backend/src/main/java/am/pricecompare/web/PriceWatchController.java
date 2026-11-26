package am.pricecompare.web;

import am.pricecompare.service.PriceWatchService;
import am.pricecompare.web.dto.PriceWatchDtos.DeviceRequest;
import am.pricecompare.web.dto.PriceWatchDtos.PriceWatchDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Price watches for anonymous app installs. The device id is a random UUID the app generates and keeps; it is the
 * only credential, so it is sent in a header and never appears in URLs or logs.
 */
@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "Price watches", description = "Follow products and get notified when the cheapest price drops")
public class PriceWatchController {

    static final String DEVICE_HEADER = "X-Device-Id";

    private final PriceWatchService service;

    public PriceWatchController(PriceWatchService service) {
        this.service = service;
    }

    @PutMapping("/devices/me")
    @Operation(summary = "Register this install's push token and language (pushToken empty = no notifications)")
    public ResponseEntity<Void> registerDevice(@RequestHeader(DEVICE_HEADER) String deviceId, @Valid @RequestBody DeviceRequest body) {
        service.registerDevice(deviceId, body.pushToken(), body.language());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/watches")
    @Operation(summary = "Watched products with their current cheapest price")
    public List<PriceWatchDto> list(@RequestHeader(DEVICE_HEADER) String deviceId) {
        return service.list(deviceId);
    }

    @PutMapping("/watches/{productId}")
    @Operation(summary = "Watch a product (or change its target price)",
            description = "Without targetPrice: notified when the cheapest price drops by at least 2 %. "
                    + "With targetPrice: notified when it reaches the target.")
    public PriceWatchDto watch(@RequestHeader(DEVICE_HEADER) String deviceId, @PathVariable long productId,
                               @RequestBody(required = false) WatchBody body) {
        return service.watch(deviceId, productId, body == null ? null : body.targetPrice());
    }

    @DeleteMapping("/watches/{productId}")
    @Operation(summary = "Stop watching a product")
    public ResponseEntity<Void> unwatch(@RequestHeader(DEVICE_HEADER) String deviceId, @PathVariable long productId) {
        service.unwatch(deviceId, productId);
        return ResponseEntity.noContent().build();
    }

    public record WatchBody(java.math.BigDecimal targetPrice) {
    }
}
