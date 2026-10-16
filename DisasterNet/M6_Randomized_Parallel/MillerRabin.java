import java.util.ArrayList;
import java.util.List;

/**
 * Primality testing by Miller-Rabin, with a deterministic 64-bit witness set -
 * module M6.
 *
 * <p>Use case in DisasterNet: deciding whether an incident code, a checksum or an
 * equipment serial is prime before admitting it to an index. Trial division is
 * hopeless at 64-bit scale - it needs up to the square root of n divisions - and
 * a general-purpose probable-prime test would leave the answer probabilistic.
 * Miller-Rabin with the right witnesses is exact on all 64-bit inputs, so the
 * pipeline gets certainty as well as speed.
 *
 * <p>The test writes {@code n - 1 = d * 2^r} with {@code d} odd, then for a
 * witness {@code a} checks that {@code a^d} is congruent to 1 or to
 * {@code -1} modulo n, or that some {@code a^(d * 2^i)} is {@code -1} for
 * {@code i < r}. Every composite fails for at least a quarter of all bases, so
 * testing a fixed set is enough to be conclusive over a finite range.
 *
 * <p>Two witness sets are provided:
 * <ul>
 *   <li>{@link #WITNESSES_64} - the seven bases that make the test deterministic
 *       for every value below 2^64. Since Java's {@code long} is signed, this
 *       class accepts the range 0 &lt; n &lt; 2^63; the top of the unsigned range
 *       is reachable only through a BigInteger, which is out of scope here.</li>
 *   <li>{@link #WITNESSES_SMALL} - the first twelve primes, which are enough
 *       below 3.3 * 10^24 and are cheaper per candidate.</li>
 * </ul>
 *
 * <p>The distinction between a composite and a <i>probable</i> prime matters
 * more than it sounds: Carmichael numbers such as 561 pass every Fermat test but
 * fail Miller-Rabin, so a Fermat-based implementation would have to keep drawing
 * bases forever without ever concluding. {@link #strongPseudoprimeTo} makes that
 * failure mode explicit and testable.
 *
 * <p>Complexity: O(log n) modular multiplications per witness, so O(log n) with a
 * witness-set constant. Small factors are removed first, which rejects the great
 * majority of candidates after a few divisions.
 */
public final class MillerRabin {

    /**
     * Bases making the test deterministic for all n below 2^64. These are not
     * primes, which is fine and is the point: they are chosen for their covering
     * property, not for being prime.
     */
    private static final long[] WITNESSES_64 = {
        2, 325, 9375, 28178, 450775, 9780504, 1795265022};

    /** Bases deterministic for n below 3.3 * 10^24, which covers most inputs. */
    private static final long[] WITNESSES_SMALL = {
        2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37};

    private static final long SMALL_PRIME_LIMIT = 37;

    private MillerRabin() {
    }

    /**
     * Whether n is prime, decided exactly for any 64-bit n.
     *
     * @param n the candidate, which must lie in 0 &lt; n &lt; 2^63
     */
    public static boolean isPrime(long n) {
        return isPrime(n, WITNESSES_64);
    }

    /** Whether n is prime, using a supplied witness set. */
    public static boolean isPrime(long n, long[] witnesses) {
        if (n < 2) {
            return false;
        }
        for (long p : WITNESSES_SMALL) {
            if (n == p) {
                return true;
            }
            if (n % p == 0) {
                return false;
            }
        }

        long d = n - 1;
        int r = Long.numberOfTrailingZeros(d);
        d >>>= r;

        for (long a : witnesses) {
            long reduced = Math.floorMod(a, n);
            if (reduced == 0) {
                continue;
            }
            long x = modPow(reduced, d, n);
            if (x == 1 || x == n - 1) {
                continue;
            }
            boolean witnessPassed = false;
            for (int i = 1; i < r; i++) {
                x = mulMod(x, x, n);
                if (x == n - 1) {
                    witnessPassed = true;
                    break;
                }
            }
            if (!witnessPassed) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether n is prime and below 2^63, where the small witness set is also
     * conclusive. Faster per candidate than the full 64-bit set.
     */
    public static boolean isPrimeSmallRange(long n) {
        return isPrime(n, WITNESSES_SMALL);
    }

    /** Primality of every value up to and including limit, by sieve. */
    public static boolean[] sieve(int limit) {
        boolean[] prime = new boolean[limit + 1];
        // Start optimistic: every value from 2 up is a candidate, and the loop
        // below crosses off the composites. Starting from false instead would
        // cross nothing off and report no primes at all.
        for (int i = 2; i <= limit; i++) {
            prime[i] = true;
        }
        for (int i = 2; (long) i * i <= limit; i++) {
            if (prime[i]) {
                // From i*i, not i: smaller multiples of i were already crossed
                // off by a smaller prime.
                for (long j = (long) i * i; j <= limit; j += i) {
                    prime[(int) j] = false;
                }
            }
        }
        return prime;
    }

    /**
     * Every prime up to limit, by sieve. The intended way to test many nearby
     * values at once: sieving costs one pass, where testing each value separately
     * costs one full test per value.
     */
    public static List<Long> primesUpTo(int limit) {
        boolean[] prime = sieve(limit);
        List<Long> primes = new ArrayList<>();
        for (int i = 2; i <= limit; i++) {
            if (prime[i]) {
                primes.add((long) i);
            }
        }
        return primes;
    }

    /**
     * Whether n is composite yet fools every one of the given bases.
     *
     * <p>Compositeness is decided by the full deterministic {@link #WITNESSES_64}
     * set, never by {@code bases}. That independence is the whole point: an
     * earlier version called {@code isPrime(n, bases)} first, which made the
     * method report "not a pseudoprime" for exactly the numbers it was meant to
     * expose, because a base set that fools a composite is by definition unable
     * to prove that composite composite.
     *
     * <p>So the answer is: n is composite (by the strong set) and every base in
     * {@code bases} individually fails to detect it. Passing a single base
     * therefore answers "would this one base have been fooled?", which is the
     * question worth asking when auditing a witness set.
     */
    public static boolean strongPseudoprimeTo(long n, long[] bases) {
        if (n < 2 || isPrime(n, WITNESSES_64)) {
            return false;
        }
        for (long a : bases) {
            if (!passesWitness(n, Math.floorMod(a, n))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the base a passes the Miller-Rabin check for n. */
    private static boolean passesWitness(long n, long a) {
        if (a == 0) {
            return true;
        }
        long d = n - 1;
        int r = Long.numberOfTrailingZeros(d);
        d >>>= r;
        long x = modPow(a, d, n);
        if (x == 1 || x == n - 1) {
            return true;
        }
        for (int i = 1; i < r; i++) {
            x = mulMod(x, x, n);
            if (x == n - 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Modular exponentiation by repeated squaring, the only exponentiation
     * primitive needed, in O(log e) multiplications.
     */
    public static long modPow(long base, long exponent, long modulus) {
        long result = 1;
        long b = base % modulus;
        while (exponent > 0) {
            if ((exponent & 1) == 1) {
                result = mulMod(result, b, modulus);
            }
            exponent >>>= 1;
            if (exponent > 0) {
                b = mulMod(b, b, modulus);
            }
        }
        return result;
    }

    /**
     * Multiplication modulo n without overflow.
     *
     * <p>A plain {@code a * b} overflows for anything near 2^63, which is most of
     * the range that matters here. Two paths avoid it:
     * <ul>
     *   <li>Below 2^31 the product always fits in a long, so the exact remainder
     *       is a single multiply and divide.</li>
     *   <li>Above that, binary doubling: shift one operand in bit by bit and add
     *       it into the accumulator, testing before each add whether the sum would
     *       exceed the modulus. Every test is a comparison against a quantity
     *       that is already reduced, so no intermediate can overflow. This is
     *       O(log b) additions rather than one multiply, which is the price of
     *       correctness being visible rather than merely asserted.</li>
     * </ul>
     */
    public static long mulMod(long a, long b, long modulus) {
        if (modulus <= 0) {
            throw new IllegalArgumentException("modulus must be positive");
        }
        a = Math.floorMod(a, modulus);
        b = Math.floorMod(b, modulus);
        if (modulus < (1L << 31)) {
            return a * b % modulus;
        }
        long result = 0;
        while (b > 0) {
            if ((b & 1) != 0) {
                // result + a >= modulus exactly when a > modulus - result.
                result = a > modulus - result ? result - (modulus - a) : result + a;
            }
            b >>>= 1;
            if (b > 0) {
                // a + a >= modulus exactly when a >= modulus - a.
                a = a >= modulus - a ? a - (modulus - a) : a + a;
            }
        }
        return result;
    }
}
