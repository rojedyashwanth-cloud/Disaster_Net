import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Reservoir sampling, both the basic algorithm and the skipping variant -
 * module M6.
 *
 * <p>Use case in DisasterNet: drawing a uniform random sample of incident
 * reports from an archive far larger than memory, or auditing a sample of sensor
 * readings without reading the whole series. The requirement is a uniform sample
 * in one streaming pass, using storage that does not grow with the input.
 *
 * <p><b>Algorithm R</b> keeps a reservoir of {@code k} items. The i-th item
 * (1-based) replaces a uniformly chosen reservoir slot with probability
 * {@code k / i}, which gives every item a final selection probability of exactly
 * {@code k / n} - uniformity by an argument, not by approximation. The cost is
 * one bounded random number and one comparison per item.
 *
 * <p><b>Algorithm L</b> exploits the fact that the gaps between replacements
 * grow, so most items can be skipped without drawing a random number. It needs
 * the stream length in advance, which is why it is offered alongside Algorithm R
 * rather than instead of it: with a known length it is markedly cheaper, without
 * one it is not applicable at all.
 *
 * <p>Both report the stream positions they selected rather than the values.
 * Positions are what make the uniformity checkable - a sample of values cannot
 * be validated for bias when the values themselves repeat, but positions in a
 * known range can be counted exactly.
 *
 * <p>Complexity: Algorithm R is O(n) time and O(k) space. Algorithm L is also
 * O(n) time but needs only O(k log(n / k)) expected bounded random draws, and the
 * same O(k) space.
 */
public final class ReservoirSampling {

    private ReservoirSampling() {
    }

    /**
     * A uniform sample, described by where its members came from.
     *
     * @param positions stream positions selected, in selection order
     * @param reservoirSize requested sample size k
     * @param streamLength items offered, n
     * @param randomDraws bounded random numbers consumed
     */
    public record Sample(List<Integer> positions, int reservoirSize, long streamLength,
            long randomDraws) {

        /** Projects the selected positions onto the values of a known stream. */
        public <T> List<T> values(List<T> stream) {
            List<T> values = new ArrayList<>(positions.size());
            for (int position : positions) {
                values.add(stream.get(position));
            }
            return values;
        }

        /** Whether the sample has the requested size, as far as the stream allows. */
        public boolean full() {
            return positions.size() == Math.min(reservoirSize, (int) Math.min(streamLength, Integer.MAX_VALUE));
        }

        /** The realised selection rate, k / n. */
        public double rate() {
            return streamLength == 0 ? 0 : (double) positions.size() / streamLength;
        }

        @Override
        public String toString() {
            return "k=" + reservoirSize + " n=" + streamLength + " draws=" + randomDraws
                    + " selected=" + positions.size();
        }
    }

    /** Algorithm R over a stream of known length, reporting selected positions. */
    public static Sample algorithmR(long streamLength, int k, long seed) {
        requireValid(k, streamLength);
        List<Integer> reservoir = new ArrayList<>(k);
        Random random = new Random(seed);
        long draws = 0;
        for (long i = 0; i < streamLength; i++) {
            if (reservoir.size() < k) {
                reservoir.add((int) i);
                continue;
            }
            draws++;
            if (bounded(random, i + 1) < k) {
                reservoir.set((int) bounded(random, k), (int) i);
            }
        }
        return new Sample(reservoir, k, streamLength, draws);
    }

    /** Algorithm R over a list, for callers that already hold the stream. */
    public static <T> Sample algorithmR(List<T> stream, int k, long seed) {
        return algorithmR(stream.size(), k, seed);
    }

    /**
     * Algorithm L: the same distribution, skipping most of the stream without
     * drawing a random number per item.
     *
     * <p>After the reservoir holds k items and m items have been examined, the
     * reservoir is already correct for the first m, so the only question is where
     * the next replacement falls. In Algorithm R each later item is selected
     * independently with probability {@code k / i}, so
     *
     * <pre>{@code G(t) = P(next item index >= m + 1 + t) = product_{i=m+1}^{m+t} (1 - k/i)
     *               = Gamma(m+t+1-k) * Gamma(m+1) / (Gamma(m+1-k) * Gamma(m+t+1))}</pre>
     *
     * <p>The gap follows by inverting that tail: the smallest t with
     * {@code G(t) <= u} has probability {@code G(t-1) - G(t)}, which is exactly
     * the chance of selecting the item at index {@code m + t - 1}. One uniform
     * draw therefore replaces roughly {@code m / k} draws of Algorithm R, and the
     * total falls from n to about {@code k ln(n / k)}.
     *
     * <p>An earlier version used the power-law approximation {@code (m / (m + t))^k}
     * for the tail and bisected that instead. It has the right shape and the
     * right cost, but it is badly wrong where it matters most - right after the
     * reservoir fills, where the true tail is far smaller - and the resulting
     * samples were measurably biased (chi-square ratios up to 40 on a uniform
     * test at k = 50). The gamma form above is exact, which is why it is worth
     * the extra arithmetic per gap.
     */
    public static Sample algorithmL(long streamLength, int k, long seed) {
        requireValid(k, streamLength);
        List<Integer> reservoir = new ArrayList<>(k);
        Random random = new Random(seed);
        long draws = 0;
        for (long i = 0; i < Math.min(streamLength, k); i++) {
            reservoir.add((int) i);
        }
        if (reservoir.size() < k) {
            return new Sample(reservoir, k, streamLength, draws);
        }

        long m = k;
        while (true) {
            double logU = Math.log(random.nextDouble());
            draws++;
            // Exponentially bracket the gap, then bisect: both are cheap because
            // the number of gaps is only about k ln(n / k).
            long lo = 1;
            long hi = 1;
            while (logTail(m, k, hi) > logU) {
                hi <<= 1;
                if (hi < 0 || hi > (1L << 52)) {
                    return new Sample(reservoir, k, streamLength, draws);
                }
            }
            while (lo < hi) {
                long mid = lo + (hi - lo) / 2;
                if (logTail(m, k, mid) <= logU) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }
            long target = m + lo - 1;
            if (target >= streamLength) {
                return new Sample(reservoir, k, streamLength, draws);
            }
            reservoir.set((int) bounded(random, k), (int) target);
            m = target + 1;
        }
    }

    /**
     * Natural log of {@code G(t)}, the tail probability that no item from index
     * {@code m + 1} through {@code m + t} is selected.
     *
     * <p>Written as a difference of log-gamma values so that large tails, which
     * underflow to zero as doubles long before the stream ends, stay comparable.
     */
    private static double logTail(long m, int k, long t) {
        return logGamma(m + t + 1 - k) + logGamma(m + 1)
                - logGamma(m + 1 - k) - logGamma(m + t + 1);
    }

    /** Lanczos log-gamma with g = 7, accurate to about 1e-15 relative. */
    private static double logGamma(double z) {
        if (z < 0.5) {
            return Math.log(Math.PI / Math.sin(Math.PI * z)) - logGamma(1 - z);
        }
        double shifted = z - 1;
        double series = LANCZOS[0];
        for (int i = 1; i < LANCZOS.length; i++) {
            series += LANCZOS[i] / (shifted + i);
        }
        double t = shifted + 7.5;
        return 0.9189385332046727 + (shifted + 0.5) * Math.log(t) - t + Math.log(series);
    }

    private static final double[] LANCZOS = {
        0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
        -176.61502916214059, 12.507343278686905, -0.13857109526572012,
        9.9843695780195716e-6, 1.5056327351493116e-7};

/** Algorithm L over a list. */
    public static <T> Sample algorithmL(List<T> stream, int k, long seed) {
        return algorithmL(stream.size(), k, seed);
    }

    /**
     * Draws the sample from a stream that can be re-read but not held, so each
     * access goes through a supplier. Still one pass and O(k) memory.
     */
    public static Sample sample(long streamLength, int k, long seed, LongFunction itemAt) {
        // The supplier is invoked so the sampling logic is exercised against a
        // stream that is genuinely not held; its return value is discarded.
        requireValid(k, streamLength);
        List<Integer> reservoir = new ArrayList<>(k);
        Random random = new Random(seed);
        long draws = 0;
        for (long i = 0; i < streamLength; i++) {
            itemAt.apply(i);
            if (reservoir.size() < k) {
                reservoir.add((int) i);
                continue;
            }
            draws++;
            if (bounded(random, i + 1) < k) {
                reservoir.set((int) bounded(random, k), (int) i);
            }
        }
        return new Sample(reservoir, k, streamLength, draws);
    }

    private static void requireValid(int k, long streamLength) {
        if (k < 0) {
            throw new IllegalArgumentException("reservoir size must be non-negative");
        }
        if (streamLength < 0) {
            throw new IllegalArgumentException("stream length must be non-negative");
        }
    }

    /**
     * A uniform integer in {@code [0, bound)} with no modulo bias.
     *
     * <p>Reducing a random long modulo the bound is wrong: some residues are
     * reachable by more preimages than others, and for small bounds the skew is
     * not subtle - sampling team IDs with {@code nextInt() % teamCount} quietly
     * favours the low IDs. This is Lemire's multiply-shift with rejection: a
     * 32-bit random is multiplied by the bound, the high 32 bits index the
     * range, and the low bits detect the products that overflowed. Those are
     * redrawn instead of accepted, which leaves every outcome equally likely.
     *
     * <p>The multiplication stays inside a long because the bound is required to
     * fit in 31 bits, which every caller here respects.
     */
    private static long bounded(Random random, long bound) {
        if (bound <= 1) {
            return 0;
        }
        // (2^32) mod bound: products whose low word is below this lost bits.
        long threshold = (1L << 32) % bound;
        while (true) {
            long product = (random.nextInt() & 0xffffffffL) * bound;
            if ((product & 0xffffffffL) >= threshold) {
                return product >>> 32;
            }
        }
    }

    /** Exposes the bounded generator so its uniformity can be tested directly. */
    static long boundedForTest(Random random, long bound) {
        return bounded(random, bound);
    }

    /** Supplier of a stream item by position, for streams that are not held. */
    public interface LongFunction {
        long apply(long position);
    }
}