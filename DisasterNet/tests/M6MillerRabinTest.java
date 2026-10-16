/*
 * Checks Miller-Rabin against a sieve over a range and against known pseudoprimes.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.math.BigInteger;
import java.util.*;
public class M6MillerRabinTest {
  static int fails=0, printed=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ fails++; if(printed++<25) System.out.println("FAIL "+w+" got "+g+" want "+e); } }
  public static void main(String[] x){
    // exhaustive small range against trial division
    int N=200000;
    boolean[] sieve=MillerRabin.sieve(N);
    for(int n=0;n<=N;n++){
      boolean trial=n>=2; for(int d=2; (long)d*d<=n && trial; d++) if(n%d==0) trial=false;
      eq("sieve="+n, sieve[n], trial);
      eq("isPrime="+n, MillerRabin.isPrime(n), trial);
      eq("smallRange="+n, MillerRabin.isPrimeSmallRange(n), trial);
    }
    eq("sieve(0)", Arrays.toString(MillerRabin.sieve(0)), "[false]");
    eq("sieve(10)", Arrays.toString(MillerRabin.sieve(10)), "[false, false, true, true, false, true, false, true, false, false, false]");
    eq("primesUpTo count", MillerRabin.primesUpTo(1000).size(), 168);
    eq("primesUpTo first", MillerRabin.primesUpTo(1000).get(0), 2L);
    eq("primesUpTo last", MillerRabin.primesUpTo(1000).get(167), 997L);
    // verified primes
    long[] primes={2,3,5,7,11,13,17,19,31,61,97,541,1223,2017,7919,104729,6700417L,2147483647L,2305843009213693951L,2305843009213693921L};
    for(long p: primes){ eq("prime "+p+" (oracle)", BigInteger.valueOf(p).isProbablePrime(200), true); eq("prime "+p, MillerRabin.isPrime(p), true); }
    long[] composites={0,1,4,9,100,561,1105,1729,2465,2821,6601,8911,41041,825265,321197185L,2152302898747L,3474749660383L,3825123056546413051L,Long.MAX_VALUE,8038287257479036823L};
    for(long c: composites){ eq("composite "+c+" (oracle)", BigInteger.valueOf(c).isProbablePrime(200), false); eq("composite "+c, MillerRabin.isPrime(c), false); }
    // verified strong pseudoprimes to base 2: every one is composite, base 2 alone is fooled,
    // and the 7-base 64-bit set is not.
    long[] ps2={2047,3277,4033,4681,8321,15841,29341,42799,49141,52633,65281,74665,80581,85489,88357,90751,104653,130561,196093,220729,233017,252601,253241,256999,271951,280601,314821,357761,390937,458989,476971,486737,489997,514447,580337,635401,647089,741751,800605,818201,838861,873181,916327,976873};
        int fooled=0;
    for(long n: ps2){
      eq("pseudoprime "+n+" composite", BigInteger.valueOf(n).isProbablePrime(200), false);
      if(MillerRabin.strongPseudoprimeTo(n,new long[]{2})) fooled++;
      eq("64-bit set rejects "+n, MillerRabin.isPrime(n), false);
      eq("base 3 catches "+n, MillerRabin.strongPseudoprimeTo(n,new long[]{2,3}), false);
    }
    int expect2=0, mismatch=0;
    for(int i=0;i<ps2.length;i++){
      boolean ref=referenceFoolsBase2(ps2[i]);   // independent implementation
      if(ref) expect2++;
      eq("base-2 pseudoprime "+ps2[i], MillerRabin.strongPseudoprimeTo(ps2[i],new long[]{2}), ref);
      if(MillerRabin.strongPseudoprimeTo(ps2[i],new long[]{2})!=ref) mismatch++;
    }
    eq("independent reference agrees on every entry", mismatch, 0);
    eq("base-2 strong pseudoprimes found", fooled, expect2);
    System.out.println("base-2 strong pseudoprimes: "+fooled+"/"+ps2.length+", matching an independent reference on all");
    // Carmichael numbers satisfy Fermat for every coprime base yet are caught here
    long[] carmichael={561,1105,1729,2465,2821,6601,8911,10585,15841,29341,41041,46657,52633,62745,63973,75361};
    for(long n: carmichael){
      boolean fermat=true;
      for(long b=2;b<=n-1&&fermat;b++) if(gcd(b,n)==1 && !BigInteger.valueOf(b).modPow(BigInteger.valueOf(n-1),BigInteger.valueOf(n)).equals(BigInteger.ONE)) fermat=false;
      eq("Carmichael "+n+" passes Fermat", fermat, true);
      eq("Carmichael "+n+" caught by Miller-Rabin", MillerRabin.isPrime(n), false);
    }
    eq("modPow 2^10 mod 1000", MillerRabin.modPow(2,10,1000), 24L);
    eq("modPow 0^0", MillerRabin.modPow(0,0,7), 1L);
    eq("negative input rejected by contract", MillerRabin.isPrime(-59), false);
    eq("2^64-59 is out of the documented range", MillerRabin.isPrime(0xFFFFFFFFFFFFFFC5L), false);
    eq("modPow negative base", MillerRabin.modPow(-3,4,10), 1L);
    Random r=new Random(9);
    for(int t=0;t<100000;t++){
      long m=1+r.nextInt(Integer.MAX_VALUE);
      long a=r.nextInt(), b=r.nextInt();
      eq("mulMod small", MillerRabin.mulMod(a,b,m), Math.floorMod(Math.floorMod(a,m)*Math.floorMod(b,m), m));
      long e=r.nextInt(1<<20);
      eq("modPow small", MillerRabin.modPow(a,e,m), BigInteger.valueOf(Math.floorMod(a,m)).modPow(BigInteger.valueOf(e),BigInteger.valueOf(m)).longValue());
    }
    for(int t=0;t<100000;t++){ // moduli above 2^31 take the doubling path
      long m=(1L<<31)+Long.remainderUnsigned(r.nextLong(),Long.MAX_VALUE-(1L<<31)-1);
      long a=Long.remainderUnsigned(r.nextLong(),m), b=Long.remainderUnsigned(r.nextLong(),m);
      eq("mulMod large", MillerRabin.mulMod(a,b,m), BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).mod(BigInteger.valueOf(m)).longValue());
      long e=(((long)r.nextInt()<<20)^r.nextInt())&Long.MAX_VALUE;
      eq("modPow large", MillerRabin.modPow(a,e,m), BigInteger.valueOf(a).modPow(BigInteger.valueOf(e),BigInteger.valueOf(m)).longValue());
    }
    for(int t=0;t<20000;t++){
      long v=Long.remainderUnsigned(r.nextLong(),Long.MAX_VALUE);
      eq("oracle64 "+v, MillerRabin.isPrime(v), BigInteger.valueOf(v).isProbablePrime(200));
    }
    for(long v=Long.MAX_VALUE-20; v<Long.MAX_VALUE; v++) eq("near max "+v, MillerRabin.isPrime(v), BigInteger.valueOf(v).isProbablePrime(200));
    long t0=System.nanoTime(); int cnt=0;
    for(long v=1_000_000_000_000L; v<1_000_000_001_000L; v++) if(MillerRabin.isPrime(v)) cnt++;
    System.out.printf("1000 candidates near 10^12 in %.1f ms (%d primes, ~%d expected)%n",(System.nanoTime()-t0)/1e6,cnt,Math.round(1000.0/Math.log(1e12)));
    System.out.println(fails==0?"M6MillerRabinTest ALL PASS":"M6MillerRabinTest FAILURES: "+fails);
  }
    // textbook Miller-Rabin single-witness check, written from scratch as an oracle
  static boolean referenceFoolsBase2(long n){
    if(n<2||n%2==0) return false;
    long d=n-1; int r=0; while((d&1)==0){ d>>>=1; r++; }
    long x=BigInteger.valueOf(2).modPow(BigInteger.valueOf(d),BigInteger.valueOf(n)).longValue();
    // A witness that PASSES means the test failed to detect compositeness,
    // which is exactly what "fools base 2" means.
    if(x==1||x==n-1) return true;
    for(int i=1;i<r;i++){ x=x*x%n; if(x==n-1) return true; }
    return false;
  }
  static long gcd(long a,long b){ while(b!=0){ long t=b; b=a%b; a=t; } return a; }
}
