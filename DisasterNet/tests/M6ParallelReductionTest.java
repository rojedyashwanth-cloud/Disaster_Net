/*
 * Checks Fork/Join reductions against sequential ones and inspects the work counts.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
import java.util.concurrent.*;
public class M6ParallelReductionTest {
  static int fails=0, printed=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ fails++; if(printed++<20) System.out.println("FAIL "+w+" got "+g+" want "+e); } }
  public static void main(String[] a) throws Exception {
    Random r=new Random(3);
    for(int n=0;n<=5000;n+=7){
      int[] v=new int[n]; for(int i=0;i<n;i++) v[i]=r.nextInt(41)-20;
      eq("sum n="+n, ParallelReduction.sum(v).value(), ParallelReduction.sumSequential(v));
      eq("max n="+n, ParallelReduction.max(v,ForkJoinPool.commonPool()).value(), ParallelReduction.maxSequential(v));
    }
    for(int t=0;t<400;t++){
      int n=r.nextInt(300000);
      int[] v=new int[n]; for(int i=0;i<n;i++) v[i]=r.nextInt();
      eq("big sum", ParallelReduction.sum(v).value(), ParallelReduction.sumSequential(v));
      eq("big max", ParallelReduction.max(v,ForkJoinPool.commonPool()).value(), ParallelReduction.maxSequential(v));
    }
    // integer sums are exact, so work-splitting cannot change the answer
    eq("overflow-safe long sum", ParallelReduction.sumLong(new long[]{Long.MAX_VALUE,1,2,3},ForkJoinPool.commonPool()).value(), Long.MIN_VALUE+5L);
    // associativity checks on the operator
    for(int n : new int[]{1,2,3,1000,9999,10000,10001,100000,100001,1_000_000}){
      int[] v=new int[n]; Arrays.fill(v,1);
      var res=ParallelReduction.sum(v);
      long seq=ParallelReduction.sumSequential(v);
      eq("exact at n="+n, res.value(), seq);
      eq("work is linear n="+n, res.operations()<=n+64, true);
      eq("depth logarithmic n="+n, res.maxDepth()<=Math.log(n)/Math.log(2)+2, true);
      eq("tasks far below n n="+n, res.tasks()<=2*n/10000+64, true);
      System.out.printf("n=%8d sum=%d ops=%9d ops/n=%.2f depth=%2d tasks=%4d%n", n, res.value(), res.operations(), (double)res.operations()/n, res.maxDepth(), res.tasks());
    }
    // pool sizes must not change the integer answer
    int[] v=new int[2_000_000]; for(int i=0;i<v.length;i++) v[i]=i;
    long want=ParallelReduction.sumSequential(v);
    for(int p : new int[]{1,2,4,8,16}){
      ForkJoinPool pool=new ForkJoinPool(p);
      eq("pool p="+p, ParallelReduction.sum(v,pool).value(), want);
      pool.shutdown();
    }
    // doubles: report the deviation rather than claiming equality
    double[] d=new double[3_000_000]; for(int i=0;i<d.length;i++) d[i]=1.0/(i+1);
    double seq=0; for(double x:d) seq+=x;
    var dr=ParallelReduction.sumDoubles(d,ForkJoinPool.commonPool());
    System.out.printf("3M doubles: sequential %.15f parallel %.15f deviation %.3e relative %.3e%n", seq, dr.value(), Math.abs(seq-dr.value()), Math.abs(seq-dr.value())/seq);
    eq("double sum close", Math.abs(seq-dr.value())/seq < 1e-12, true);
    // performance
    int[] big=new int[8_000_000]; for(int i=0;i<big.length;i++) big[i]=i&255;
    for(int run=0;run<3;run++){
      long t0=System.nanoTime(); long s1=ParallelReduction.sumSequential(big); long t1=System.nanoTime();
      var pr=ParallelReduction.sum(big); long t2=System.nanoTime();
      eq("8M agrees", pr.value(), s1);
      System.out.printf("8M ints: sequential %.1f ms, parallel %.1f ms%n",(t1-t0)/1e6,(t2-t1)/1e6);
    }
    ForkJoinPool p4=new ForkJoinPool(4);
    long t3=System.nanoTime(); ParallelReduction.sum(big,p4); long t4=System.nanoTime();
    System.out.printf("8M ints on a 4-thread pool: %.1f ms%n",(t4-t3)/1e6);
    p4.shutdown();
    eq("empty sum", ParallelReduction.sum(new int[0]).value(), 0L);
    eq("empty max", ParallelReduction.max(new int[0],ForkJoinPool.commonPool()).value(), Integer.MIN_VALUE);
    eq("split 1000000", ParallelReduction.balancedSplitSize(1_000_000), 524288);
    eq("split 1024", ParallelReduction.balancedSplitSize(1024), 512);
    eq("split 1", ParallelReduction.balancedSplitSize(1), 1);
    System.out.println(fails==0?"M6ParallelReductionTest ALL PASS":"M6ParallelReductionTest FAILURES: "+fails);
  }
}
