import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Brent's cycle detection, and Floyd's algorithm beside it for comparison -
 * module M6.
 *
 * <p>Repeatedly applying a function {@code f} to a value must eventually revisit a
 * value if the trajectory enters a cycle. Both algorithms find that cycle without
 * storing anything, which is the whole point: a hash table answers the same
 * question in one pass but costs {@code O(n)} memory, and the interesting case is
 * usually a trajectory too large to keep.
 *
 * <p>Use case in DisasterNet: a team is moved around a shifting set of districts
 * and eventually revisits a district it has already served, so the cycle in its
 * route is what to report. Also a bounded check on any hash-based pipeline: if a
 * state repeats, the loop will never terminate on its own.
 *
 * <h2>The three algorithms</h2>
 * <ul>
 *   <li><b>Floyd's tortoise and hare</b> moves one pointer one step and the other
 *       two. On entry it costs three times as many {@code f} applications as a
 *       single walk, and it needs no memory at all.</li>
 *   <li><b>Brent's</b> keeps one pointer that advances in blocks of doubling
 *       length while a second pointer stands still, so it uses no memory and makes
 *       only slightly more calls than a single walk - never more than about twice
 *       the cycle length in total.</li>
 *   <li><b>The hash table</b> is the obvious method, kept here as the reference the
 *       other two are checked against.</li>
 * </ul>
 *
 * <p>Brent's guarantee is the interesting part. Once the block length exceeds the
 * cycle length and the stationary pointer is inside the cycle, the moving pointer
 * must land back on it within one more block - so the number of calls is bounded
 * by a small multiple of {@code mu + lambda}, where {@code mu} is the tail length
 * and {@code lambda} the cycle length. Neither Brent nor Floyd is linear in
 * {@code n} in the worst case; both are {@code O(mu + lambda)}.
 */
public final class BrentAlgorithm {

    private BrentAlgorithm() {
    }

    /**
     * What cycle detection found.
     *
     * @param found whether the trajectory ever repeated
     * @param index the first index of the cycle, that is its tail length
     * @param length the cycle's length
     * @param steps how many applications of the function were needed
     * @param method which algorithm produced this
     */
    public record CycleResult(boolean found, int index, int length, long steps, String method) {

        @Override
        public String toString() {
            if (!found) {
                return method + ": no cycle within the limit (" + steps + " steps)";
            }
            return method + ": enters a cycle of length " + length
                    + " at index " + index + " (" + steps + " steps)";
        }
    }

    /**
     * Brent's algorithm on {@code f} starting from {@code start}.
     *
     * <p>One pointer stands still while the other advances in blocks whose length
     * doubles: 1, 2, 4, 8, and so on. When the block length first exceeds the
     * cycle length and the stationary pointer is inside the cycle, the moving
     * pointer must land back on it within one more block, which is where the loop
     * stops. {@code i} is then exactly the cycle length.
     *
     * <p>No memory is used beyond two references, and the number of applications
     * of {@code f} is bounded by about {@code 2 * (mu + lambda)} rather than
     * Floyd's {@code 3 * (mu + lambda)}.
     *
     * @param f the function to iterate; must be pure, since the algorithm visits
     *     states more than once and expects the same answer each time
     * @param start the starting value
     * @param limit a ceiling on applications of f, so a non-cycling function
     *     terminates instead of running forever
     */
    public static <T> CycleResult brent(Function<T, T> f, T start, long limit) {
        T checkpoint = start;
        T hare = f.apply(start);
        long steps = 1;
        long block = 1;
        // One step has already been taken, so a fixed point has cycle length 1
        // and not 0. Starting i at zero understates every length by one.
        long i = 1;

        while (!brentEqual(checkpoint, hare)) {
            if (steps >= limit) {
                return new CycleResult(false, -1, -1, steps, "Brent");
            }
            if (i == block) {
                // The block just finished without a repeat, so move the checkpoint
                // and double the block. Eventually a block outlasts the cycle.
                checkpoint = hare;
                block *= 2;
                i = 0;
            }
            hare = f.apply(hare);
            steps++;
            i++;
        }
        int length = (int) i;
        return new CycleResult(true, brentIndex(f, start, length, limit), length, steps, "Brent");
    }

    /**
     * Floyd's tortoise and hare, same contract as {@link #brent}.
     *
     * <p>Kept because it is the algorithm most people meet first, and because the
     * two agreeing on the cycle while disagreeing on step counts is the clearest
     * demonstration that Brent's saving is in calls, not in correctness.
     */
    public static <T> CycleResult floyd(Function<T, T> f, T start, long limit) {
        long steps = 0;
        T tortoise = start;
        T hare = start;
        do {
            tortoise = f.apply(tortoise);
            steps++;
            if (steps > limit) {
                return new CycleResult(false, -1, -1, steps, "Floyd");
            }
            hare = f.apply(f.apply(hare));
            steps += 2;
            if (steps > limit) {
                return new CycleResult(false, -1, -1, steps, "Floyd");
            }
        } while (!brentEqual(tortoise, hare));

        int mu = 0;
        tortoise = start;
        while (!brentEqual(tortoise, hare)) {
            tortoise = f.apply(tortoise);
            hare = f.apply(hare);
            mu++;
            steps += 2;
            if (steps > limit) {
                return new CycleResult(false, -1, -1, steps, "Floyd");
            }
        }
        int lambda = 1;
        hare = f.apply(tortoise);
        steps++;
        while (!brentEqual(tortoise, hare)) {
            hare = f.apply(hare);
            lambda++;
            steps++;
            if (steps > limit) {
                return new CycleResult(false, -1, -1, steps, "Floyd");
            }
        }
        return new CycleResult(true, mu, lambda, steps, "Floyd");
    }

    /**
     * The hash-table method: one pass, recording the first index of each value.
     *
     * <p>Not space-efficient, which is exactly why Brent exists, but it is the
     * reference the other two are verified against.
     */
    public static <T> CycleResult hashTable(Function<T, T> f, T start, long limit) {
        Map<T, Integer> firstSeen = new HashMap<>();
        T current = start;
        long steps = 0;
        int index = 0;
        while (steps < limit) {
            Integer previous = firstSeen.putIfAbsent(current, index);
            if (previous != null) {
                return new CycleResult(true, previous, index - previous, steps, "Hash table");
            }
            current = f.apply(current);
            steps++;
            index++;
        }
        return new CycleResult(false, -1, -1, steps, "Hash table");
    }

    /**
     * The cycle's entry index, given that the cycle's length is already known.
     *
     * <p>Brent's checkpoint is <em>not</em> the cycle entry: it is whatever value
     * the moving pointer happened to return to, somewhere inside the cycle. An
     * earlier version walked forward looking for that specific value and reported
     * its distance from the start, which is the cycle entry only when the
     * checkpoint lands on the first element - it reported 255 where the answer was
     * 1 for the trajectory {@code 0, 1, 2, ..., 256, 1, 2, ...}.
     *
     * <p>Instead, keep one pointer exactly {@code lambda} steps ahead of the
     * other. Advancing both by one preserves that separation, so the first moment
     * they coincide is the first moment the leading pointer is inside the cycle -
     * which is the cycle entry. Cost is {@code O(lambda + mu)} time and no memory
     * at all, and it works from any length.
     */
    private static <T> int brentIndex(Function<T, T> f, T start, int length, long bound) {
        T behind = start;
        T ahead = start;
        for (int i = 0; i < length; i++) {
            ahead = f.apply(ahead);
        }
        int index = 0;
        while (!brentEqual(behind, ahead)) {
            if (index > bound) {
                return -1;
            }
            behind = f.apply(behind);
            ahead = f.apply(ahead);
            index++;
        }
        return index;
    }

    /** Equality that tolerates a null trajectory value. */
    private static boolean brentEqual(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    /**
     * Whether steps is a power of two: 1, 2, 4, 8, and so on.
     *
     * <p>Exactly the schedule that makes Brent's algorithm work, and the reason it
     * needs no memory: the block length doubles only when a block has just failed
     * to find the cycle, so eventually a block is longer than the cycle itself.
     */
    public static boolean isPowerOfTwo(long steps) {
        return steps > 0 && (steps & (steps - 1)) == 0;
    }

    /** The trajectory from {@code start} up to {@code limit} applications of f. */
    public static <T> List<T> trajectory(Function<T, T> f, T start, int limit) {
        List<T> values = new ArrayList<>(Math.min(limit, 1024));
        T current = start;
        for (int i = 0; i < limit; i++) {
            values.add(current);
            current = f.apply(current);
        }
        return values;
    }

    /**
     * The cycle's length read straight off a recorded trajectory: the first
     * repeated value's distance back to its first occurrence. Used by the tests
     * as an independent check on both algorithms.
     */
    public static <T> int cycleLengthOf(List<T> trajectory) {
        Map<T, Integer> firstSeen = new HashMap<>();
        for (int i = 0; i < trajectory.size(); i++) {
            Integer previous = firstSeen.putIfAbsent(trajectory.get(i), i);
            if (previous != null) {
                return i - previous;
            }
        }
        return -1;
    }

    /** The cycle's entry index read straight off a recorded trajectory. */
    public static <T> int cycleIndexOf(List<T> trajectory) {
        Map<T, Integer> firstSeen = new HashMap<>();
        for (int i = 0; i < trajectory.size(); i++) {
            Integer previous = firstSeen.putIfAbsent(trajectory.get(i), i);
            if (previous != null) {
                return previous;
            }
        }
        return -1;
    }
}
