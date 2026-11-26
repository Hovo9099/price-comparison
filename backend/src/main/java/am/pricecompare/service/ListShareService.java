package am.pricecompare.service;

import am.pricecompare.domain.ShoppingList;
import am.pricecompare.repository.ShoppingListRepository;
import am.pricecompare.web.NotFoundException;
import am.pricecompare.web.dto.ShoppingListDtos.ShoppingListDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * Sharing a shopping list between phones without accounts: the owner gets a random code (8 characters from an
 * alphabet without look-alikes, ~8·10¹¹ combinations; the public API is rate limited) and another device opens the
 * list with it. Everyone with the code edits the same list. Revoking makes the old code useless.
 */
@Service
public class ListShareService {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();
    private final ShoppingListRepository repo;
    private final ShoppingListService lists;

    public ListShareService(ShoppingListRepository repo, ShoppingListService lists) {
        this.repo = repo;
        this.lists = lists;
    }

    /** Returns the list's share code, creating it on first use. */
    @Transactional
    public String share(long listId) {
        ShoppingList l = repo.findById(listId).orElseThrow(() -> new NotFoundException("Shopping list " + listId + " not found"));
        if (l.getShareCode() == null) {
            String code;
            do {
                code = newCode();
            } while (repo.findByShareCode(code).isPresent());
            l.setShareCode(code);
            l.setUpdatedAt(Instant.now());
        }
        return l.getShareCode();
    }

    @Transactional
    public void revoke(long listId) {
        repo.findById(listId).ifPresent(l -> l.setShareCode(null));
    }

    public ShoppingListDto join(String rawCode) {
        String code = normalize(rawCode);
        ShoppingList l = code == null ? null : repo.findByShareCode(code).orElse(null);
        if (l == null) {
            throw new NotFoundException("No shared list with this code");
        }
        return lists.get(l.getId());
    }

    /** Accepts lower case, spaces and dashes ("abcd-2345"); null when it cannot be a code. */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String c = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (c.length() != LENGTH) {
            return null;
        }
        for (char ch : c.toCharArray()) {
            if (ALPHABET.indexOf(ch) < 0) {
                return null;
            }
        }
        return c;
    }

    String newCode() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
