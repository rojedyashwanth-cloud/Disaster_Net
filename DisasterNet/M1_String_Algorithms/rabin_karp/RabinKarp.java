/*
 * Rabin-Karp fingerprinting - module M1.
 *
 * <p>Use case in DisasterNet: two jobs are needed at once. (1) Pattern search
 * by rolling hash, which is the M1 counterpart of KMP/Z but uses O(1) extra
 * space instead of O(m). (2) Whole-report fingerprinting, so duplicate radio
 * traffic and copy-pasted boilerplate between incident reports are detected
 * without comparing every pair of reports character by character.
 *
 * <p>Hash for a window s[i..i+m):
 * {@code H = sum over j of s[i+j] * base^(m-1-j)  (mod p)}
 * The next window reuses the outgoing and incoming characters:
 * {@code H' = (H - s[i]*base^(m-1))*base + s[i+m]}.
 *
 * <p>A match of the hash is only a candidate: a collision is possible, so
 * every hit is confirmed with a real character comparison. Two independent
 * moduli keep false candidates rare in practice.
 *
 * <p>Complexity: search O(n + m) expected, worst case O(nm) with adversarial
 * collisions; space O(1) (O(m) only for pattern hashing).
 */
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RabinKarp {

    private static final int MOD1 = 1_000_000_007;
    private static final int MOD2 = 1_000_000_009;
    private static final int BASE = 257;

    private final int mod1;
    private final int mod2;
    private final int base;

    public RabinKarp() {
        this(BASE, MOD1, MOD2);
    }

    public RabinKarp(int base, int mod1, int mod2) {
        this.base = base;
        this.mod1 = mod1;
        this.mod2 = mod2;
    }

    /** Hash of a string under a single modulus. */
    public long hashOf(String s, int mod) {
        long h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = (h * base + s.charAt(i)) % mod;
        }
        return h;
    }

    /** Canonical 64-bit fingerprint of a whole report: {@code hash1 << 32 | hash2}. */
    public long fingerprint(String s) {
        return (hashOf(s, mod1) << 32) | (hashOf(s, mod2) & 0xFFFF_FFFFL);
    }

    /** All verified occurrences of {@code pattern} in {@code text}. */
    public static List<Integer> searchAll(String text, String pattern) {
        return searchAll(text, pattern, new RabinKarp());
    }

    /** All verified occurrences, with an explicit hasher. */
    public static List<Integer> searchAll(String text, String pattern, RabinKarp rk) {
        List<Integer> hits = new ArrayList<>();
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || m > n) {
            return hits;
        }
        int[] charVals = toCodes(text);
        int[] patVals = toCodes(pattern);

        long p1 = 0;
        long p2 = 0;
        long pow1 = 1;
        long pow2 = 1;
        for (int i = 0; i < m; i++) {
            p1 = (p1 * rk.base + patVals[i]) % rk.mod1;
            p2 = (p2 * rk.base + patVals[i]) % rk.mod2;
            if (i < m - 1) {
                pow1 = pow1 * rk.base % rk.mod1;
                pow2 = pow2 * rk.base % rk.mod2;
            }
        }

        long h1 = 0;
        long h2 = 0;
        for (int i = 0; i < m; i++) {
            h1 = (h1 * rk.base + charVals[i]) % rk.mod1;
            h2 = (h2 * rk.base + charVals[i]) % rk.mod2;
        }

        for (int i = 0; ; i++) {
            if (h1 == p1 && h2 == p2 && matches(text, pattern, i)) {
                hits.add(i);
            }
            if (i + m >= n) {
                break;
            }
            int out = charVals[i];
            int in = charVals[i + m];
            h1 = ((h1 - out * pow1) % rk.mod1 + rk.mod1) % rk.mod1;
            h1 = (h1 * rk.base + in) % rk.mod1;
            h2 = ((h2 - out * pow2) % rk.mod2 + rk.mod2) % rk.mod2;
            h2 = (h2 * rk.base + in) % rk.mod2;
        }
        return hits;
    }

    /** First verified occurrence, or -1. */
    public static int search(String text, String pattern) {
        List<Integer> hits = searchAll(text, pattern);
        return hits.isEmpty() ? -1 : hits.get(0);
    }

    private static boolean matches(String text, String pattern, int at) {
        for (int i = 0; i < pattern.length(); i++) {
            if (text.charAt(at + i) != pattern.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int[] toCodes(String s) {
        int[] out = new int[s.length()];
        for (int i = 0; i < s.length(); i++) {
            out[i] = s.charAt(i) + 1;
        }
        return out;
    }

    /**
     * Groups strings sharing a fingerprint. DisasterNet uses this to find
     * duplicated incident narratives across reports.
     *
     * @return map from fingerprint to every string carrying it (size > 1 only)
     */
    public static Map<Long, List<String>> findDuplicates(Collection<String> items) {
        RabinKarp rk = new RabinKarp();
        Map<Long, List<String>> buckets = new LinkedHashMap<>();
        for (String item : items) {
            buckets.computeIfAbsent(rk.fingerprint(item), k -> new ArrayList<>()).add(item);
        }
        buckets.values().removeIf(v -> v.size() < 2);
        return buckets;
    }
}
