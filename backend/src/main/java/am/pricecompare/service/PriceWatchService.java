package am.pricecompare.service;

import am.pricecompare.crawler.CrawlFinishedEvent;
import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.domain.PriceWatch;
import am.pricecompare.domain.WatchDevice;
import am.pricecompare.push.PushMessage;
import am.pricecompare.push.PushSender;
import am.pricecompare.repository.CanonicalProductRepository;
import am.pricecompare.repository.PriceWatchRepository;
import am.pricecompare.repository.WatchDeviceRepository;
import am.pricecompare.web.NotFoundException;
import am.pricecompare.web.dto.CheapestDto;
import am.pricecompare.web.dto.PriceWatchDtos.PriceWatchDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Price watches: an app install follows products; after every crawl the cheapest current (non-outdated) price of each
 * watched product is compared with the watch and a push notification is sent on a real drop.
 */
@Service
public class PriceWatchService {

    private static final Logger log = LoggerFactory.getLogger(PriceWatchService.class);
    static final int MAX_WATCHES_PER_DEVICE = 200;
    /** Without a target price, only drops of at least 2 % notify (avoids noise from 1 ֏ changes). */
    static final BigDecimal MIN_DROP = new BigDecimal("0.02");
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9-]{16,64}");
    private static final Pattern PUSH_TOKEN = Pattern.compile("Expo(nent)?PushToken\\[[A-Za-z0-9_-]{10,200}]");

    private final WatchDeviceRepository deviceRepo;
    private final PriceWatchRepository watchRepo;
    private final CanonicalProductRepository canonicalRepo;
    private final OfferService offerService;
    private final PushSender push;
    private final TransactionTemplate tx;

    public PriceWatchService(WatchDeviceRepository deviceRepo, PriceWatchRepository watchRepo,
                             CanonicalProductRepository canonicalRepo, OfferService offerService, PushSender push,
                             TransactionTemplate tx) {
        this.deviceRepo = deviceRepo;
        this.watchRepo = watchRepo;
        this.canonicalRepo = canonicalRepo;
        this.offerService = offerService;
        this.push = push;
        this.tx = tx;
    }

    // ---------------------------------------------------------------- API

    public void registerDevice(String deviceId, String pushToken, String language) {
        checkDeviceId(deviceId);
        String token = pushToken == null || pushToken.isBlank() ? null : pushToken.trim();
        if (token != null && !PUSH_TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("Invalid Expo push token");
        }
        tx.executeWithoutResult(s -> {
            WatchDevice d = deviceRepo.findById(deviceId).orElseGet(() -> {
                WatchDevice n = new WatchDevice();
                n.setDeviceId(deviceId);
                return n;
            });
            d.setPushToken(token);
            d.setLanguage("en".equals(language) ? "en" : "hy");
            d.setUpdatedAt(Instant.now());
            deviceRepo.save(d);
        });
    }

    public List<PriceWatchDto> list(String deviceId) {
        checkDeviceId(deviceId);
        return tx.execute(s -> {
            List<PriceWatch> watches = watchRepo.findByDevice(deviceId);
            Map<Long, CheapestDto> cheapest = cheapest(watches.stream().map(w -> w.getCanonicalProduct().getId()).toList());
            return watches.stream().map(w -> toDto(w, cheapest.get(w.getCanonicalProduct().getId()))).toList();
        });
    }

    /** Creates the watch, or updates its target price when the product is already watched. */
    public PriceWatchDto watch(String deviceId, long productId, BigDecimal targetPrice) {
        checkDeviceId(deviceId);
        if (targetPrice != null && targetPrice.signum() <= 0) {
            throw new IllegalArgumentException("targetPrice must be positive");
        }
        return tx.execute(s -> {
            CanonicalProduct product = canonicalRepo.findById(productId).filter(CanonicalProduct::isActive)
                    .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));
            WatchDevice device = deviceRepo.findById(deviceId).orElseGet(() -> {
                WatchDevice n = new WatchDevice();
                n.setDeviceId(deviceId);
                return deviceRepo.save(n);
            });
            CheapestDto now = cheapest(List.of(productId)).get(productId);
            BigDecimal price = now == null ? null : now.price();
            PriceWatch w = watchRepo.findByDeviceDeviceIdAndCanonicalProductId(deviceId, productId).orElse(null);
            if (w == null) {
                if (watchRepo.countByDeviceDeviceId(deviceId) >= MAX_WATCHES_PER_DEVICE) {
                    throw new IllegalStateException("At most " + MAX_WATCHES_PER_DEVICE + " watched products per device");
                }
                w = new PriceWatch();
                w.setDevice(device);
                w.setCanonicalProduct(product);
                w.setStartPrice(price);
                w.setReferencePrice(price);
            }
            w.setTargetPrice(targetPrice == null ? null : targetPrice.setScale(2, RoundingMode.HALF_UP));
            // a new or changed target may already be met; let the next crawl notify once it is
            w.setLastNotifiedPrice(null);
            return toDto(watchRepo.save(w), now);
        });
    }

    public void unwatch(String deviceId, long productId) {
        checkDeviceId(deviceId);
        tx.executeWithoutResult(s -> watchRepo.findByDeviceDeviceIdAndCanonicalProductId(deviceId, productId)
                .ifPresent(watchRepo::delete));
    }

    // ---------------------------------------------------------------- notifications

    @EventListener
    public void onCrawlFinished(CrawlFinishedEvent event) {
        if (event.pricesUpdated() > 0) {
            int sent = checkAll();
            log.info("Crawl {}: price watches checked, {} notifications sent", event.runId(), sent);
        }
    }

    /** Re-evaluates every watch against current prices; returns the number of notifications sent. */
    public int checkAll() {
        List<PushMessage> messages = new ArrayList<>();
        tx.executeWithoutResult(s -> {
            List<PriceWatch> watches = watchRepo.findAllActive();
            Map<Long, CheapestDto> cheapest = cheapest(watches.stream().map(w -> w.getCanonicalProduct().getId()).distinct().toList());
            Instant now = Instant.now();
            for (PriceWatch w : watches) {
                CheapestDto c = cheapest.get(w.getCanonicalProduct().getId());
                if (c == null) {
                    continue;
                }
                BigDecimal previous = w.getReferencePrice();
                Decision d = evaluate(w.getTargetPrice(), w.getReferencePrice(), w.getLastNotifiedPrice(), c.price());
                w.setReferencePrice(d.reference());
                w.setLastNotifiedPrice(d.lastNotified());
                if (w.getStartPrice() == null) {
                    w.setStartPrice(c.price());
                }
                if (d.send()) {
                    w.setLastNotifiedAt(now);
                    if (w.getDevice().getPushToken() != null) {
                        messages.add(message(w, c, previous));
                    }
                }
            }
        });
        Set<String> gone = messages.isEmpty() ? Set.of() : push.send(messages);
        if (!gone.isEmpty()) {
            tx.executeWithoutResult(s -> deviceRepo.findAll().stream()
                    .filter(d -> d.getPushToken() != null && gone.contains(d.getPushToken()))
                    .forEach(d -> d.setPushToken(null)));
        }
        return messages.size();
    }

    record Decision(boolean send, BigDecimal reference, BigDecimal lastNotified) {
    }

    /**
     * Pure decision for one watch.
     * <ul>
     *   <li>With a target: notify when the price is at or below the target, once per new low; going back above the
     *       target re-arms the watch.</li>
     *   <li>Without a target: notify when the price fell at least {@link #MIN_DROP} below the reference; the reference
     *       follows the price up immediately and down only when a notification is sent.</li>
     * </ul>
     */
    static Decision evaluate(BigDecimal target, BigDecimal reference, BigDecimal lastNotified, BigDecimal current) {
        if (current == null) {
            return new Decision(false, reference, lastNotified);
        }
        if (target != null) {
            if (current.compareTo(target) > 0) {
                return new Decision(false, current, null);
            }
            boolean notify = lastNotified == null || current.compareTo(lastNotified) < 0;
            return new Decision(notify, current, notify ? current : lastNotified);
        }
        if (reference == null || current.compareTo(reference) > 0) {
            return new Decision(false, current, lastNotified);
        }
        BigDecimal threshold = reference.multiply(BigDecimal.ONE.subtract(MIN_DROP));
        if (current.compareTo(threshold) <= 0) {
            return new Decision(true, current, current);
        }
        return new Decision(false, reference, lastNotified);
    }

    private PushMessage message(PriceWatch w, CheapestDto c, BigDecimal previous) {
        CanonicalProduct p = w.getCanonicalProduct();
        boolean en = "en".equals(w.getDevice().getLanguage());
        String name = en && p.getNameEn() != null ? p.getNameEn() : p.getName();
        String price = amd(c.price());
        String body;
        if (w.getTargetPrice() != null) {
            body = en ? price + " at " + c.storeName() + " (your target " + amd(w.getTargetPrice()) + ")"
                    : price + "՝ " + c.storeName() + " (ձեր նպատակային գինը՝ " + amd(w.getTargetPrice()) + ")";
        } else {
            body = en ? price + " at " + c.storeName() + (previous != null ? " (was " + amd(previous) + ")" : "")
                    : price + "՝ " + c.storeName() + (previous != null ? " (նախկինում՝ " + amd(previous) + ")" : "");
        }
        return new PushMessage(w.getDevice().getPushToken(), (en ? "Price drop: " : "Գինն իջավ՝ ") + name, body,
                Map.of("productId", p.getId()));
    }

    static String amd(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return f.format(v) + " ֏";
    }

    // ---------------------------------------------------------------- helpers

    private Map<Long, CheapestDto> cheapest(Collection<Long> productIds) {
        Map<Long, CheapestDto> out = new HashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        offerService.currentOffers(productIds).forEach((id, offers) ->
                OfferService.cheapest(offers).ifPresent(o -> out.put(id, OfferService.toCheapest(o))));
        return out;
    }

    private PriceWatchDto toDto(PriceWatch w, CheapestDto cheapest) {
        CanonicalProduct p = w.getCanonicalProduct();
        BigDecimal now = cheapest == null ? null : cheapest.price();
        boolean dropped = now != null && (w.getTargetPrice() != null
                ? now.compareTo(w.getTargetPrice()) <= 0
                : w.getStartPrice() != null && now.compareTo(w.getStartPrice()) < 0);
        return new PriceWatchDto(w.getId(), p.getId(), p.getName(), p.getNameEn(), OfferService.displaySize(p), p.getImageUrl(),
                w.getTargetPrice(), w.getStartPrice(), cheapest, dropped, w.getLastNotifiedAt(), w.getCreatedAt());
    }

    private static void checkDeviceId(String deviceId) {
        if (deviceId == null || !DEVICE_ID.matcher(deviceId).matches()) {
            throw new IllegalArgumentException("Invalid device id");
        }
    }
}
