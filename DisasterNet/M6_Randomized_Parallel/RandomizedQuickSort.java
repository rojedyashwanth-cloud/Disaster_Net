import java.util.Arrays;
import java.util.Random;

/**
 * Randomized quicksort with a duplicate-aware partition and a worst-case guard -
 * module M6.
 *
 * <p>Use case in DisasterNet: ranking sensor readings and incident reports by
 * severity as they stream in. The sort has to be fast on the common input and
 * must never fall off a cliff on an unlucky one, because a live dashboard cannot
 * afford a stall whose length nobody can predict.
 *
 * <p>Three ideas, each addressing a different failure:
 * <ul>
 *   <li><b>Random pivot.</b> Choosing the pivot uniformly at random from the
 *       subarray rather than from the first, last or middle element. The
 *       consequence is not a better average, which deterministic quicksort
 *       already has, but the removal of the correlation between the input order
 *       and the partition quality. Sorted input - the shape real telemetry often
 *       arrives in - costs O(n log n) instead of O(n^2), and an adversary who
 *       knows the pivot rule still cannot predict the next one.</li>
 *   <li><b>Three-way partitioning.</b> Values equal to the pivot are collected
 *       into a middle block rather than being split between the two sides. With
 *       many duplicate keys - team IDs, district codes, repeated severity
 *       levels - two-way partitioning degenerates towards O(n^2) purely because
 *       equal elements are still compared and swapped, and the middle block makes
 *       a run of equal keys cost O(k) once.</li>
 *   <li><b>Worst-case guard.</b> Randomness makes deep recursion unlikely, not
 *       impossible. Past a depth of {@code 2 * ceil(log2(n + 1))} - already well
 *       beyond the expected depth of about {@code 1.39 log2 n} - the remaining
 *       subarray is sorted by heapsort, giving the introsort guarantee of
 *       O(n log n) in the worst case while keeping quicksort's constants
 *       everywhere else.</li>
 * </ul>
 *
 * <p>Small subarrays go to insertion sort, which beats quicksort below roughly
 * sixteen elements: the recursion and partitioning overhead exceeds the gain.
 *
 * <p>Complexity: expected O(n log n) time, worst case O(n log n) thanks to the
 * guard, O(log n) auxiliary space, and in-place.
 */
public final class RandomizedQuickSort {

    /** Below this size, insertion sort wins on constants. */
    private static final int INSERTION_SORT_CUTOFF = 16;

    private RandomizedQuickSort() {
    }

    /**
     * The outcome of a sort, with the counters that make the analysis checkable
     * rather than asserted.
     *
     * @param values the sorted values
     * @param comparisons value comparisons performed
     * @param swaps element moves performed
     * @param maxDepth deepest recursion reached
     * @param guardTriggered whether the worst-case guard ever fired
     * @param insertionSortUsed whether any subarray fell below the cutoff
     */
    public record Result(int[] values, long comparisons, long swaps, int maxDepth,
            boolean guardTriggered, boolean insertionSortUsed) {

        /** Whether the output is in non-decreasing order. */
        public boolean sorted() {
            for (int i = 1; i < values.length; i++) {
                if (values[i] < values[i - 1]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "n=" + values.length + " comparisons=" + comparisons + " maxDepth=" + maxDepth
                    + " expectedDepth=" + String.format("%.1f", RandomizedQuickSort.expectedDepth(values.length))
                    + (guardTriggered ? " GUARD-FIRED" : "");
        }
    }

    /**
     * Expected recursion depth of randomized quicksort, about {@code 2 ln n}.
     * Used as the baseline the guard's threshold is set well above.
     */
    public static double expectedDepth(int n) {
        return n <= 1 ? 0 : 2 * Math.log(n);
    }

    /** Sorts in place using an unpredictable pivot, then returns the array. */
    public static int[] sort(int[] values) {
        Result result = sort(values, new Random().nextLong());
        int[] copy = result.values();
        System.arraycopy(copy, 0, values, 0, values.length);
        return values;
    }

    /** Sorts a copy of the input with a seeded pivot sequence, for reproducibility. */
    public static Result sort(int[] input, long seed) {
        int[] a = Arrays.copyOf(input, input.length);
        long[] counters = new long[2];
        State state = new State(new Random(seed), counters, a.length);
        sortRange(a, 0, a.length - 1, 0, state);
        return new Result(a, counters[0], counters[1], state.maxDepth,
                state.guardTriggered, state.insertionUsed);
    }

    /** Mutable state threaded through the recursion. */
    private static final class State {
        private final Random random;
        private final long[] counters;
        private final int depthLimit;
        private int maxDepth;
        private boolean guardTriggered;
        private boolean insertionUsed;

        State(Random random, long[] counters, int length) {
            this.random = random;
            this.counters = counters;
            // The limit must come from the size of the whole input. Deriving it
            // from the current subarray would shrink the budget as the sort
            // descends, so a small subarray reached at depth 15 would trip the
            // guard even though nothing pathological had happened.
            this.depthLimit = 2 * (32 - Integer.numberOfLeadingZeros(length + 1)) + 8;
        }
    }

    private static void sortRange(int[] a, int lo, int hi, int depth, State state) {
        int length = hi - lo + 1;
        if (length <= 1) {
            return;
        }
        state.maxDepth = Math.max(state.maxDepth, depth);

        if (length <= INSERTION_SORT_CUTOFF) {
            state.insertionUsed = true;
            insertionSort(a, lo, hi, state);
            return;
        }

        // Worst-case guard: past this depth a random pivot sequence has almost
        // certainly been unlucky, so finish with the guaranteed O(m log m) sort.
        if (depth > state.depthLimit) {
            state.guardTriggered = true;
            heapSort(a, lo, hi, state);
            return;
        }

        int pivot = a[lo + state.random.nextInt(length)];
        int lt = lo;
        int gt = hi;
        int i = lo;
        while (i <= gt) {
            state.counters[0]++;
            if (a[i] < pivot) {
                if (i != lt) {
                    swap(a, i, lt, state);
                }
                lt++;
                i++;
            } else if (a[i] > pivot) {
                swap(a, i, gt, state);
                gt--;
            } else {
                i++;
            }
        }
        // [lo, lt-1] < pivot, [lt, gt] == pivot, [gt+1, hi] > pivot.
        // The equal block needs no recursion, which is what makes duplicates cheap.
        sortRange(a, lo, lt - 1, depth + 1, state);
        sortRange(a, gt + 1, hi, depth + 1, state);
    }

    private static void insertionSort(int[] a, int lo, int hi, State state) {
        for (int i = lo + 1; i <= hi; i++) {
            int key = a[i];
            int j = i - 1;
            while (j >= lo) {
                state.counters[0]++;
                if (a[j] <= key) {
                    break;
                }
                a[j + 1] = a[j];
                state.counters[1]++;
                j--;
            }
            a[j + 1] = key;
        }
    }

    private static void heapSort(int[] a, int lo, int hi, State state) {
        int length = hi - lo + 1;
        for (int i = length / 2 - 1; i >= 0; i--) {
            siftDown(a, lo, i, length, state);
        }
        for (int end = length - 1; end > 0; end--) {
            swap(a, lo, lo + end, state);
            siftDown(a, lo, 0, end, state);
        }
    }

    private static void siftDown(int[] a, int lo, int root, int length, State state) {
        while (true) {
            int child = 2 * root + 1;
            if (child >= length) {
                return;
            }
            if (child + 1 < length) {
                state.counters[0]++;
                if (a[lo + child] < a[lo + child + 1]) {
                    child++;
                }
            }
            state.counters[0]++;
            if (a[lo + root] >= a[lo + child]) {
                return;
            }
            swap(a, lo + root, lo + child, state);
            root = child;
        }
    }

    private static void swap(int[] a, int i, int j, State state) {
        if (i == j) {
            return;
        }
        int temp = a[i];
        a[i] = a[j];
        a[j] = temp;
        state.counters[1]++;
    }
}