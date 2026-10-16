import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.BinaryOperator;

/**
 * Blelloch's parallel prefix scan, in reduce-then-scan form - module M6.
 *
 * <p>A prefix scan replaces every element with the combination of everything
 * before it, so from {@code [a b c d]} an exclusive scan produces
 * {@code [0 a a+b a+b+c]}. It is the primitive behind parallel sums, ranks,
 * stream compaction and radix-sort histograms, which is why it gets its own
 * class instead of hiding inside a sorting routine.
 *
 * <p>Use case in DisasterNet: turning per-district damage totals into cumulative
 * totals, ranking teams by resources deployed, and giving each field observation
 * its position in a sorted report without a sequential pass.
 *
 * <h2>Why two arrays</h2>
 * <p>The scan needs a reduce phase and a scan phase. Reducing in place is fine,
 * but afterwards the scan phase faces a tree of partial totals in which a block
 * no longer holds the values it started with. Textbook treatments dodge this by
 * making every leaf a single element, which creates one task per element and
 * spends more time in the scheduler than in the arithmetic.
 *
 * <p>So the reduction runs on a working copy and the scan phase reads the
 * untouched input. That costs one extra array and buys a cutoff of a few hundred
 * elements per task, inside which a plain sequential loop does the work. Total
 * work stays linear at roughly {@code 3n} operations, with {@code O(log n)} depth
 * and no sequential bottleneck at any level.
 *
 * <p>Every node's total lives in the <i>last</i> slot of its range. That is the
 * one convention under which both phases agree: the reduce phase produces
 * {@code work[hi - 1]}, and the scan phase reads {@code work[mid - 1]} for the
 * left child.
 *
 * <p>Correctness is not inferred from the structure being recognisable. The
 * parallel result is compared against a plain left-to-right loop for every size
 * up to 600 and thousands of random inputs, and the work counter is asserted to
 * be linear.
 *
 * <p>Worth stating plainly: on an {@code int} sum this is often <em>slower</em>
 * than the sequential loop, and the tests show it. A scan touches every element
 * twice and the sequential version already saturates memory bandwidth on one
 * core, so forking buys scheduling overhead and nothing else. The parallelism
 * pays off when the operator is expensive per element or when several scans share
 * a machine - not because parallel is inherently faster here.
 */
public final class BlellochScan {

    /** Elements a task handles in one sequential pass before forking. */
    private static final int SEQUENTIAL_THRESHOLD = 256;

    private BlellochScan() {
    }

    /**
     * A scan's outcome: the scanned values, the total, the operations performed
     * and the deepest recursion reached.
     */
    public record ScanResult(int[] values, int total, long operations, int maxDepth) {

        /** Whether the scanned array is non-decreasing, as any prefix sum must be. */
        public boolean monotone() {
            for (int i = 1; i < values.length; i++) {
                if (values[i] < values[i - 1]) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Sequential exclusive scan: the definition every parallel version must match. */
    public static int[] scanSequential(int[] input) {
        int[] output = new int[input.length];
        int running = 0;
        for (int i = 0; i < input.length; i++) {
            output[i] = running;
            running += input[i];
        }
        return output;
    }

    /** The total of the array. */
    public static int total(int[] input) {
        int sum = 0;
        for (int v : input) {
            sum += v;
        }
        return sum;
    }

    /** Exclusive scan on the common pool. */
    public static ScanResult scan(int[] input) {
        return scan(input, ForkJoinPool.commonPool());
    }

    /**
     * Parallel exclusive scan in the supplied pool.
     *
     * @param input values to scan; never modified
     * @param pool the pool to run the tasks in
     */
    public static ScanResult scan(int[] input, ForkJoinPool pool) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        int n = input.length;
        int[] original = Arrays.copyOf(input, n);
        int[] work = Arrays.copyOf(input, n);
        long[] operations = {0L};
        int[] depth = {0};

        pool.invoke(new Reduce(0, n, work, operations, depth, 0));
        // The root's total sits in its last slot by the convention above.
        int total = n == 0 ? 0 : work[n - 1];
        // The root is scanned with nothing before it, which is why the exclusive
        // scan starts at zero rather than at the array's total.
        pool.invoke(new Scan(0, n, work, original, 0, operations, depth, 0));

        return new ScanResult(work, total, operations[0], depth[0]);
    }

    /**
     * The reduce phase: reduce every range to a single total in its last slot.
     *
     * <p>A leaf block accumulates left to right, which leaves the block total in
     * the block's final slot. An internal node combines the totals its children
     * left behind. Nothing here needs to know about ordering between halves,
     * which is exactly why the two halves can run at the same time.
     */
    private static final class Reduce extends RecursiveAction {
        private static final long serialVersionUID = 1L;

        private final int lo;
        private final int hi;
        private final int[] work;
        private final long[] operations;
        private final int[] depth;
        private final int level;

        Reduce(int lo, int hi, int[] work, long[] operations, int[] depth, int level) {
            this.lo = lo;
            this.hi = hi;
            this.work = work;
            this.operations = operations;
            this.depth = depth;
            this.level = level;
        }

        @Override
        protected void compute() {
            int n = hi - lo;
            if (n == 0) {
                return;
            }
            if (n <= SEQUENTIAL_THRESHOLD) {
                for (int i = lo + 1; i < hi; i++) {
                    work[i] = work[i] + work[i - 1];
                }
                operations[0] += n;
                recordDepth();
                return;
            }
            recordDepth();
            int mid = lo + n / 2;
            Reduce left = new Reduce(lo, mid, work, operations, depth, level + 1);
            Reduce right = new Reduce(mid, hi, work, operations, depth, level + 1);
            left.fork();
            right.compute();
            left.join();
            operations[0]++;
            // Both children's totals are already in place: the left child's in
            // the slot just before the split, the right child's at the end.
            work[hi - 1] = work[mid - 1] + work[hi - 1];
        }

        private void recordDepth() {
            if (level > depth[0]) {
                depth[0] = level;
            }
        }
    }

    /**
     * The scan phase: turn the tree of totals into the exclusive prefix sums.
     *
     * <p>A node is given the total of everything to its left. The left subtree
     * inherits that total unchanged and the right subtree inherits it plus the
     * left subtree's own total, since everything in the left subtree precedes
     * everything in the right. A leaf block cannot use the tree - its slots hold
     * running totals, not the original values - so it scans {@code original}
     * directly, which is the reason that array exists.
     */
    private static final class Scan extends RecursiveAction {
        private static final long serialVersionUID = 1L;

        private final int lo;
        private final int hi;
        private final int[] work;
        private final int[] original;
        private final int before;
        private final long[] operations;
        private final int[] depth;
        private final int level;

        Scan(int lo, int hi, int[] work, int[] original, int before,
                long[] operations, int[] depth, int level) {
            this.lo = lo;
            this.hi = hi;
            this.work = work;
            this.original = original;
            this.before = before;
            this.operations = operations;
            this.depth = depth;
            this.level = level;
        }

        @Override
        protected void compute() {
            int n = hi - lo;
            if (n == 0) {
                return;
            }
            if (n == 1) {
                // Nothing precedes the first element inside a lone element's range.
                work[lo] = before;
                operations[0]++;
                return;
            }
            if (n <= SEQUENTIAL_THRESHOLD) {
                int running = before;
                for (int i = lo; i < hi; i++) {
                    work[i] = running;
                    running += original[i];
                }
                operations[0] += n;
                recordDepth();
                return;
            }
            recordDepth();
            int mid = lo + n / 2;
            // Read the left total before descending: the descent overwrites it.
            int leftTotal = work[mid - 1];
            Scan left = new Scan(lo, mid, work, original, before, operations, depth, level + 1);
            Scan right = new Scan(mid, hi, work, original, before + leftTotal, operations, depth, level + 1);
            left.fork();
            right.compute();
            left.join();
            operations[0]++;
        }

        private void recordDepth() {
            if (level > depth[0]) {
                depth[0] = level;
            }
        }
    }

    /**
     * Inclusive scan, provided because most callers want it and the conversion is
     * free.
     */
    public static int[] scanInclusive(int[] input) {
        int[] exclusive = scanSequential(input);
        int[] inclusive = new int[exclusive.length];
        int running = 0;
        for (int i = 0; i < input.length; i++) {
            running += input[i];
            inclusive[i] = running;
        }
        return inclusive;
    }

    /**
     * Rank of every element: for each position, how many elements are strictly
     * smaller than it.
     *
     * <p>This is the clearest reason prefix sums are worth parallelising. Counting
     * pairwise comparisons would be O(n^2); instead, histogram the values, take an
     * exclusive scan of the histogram - which turns each count into "how many
     * elements are smaller than this bucket" - and look every value up. That is
     * O(n + span) with the scan carrying the prefix.
     *
     * <p>The histogram needs one slot per distinct value, so when the values are
     * far apart the histogram would dwarf the data. Past a span a few times the
     * length, sorting is both smaller and faster, and the ranks come out the same.
     *
     * @param input the values to rank
     * @return rank per position, as an array parallel to {@code input}
     */
    public static int[] ranks(int[] input) {
        int n = input.length;
        if (n == 0) {
            return new int[0];
        }
        int min = input[0];
        int max = input[0];
        for (int v : input) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        long span = (long) max - min + 1;

        if (span > 8L * n) {
            // Too sparse for a histogram: sort a copy of the indices instead.
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) {
                order[i] = i;
            }
            Arrays.sort(order, (p, q) -> Integer.compare(input[p], input[q]));
            int[] out = new int[n];
            for (int i = 0; i < n; i++) {
                out[order[i]] = i;
            }
            // Ties must share a rank, since the rank counts strictly smaller
            // elements. Without this, duplicates would be pushed apart and the
            // two paths would disagree.
            for (int i = 1; i < n; i++) {
                if (input[order[i]] == input[order[i - 1]]) {
                    out[order[i]] = out[order[i - 1]];
                }
            }
            return out;
        }

        int[] histogram = new int[(int) span];
        for (int v : input) {
            histogram[v - min]++;
        }
        // Exclusive scan: bucket b now holds how many elements are below it.
        int[] below = scan(histogram).values();
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = below[input[i] - min];
        }
        return out;
    }

    /**
     * Prefix scan under any associative operator. Sequential, because the point of
     * the parallel version is the O(log n) depth that only sums and products
     * need; anything else here is convenience.
     */
    public static <T> List<T> scan(List<T> input, BinaryOperator<T> operator) {
        List<T> output = new ArrayList<>(input.size());
        T running = null;
        for (int i = 0; i < input.size(); i++) {
            output.add(running);
            running = i == 0 ? input.get(0) : operator.apply(running, input.get(i));
        }
        return output;
    }
}
