package am.pricecompare.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chooses which comparable products each shopping strategy may buy, using only transparent price evidence:
 * a product's position among comparable products by price per unit (litre, kg or piece). No quality claims.
 *
 * <ul>
 *   <li>CHEAPEST – every comparable product; the optimizer then picks the lowest price.</li>
 *   <li>MID_RANGE – products between the 25th and 75th percentile (mainstream price band); if none, the one closest to
 *       the median.</li>
 *   <li>PREMIUM – the top quartile (at least 4 comparable products); with 2–3 products the highest-priced one; with a
 *       single product there is no premium alternative, which is reported.</li>
 * </ul>
 */
public final class StrategySelector {

    private StrategySelector() {
    }

    public enum Strategy { CHEAPEST, MID_RANGE, PREMIUM }

    /** A comparable product with its typical (median across stores) price per unit. */
    public record ProductPrice(long productId, BigDecimal pricePerUnit) {
    }

    public record Selection(Set<Long> eligible, Map<Long, Integer> percentile, BigDecimal medianPricePerUnit,
                            int comparableCount, boolean noPremiumAlternative) {
    }

    public static Selection select(Strategy strategy, List<ProductPrice> products) {
        if (products.isEmpty()) {
            return new Selection(Set.of(), Map.of(), null, 0, true);
        }
        List<ProductPrice> sorted = new ArrayList<>(products);
        sorted.sort(Comparator.comparing(ProductPrice::pricePerUnit).thenComparingLong(ProductPrice::productId));
        int n = sorted.size();
        Map<Long, Double> pct = new HashMap<>();
        for (int i = 0; i < n; i++) {
            // ties share the average position
            int j = i;
            while (j + 1 < n && sorted.get(j + 1).pricePerUnit().compareTo(sorted.get(i).pricePerUnit()) == 0) {
                j++;
            }
            double p = n == 1 ? 0.5 : ((i + j) / 2.0) / (n - 1);
            for (int k = i; k <= j; k++) {
                pct.put(sorted.get(k).productId(), p);
            }
            i = j;
        }
        BigDecimal median = n % 2 == 1 ? sorted.get(n / 2).pricePerUnit()
                : sorted.get(n / 2 - 1).pricePerUnit().add(sorted.get(n / 2).pricePerUnit()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        Map<Long, Integer> pctInt = new HashMap<>();
        pct.forEach((k, v) -> pctInt.put(k, (int) Math.round(v * 100)));

        Set<Long> eligible = new LinkedHashSet<>();
        boolean noPremium = false;
        switch (strategy) {
            case CHEAPEST -> sorted.forEach(p -> eligible.add(p.productId()));
            case MID_RANGE -> {
                sorted.stream().filter(p -> pct.get(p.productId()) >= 0.25 && pct.get(p.productId()) <= 0.75)
                        .forEach(p -> eligible.add(p.productId()));
                if (eligible.isEmpty()) {
                    sorted.stream().min(Comparator.comparingDouble(p -> Math.abs(pct.get(p.productId()) - 0.5)))
                            .ifPresent(p -> eligible.add(p.productId()));
                }
            }
            case PREMIUM -> {
                if (n == 1) {
                    eligible.add(sorted.get(0).productId());
                    noPremium = true;
                } else if (n < 4) {
                    BigDecimal top = sorted.get(n - 1).pricePerUnit();
                    sorted.stream().filter(p -> p.pricePerUnit().compareTo(top) == 0).forEach(p -> eligible.add(p.productId()));
                } else {
                    sorted.stream().filter(p -> pct.get(p.productId()) >= 0.75).forEach(p -> eligible.add(p.productId()));
                }
            }
        }
        return new Selection(eligible, pctInt, median, n, noPremium);
    }
}
