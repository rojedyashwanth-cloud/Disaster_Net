/*
 * Checks Brent cycle detection against Floyd and a hash table, including null states.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
import java.util.function.Function;
public class M6BrentCycleTest {
  static int fails=0, printed=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ fails++; if(printed++<20) System.out.println("FAIL "+w+" got "+g+" want "+e); } }
  // f(i) = i + skip for i < limit, else i - limit + skip : a tail of `tail` then a cycle
  static Function<Integer,Integer> lam(int skip,int limit){ return i -> i < limit ? i+skip : i-limit+skip; }
  public static void main(String[] a){
    for(int skip : new int[]{1,2,3,5,7}){
      for(int limit : new int[]{1,2,3,7,20,64,100,257,1000}){
        Function<Integer,Integer> f=lam(skip,limit);
        List<Integer> tr=BrentAlgorithm.trajectory(f,0,limit*4+50);
        int wantLen=BrentAlgorithm.cycleLengthOf(tr), wantIdx=BrentAlgorithm.cycleIndexOf(tr);
        if(wantLen<0) continue;
        var b=BrentAlgorithm.brent(f,0,limit*4L+50);
        var fl=BrentAlgorithm.floyd(f,0,limit*4L+50);
        var h=BrentAlgorithm.hashTable(f,0,limit*4L+50);
        String tag="skip="+skip+" limit="+limit;
        eq("brent found "+tag, b.found(), true);
        eq("brent length "+tag, b.length(), wantLen);
        eq("brent index "+tag, b.index(), wantIdx);
        eq("floyd length "+tag, fl.length(), wantLen);
        eq("floyd index "+tag, fl.index(), wantIdx);
        eq("hash length "+tag, h.length(), wantLen);
        eq("hash index "+tag, h.index(), wantIdx);
        // Brent should not need many more calls than a single walk
        long singleWalk=wantIdx+wantLen;
        eq("brent steps bounded "+tag, b.steps() <= 4*singleWalk+16, true);
        eq("brent no worse than floyd by much "+tag, b.steps() <= fl.steps()+8, true);
      }
    }
    // pure cycle from the start
    for(int len : new int[]{1,2,5,17,64}){
      Function<Integer,Integer> f=i->(i+1)%len;
      var b=BrentAlgorithm.brent(f,0,1000);
      eq("pure cycle len="+len+" index", b.index(), 0);
      eq("pure cycle len="+len+" length", b.length(), len);
    }
    // non-cycling functions must terminate at the limit
    Function<Integer,Integer> grow=i->i+1;
    eq("no cycle brent", BrentAlgorithm.brent(grow,0,100).found(), false);
    eq("no cycle floyd", BrentAlgorithm.floyd(grow,0,100).found(), false);
    eq("no cycle hash", BrentAlgorithm.hashTable(grow,0,100).found(), false);
    eq("limit respected brent", BrentAlgorithm.brent(grow,0,100).steps()<=101, true);
    Function<Integer,Integer> strict=i->i<1000?i+1:i;
    eq("no cycle until limit", BrentAlgorithm.brent(strict,0,500).found(), false);
    // non-integer states
    Function<String,String> strings=s->s.length()>4 ? s.substring(0,s.length()-1) : s+"X";
    var sb=BrentAlgorithm.brent(strings,"abcde",1000);
    eq("string cycle found", sb.found(), true);
    eq("string cycle length", sb.length(), BrentAlgorithm.cycleLengthOf(BrentAlgorithm.trajectory(strings,"abcde",500)));
    eq("string cycle index", sb.index(), BrentAlgorithm.cycleIndexOf(BrentAlgorithm.trajectory(strings,"abcde",500)));
    // null-returning function
    // Brent calls f on its own output, so a function returning null must itself
    // cope with a null input. This one reaches null, then 7, which is a fixed point.
    Function<Integer,Integer> throughNull=i -> i==null ? Integer.valueOf(7) : (i<5 ? null : i);
    var nb=BrentAlgorithm.brent(throughNull,0,100);
    eq("null state cycle found", nb.found(), true);
    eq("null state length", nb.length(), 1);
    eq("null state index", nb.index(), 2);
    eq("null state floyd agrees", BrentAlgorithm.floyd(throughNull,0,100).length(), 1);
    eq("null state hash agrees", BrentAlgorithm.hashTable(throughNull,0,100).index(), 2);
    // power-of-two schedule
    eq("pow2 1", BrentAlgorithm.isPowerOfTwo(1), true);
    eq("pow2 2", BrentAlgorithm.isPowerOfTwo(2), true);
    eq("pow2 1024", BrentAlgorithm.isPowerOfTwo(1024), true);
    eq("pow2 3", BrentAlgorithm.isPowerOfTwo(3), false);
    eq("pow2 0", BrentAlgorithm.isPowerOfTwo(0), false);
    eq("pow2 -4", BrentAlgorithm.isPowerOfTwo(-4), false);
    // a big cycle: brent must find it in a small multiple of mu+lambda
    int mu=5000, lambda=100000;
    Function<Integer,Integer> big=i-> i<mu ? i+1 : mu + (i-mu+1)%lambda;
    long t0=System.nanoTime();
    var rb=BrentAlgorithm.brent(big,0,2_000_000L);
    long t1=System.nanoTime();
    eq("big index", rb.index(), mu);
    eq("big length", rb.length(), lambda);
    eq("big steps bounded", rb.steps() < 6L*(mu+lambda), true);
    System.out.printf("mu=%d lambda=%d: brent found it in %d steps (%.1fx mu+lambda), %.1f ms%n", mu, lambda, rb.steps(), (double)rb.steps()/(mu+lambda), (t1-t0)/1e6);
    long t2=System.nanoTime();
    var rf=BrentAlgorithm.floyd(big,0,2_000_000L);
    long t3=System.nanoTime();
    System.out.printf("                 floyd used %d steps, %.1f ms%n", rf.steps(), (t3-t2)/1e6);
    eq("floyd agrees on big", rf.length(), lambda);
    eq("floyd index big", rf.index(), mu);
    eq("brent cheaper than floyd", rb.steps() < rf.steps(), true);
    System.out.println(rb);
    System.out.println(fails==0?"M6BrentCycleTest ALL PASS":"M6BrentCycleTest FAILURES: "+fails);
  }
}
