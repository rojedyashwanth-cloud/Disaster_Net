/*
 * Cross-checks KMP, Z, Rabin-Karp and Aho-Corasick against brute force on fixed and random inputs.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
import java.util.stream.*;

/** Cross-checks every M1StringMatchingTest algorithm against brute force on random and adversarial inputs. */
public class M1StringMatchingTest {
    static int fails = 0;

    static void eq(String what, Object got, Object want) {
        if (!Objects.equals(got, want)) {
            fails++;
            if (fails <= 20) System.out.println("FAIL " + what + ": got " + got + " want " + want);
        }
    }

    static void ok(String what, boolean cond) {
        if (!cond) {
            fails++;
            if (fails <= 20) System.out.println("FAIL " + what);
        }
    }

    /**
     * Every occurrence of pattern in text, by definition. An empty pattern is
     * treated as matching nowhere, which is the convention all four searchers
     * follow: formally an empty string occurs at all n + 1 boundaries, but
     * reporting that would flood every pipeline stage with zero-length hits.
     */
    static List<Integer> brute(String text, String pattern) {
        List<Integer> out = new ArrayList<>();
        if (pattern.isEmpty()) {
            return out;
        }
        for (int i = 0; i + pattern.length() <= text.length(); i++) {
            if (text.startsWith(pattern, i)) out.add(i);
        }
        return out;
    }

    static List<Integer> brutePrefix(String text, String pattern) {
        return brute(text, pattern).stream().filter(i -> i <= text.length() - pattern.length()).collect(Collectors.toList());
    }

    /** Greedy left-to-right selection of non-overlapping hits. */
    static int greedyNonOverlapping(List<Integer> sortedHits, int patternLength) {
        int count = 0, nextAllowed = 0;
        for (int hit : sortedHits) {
            if (hit >= nextAllowed) {
                count++;
                nextAllowed = hit + patternLength;
            }
        }
        return count;
    }

    static String rand(int n, int alpha, Random r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append((char) ('a' + r.nextInt(alpha)));
        return sb.toString();
    }

    public static void main(String[] args) {
        Random r = new Random(7);
        List<String> corpus = new ArrayList<>(List.of("", "a", "aa", "aaa", "ab", "aba", "abab", "ababa",
                "banana", "ananas", "ananasusegel", "aaaaa", "aabaaab", "xyzxyzxyz", "mississippi",
                "The quick brown fox", "GCATCGCAGAGAGTATACAGTACG", "abababab", "aaaaaaaaaaaaaaaab"));

        // ---- prefix function, checked against the definition ----
        for (String s : corpus) {
            int[] pi = KMP.prefixFunction(s);
            eq("prefixFunction length " + s, pi.length, s.length());
            if (pi.length > 0) eq("prefixFunction[0] " + s, pi[0], 0);
            for (int i = 1; i < s.length(); i++) {
                // A border of length k is a prefix of the whole string and a suffix
                // of s[0..i], and k can be at most i since the border must be proper.
                int best = 0;
                for (int k = i; k >= 1; k--) {
                    if (s.substring(0, k).equals(s.substring(i + 1 - k, i + 1))) { best = k; break; }
                }
                eq("pi[" + i + "] of " + s, pi[i], best);
            }
            // z[0] is the length, by this class's convention.
            if (!s.isEmpty()) eq("z[0] of " + s, ZAlgorithm.zArray(s)[0], s.length());
        }
        for (int t = 0; t < 3000; t++) {
            String s = rand(r.nextInt(12), 2 + r.nextInt(3), r);
            int[] pi = KMP.prefixFunction(s);
            if (pi.length > 0) eq("random prefixFunction[0] " + s, pi[0], 0);
            for (int i = 1; i < s.length(); i++) {
                // The KMP invariant: a border can grow by at most one per position.
                ok("pi grows by at most one " + s + "@" + i, pi[i] <= pi[i - 1] + 1);
                ok("pi[" + i + "] <= i " + s, pi[i] <= i);
                // pi[i] is a border of s[0..i], so it must be a real border.
                if (pi[i] > 0) {
                    eq("pi is a border " + s + "@" + i,
                            s.substring(i + 1 - pi[i], i + 1), s.substring(0, pi[i]));
                }
            }
        }

        // ---- Z array, checked against naive computation ----
        for (String s : corpus) {
            int[] z = ZAlgorithm.zArray(s);
            eq("z length " + s, z.length, s.length());
            for (int i = 0; i < s.length(); i++) {
                int k = 0;
                while (i + k < s.length() && s.charAt(i + k) == s.charAt(k)) k++;
                eq("z[" + i + "] of " + s, z[i], k);
            }
        }

        // ---- KMP, Z, Rabin-Karp must agree with brute force, including overlaps ----
        int searched = 0;
        for (String text : corpus) {
            for (String pattern : corpus) {
                List<Integer> want = brutePrefix(text, pattern);
                List<Integer> kmp = KMP.searchAll(text, pattern);
                List<Integer> zed = ZAlgorithm.searchAll(text, pattern);
                List<Integer> zed2 = ZAlgorithm.searchAllZ(text, pattern);
                List<Integer> rk = RabinKarp.searchAll(text, pattern);
                eq("KMP all " + text + "/" + pattern, kmp, want);
                eq("Z all " + text + "/" + pattern, zed, want);
                eq("Z searchAllZ " + text + "/" + pattern, zed2, want);
                eq("RK all " + text + "/" + pattern, rk, want);
                // countMatches is greedy NON-OVERLAPPING by documented contract,
                // so it must be checked against the greedy count, not against
                // searchAll().size().
                eq("KMP count " + text + "/" + pattern, KMP.countMatches(text, pattern), greedyNonOverlapping(want, pattern.length()));
                eq("KMP first " + text + "/" + pattern, KMP.search(text, pattern),
                        want.isEmpty() ? -1 : want.get(0));
                eq("RK first " + text + "/" + pattern, RabinKarp.search(text, pattern),
                        want.isEmpty() ? -1 : want.get(0));
                eq("Z first " + text + "/" + pattern, ZAlgorithm.search(text, pattern),
                        want.isEmpty() ? -1 : want.get(0));
                eq("KMP contains " + text + "/" + pattern, KMP.contains(text, pattern), !want.isEmpty());
                searched++;
            }
        }
        for (int t = 0; t < 20000; t++) {
            int tl = r.nextInt(30), pl = r.nextInt(5);
            String text = rand(tl, 2 + r.nextInt(3), r);
            String pattern = rand(pl, 2 + r.nextInt(3), r);
            List<Integer> want = brutePrefix(text, pattern);
            eq("KMP random", KMP.searchAll(text, pattern), want);
            eq("Z random", ZAlgorithm.searchAll(text, pattern), want);
            eq("Z random 2", ZAlgorithm.searchAllZ(text, pattern), want);
            eq("RK random", RabinKarp.searchAll(text, pattern), want);
            eq("RK random custom hash",
                    RabinKarp.searchAll(text, pattern, new RabinKarp(31, 1_000_003, 1_000_033)), want);
            eq("RK first random", RabinKarp.search(text, pattern), want.isEmpty() ? -1 : want.get(0));
            searched++;
        }
        // ---- the empty-pattern convention, stated rather than assumed ----
        for (String text : new String[] {"", "a", "banana"}) {
            eq("KMP rejects empty pattern on " + text, KMP.searchAll(text, ""), List.of());
            eq("Z rejects empty pattern on " + text, ZAlgorithm.searchAll(text, ""), List.of());
            eq("RK rejects empty pattern on " + text, RabinKarp.searchAll(text, ""), List.of());
            eq("KMP contains empty pattern on " + text, KMP.contains(text, ""), false);
            eq("RK first empty pattern on " + text, RabinKarp.search(text, ""), -1);
            eq("Z first empty pattern on " + text, ZAlgorithm.search(text, ""), -1);
        }
        eq("KMP long text empty pattern", KMP.searchAll("abcdef".repeat(1000), ""), List.of());
        eq("KMP count long text empty pattern", KMP.countMatches("abcdef".repeat(1000), ""), 0);
        // AhoCorasick skips null and empty patterns rather than inserting a
        // zero-length terminal that would match at every position.
        eq("AC skips empty pattern", new AhoCorasick(List.of("")).patterns(), List.of());
        eq("AC skips null pattern", new AhoCorasick(Arrays.asList((String) null, "ab")).patterns(), List.of("ab"));
        eq("AC keeps usable patterns", new AhoCorasick(Arrays.asList("", "ab", null, "b")).patterns().size(), 2);

        System.out.println("cross-checked " + searched + " (text, pattern) pairs across KMP, Z and Rabin-Karp");

        // ---- Rabin-Karp fingerprint sanity: equal strings hash equally ----
        RabinKarp rk = new RabinKarp();
        for (String s : corpus) {
            long h = rk.fingerprint(s);
            eq("fingerprint stable " + s, h, rk.fingerprint(s));
            eq("fingerprint prefix mod " + s, rk.hashOf(s, 1_000_003), rk.hashOf(s, 1_000_003));
            ok("fingerprint nonnegative " + s, h >= 0);
        }
        eq("fingerprint agrees with hashOf on both default mods", rk.fingerprint("banana"),
                (rk.hashOf("banana", 1_000_000_007) << 32) | (rk.hashOf("banana", 1_000_000_009) & 0xFFFF_FFFFL));
        eq("fingerprint is collision-free on the corpus",
                new HashSet<>(corpus).size(), new HashSet<>(corpus.stream().map(rk::fingerprint).toList()).size());

        // ---- Rabin-Karp duplicate detection must agree with a naive grouping ----
        for (int t = 0; t < 500; t++) {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 2 + r.nextInt(40); i++) items.add(rand(r.nextInt(4), 2, r));
            // findDuplicates reports only fingerprints carrying more than one item,
            // so the naive comparison must bucket the same way.
            Map<String, List<String>> naive = new LinkedHashMap<>();
            for (String s : items) naive.computeIfAbsent(s, k -> new ArrayList<>()).add(s);
            Map<String, List<String>> expected = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : naive.entrySet()) {
                if (e.getValue().size() > 1) expected.put(e.getKey(), e.getValue());
            }
            Map<Long, List<String>> byHash = RabinKarp.findDuplicates(items);
            eq("dup group count " + items, byHash.size(), expected.size());
            // Every reported group must hold one repeated value and nothing else:
            // a bucket holding two different values would be a hash collision.
            for (Map.Entry<Long, List<String>> e : byHash.entrySet()) {
                ok("dup bucket has >1 item " + e.getValue(), e.getValue().size() > 1);
                String only = e.getValue().get(0);
                for (String s : e.getValue()) eq("dup bucket value " + only, s, only);
                eq("dup group size " + only, e.getValue().size(), naive.get(only).size());
            }
            Set<String> reported = new HashSet<>();
            for (List<String> v : byHash.values()) reported.add(v.get(0));
            eq("dup reported set " + items, reported, new HashSet<>(expected.keySet()));
        }

        // ---- Aho-Corasick must report every occurrence of every pattern ----
        for (String text : corpus) {
            List<String> pats = List.of("a", "an", "ana", "nan", "as", "banana", "zzz");
            AhoCorasick ac = new AhoCorasick(pats, false);
            List<AhoCorasick.Match> matches = ac.search(text);
            // Expected: one match per (pattern, occurrence), ordered by start then end.
            List<AhoCorasick.Match> expected = new ArrayList<>();
            for (String p : pats) for (int i : brutePrefix(text, p)) expected.add(new AhoCorasick.Match(p, i, i + p.length(), null));
            eq("AC match count on " + text, matches.size(), expected.size());
            Set<String> gotKeys = new TreeSet<>(), wantKeys = new TreeSet<>();
            for (AhoCorasick.Match m : matches) gotKeys.add(m.pattern() + "@" + m.start());
            for (AhoCorasick.Match m : expected) wantKeys.add(m.pattern() + "@" + m.start());
            eq("AC match set on " + text, gotKeys, wantKeys);
            for (AhoCorasick.Match m : matches) {
                eq("AC span on " + text + "@" + m.start(), text.substring(m.start(), m.end()), m.pattern());
                eq("AC length() on " + text, m.length(), m.pattern().length());
            }
            // hitCounts must agree with the matches
            Map<String, Integer> counts = new HashMap<>();
            for (AhoCorasick.Match m : matches) counts.merge(m.pattern(), 1, Integer::sum);
            for (String p : pats) eq("AC hitCount " + p + " on " + text, ac.hitCounts(text).getOrDefault(p, 0), counts.getOrDefault(p, 0));
            eq("AC matchesAny on " + text, ac.matchesAny(text), !matches.isEmpty());
            eq("AC firstMatch on " + text, ac.firstMatch(text), matches.isEmpty() ? null : matches.get(0).pattern());
        }

        // ---- Aho-Corasick on many random pattern sets, versus brute force ----
        for (int t = 0; t < 400; t++) {
            String text = rand(r.nextInt(40), 3, r);
            List<String> pats = new ArrayList<>();
            for (int i = 0; i < 1 + r.nextInt(5); i++) pats.add(rand(1 + r.nextInt(3), 3, r));
            AhoCorasick ac = new AhoCorasick(pats, false);
            List<AhoCorasick.Match> got = ac.search(text);
            TreeSet<String> gotKeys = new TreeSet<>();
            for (AhoCorasick.Match m : got) gotKeys.add(m.pattern() + "@" + m.start());
            TreeSet<String> wantKeys = new TreeSet<>();
            for (String p : pats) for (int i : brutePrefix(text, p)) wantKeys.add(p + "@" + i);
            eq("AC random set", gotKeys, wantKeys);
            ok("AC nodes >= distinct patterns", ac.nodeCount() >= new HashSet<>(pats).size());
            eq("AC patterns round-trip", new HashSet<>(ac.patterns()), new HashSet<>(pats));
        }

        // ---- Aho-Corasick labels and case folding ----
        AhoCorasick ac = new AhoCorasick(List.of("he", "she", "his", "hers"), true);
        eq("AC case-insensitive count", ac.search("ushers").size(), 3);
        List<AhoCorasick.Match> labelled = ac.search("ushers", "corpus-1");
        for (AhoCorasick.Match m : labelled) eq("AC label", m.textLabel(), "corpus-1");
        eq("AC case-sensitive misses", new AhoCorasick(List.of("he"), false).search("HE").size(), 0);
        eq("AC case-insensitive hits", new AhoCorasick(List.of("he")).search("HE").size(), 1);
        eq("AC matchesAny is false when nothing matches", new AhoCorasick(List.of("he"), false).matchesAny("xyz"), false);
        eq("AC matchesAny is true on a hit", new AhoCorasick(List.of("he"), false).matchesAny("xyzhexyz"), true);
        eq("AC matchesAny false on empty text", new AhoCorasick(List.of("he")).matchesAny(""), false);
        eq("AC matchesAny agrees with search on a late hit",
                new AhoCorasick(List.of("zzz"), false).matchesAny("abcabcabczzz"), true);
        eq("AC matchesAny agrees with search on a miss after a prefix",
                new AhoCorasick(List.of("abcx"), false).matchesAny("abcabcab"), false);

        // ---- longestMatch, LCP and overlap helpers ----
        List<String> multi = List.of("abcd", "bc", "zz", "cde");
        eq("KMP longestMatch", KMP.longestMatch("xxabcdxxbc", multi), "abcd");
        // Length wins over position: the documented severity-keyword case.
        eq("longestMatch prefers the longer keyword later on",
                KMP.longestMatch("evac then evacuate", List.of("evac", "evacuate")), "evacuate");
        eq("longestMatch tie breaks on earliest position",
                KMP.longestMatch("abc abc", List.of("abc", "bca")), "abc");
        eq("longestMatch ignores absent longer patterns",
                KMP.longestMatch("evac", List.of("evac", "evacuate")), "evac");
        eq("longestMatch on empty pattern list", KMP.longestMatch("abc", List.of()), null);
        eq("KMP longestMatch none", KMP.longestMatch("qqq", multi), null);
        eq("LCP banana/ananas", ZAlgorithm.longestCommonPrefix("banana", "ananas"), 0);
        eq("LCP banana/anana", ZAlgorithm.longestCommonPrefix("banana", "anana"), 0);
        eq("LCP a/anana", ZAlgorithm.longestCommonPrefix("a", "anana"), 1);
        eq("LCP identical", ZAlgorithm.longestCommonPrefix("abc", "abc"), 3);
        eq("LCP disjoint", ZAlgorithm.longestCommonPrefix("abc", "xyz"), 0);
        eq("LCP empty left", ZAlgorithm.longestCommonPrefix("", "abc"), 0);
        eq("overlap abab/ababc", ZAlgorithm.overlapLength("abab", "ababc"), 4);
        eq("overlap abc/c", ZAlgorithm.overlapLength("abc", "c"), 1);
        eq("overlap none", ZAlgorithm.overlapLength("abc", "xyz"), 0);
        for (int t = 0; t < 500; t++) {
            String a = rand(r.nextInt(6), 3, r), b = rand(r.nextInt(6), 3, r);
            int lcp = 0;
            while (lcp < a.length() && lcp < b.length() && a.charAt(lcp) == b.charAt(lcp)) lcp++;
            eq("LCP random " + a + "/" + b, ZAlgorithm.longestCommonPrefix(a, b), lcp);
            int ov = 0;
            for (int k = Math.min(a.length(), b.length()); k >= 1; k--) {
                if (a.endsWith(b.substring(0, k))) { ov = k; break; }
            }
            eq("overlap random " + a + "/" + b, ZAlgorithm.overlapLength(a, b), ov);
        }

        System.out.println(fails == 0 ? "M1StringMatchingTest ALL PASS" : "M1StringMatchingTest FAILURES: " + fails);
    }
}
