/*
 * Checks Blelloch scans and ranks against sequential scans.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
import java.util.concurrent.*;
public class M6BlellochScanTest {
  static int fails=0, printed=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ fails++; if(printed++<20) System.out.println("FAIL "+w+" got "+g+" want "+e); } }
  public static void main(String[] a) throws Exception {
    Random r=new Random(11);
    int[] original=null;
    for(int n=0;n<=600;n++){
      int[] v=new int[n]; for(int i=0;i<n;i++) v[i]=r.nextInt(21)-10;
      if(n>0){ int[] nn=new int[n]; for(int i=0;i<n;i++) nn[i]=r.nextInt(21); var rn=BlellochScan.scan(nn); eq("nonneg monotone n="+n, rn.monotone(), true); eq("nonneg agrees n="+n, Arrays.toString(rn.values()), Arrays.toString(BlellochScan.scanSequential(nn))); }
      int[] copy=Arrays.copyOf(v,n);
      int[] want=BlellochScan.scanSequential(v);
      var res=BlellochScan.scan(v);
      eq("scan n="+n, Arrays.toString(res.values()), Arrays.toString(want));
      eq("total n="+n, res.total(), BlellochScan.total(v));
      boolean allNonNegative=true; for(int q:v) if(q<0) allNonNegative=false;
      eq("monotone n="+n, res.monotone(), allNonNegative);
      eq("input not modified n="+n, Arrays.toString(v), Arrays.toString(copy));
    }
    for(int n=0;n<=40;n++){
      int[] v=new int[n]; for(int i=0;i<n;i++) v[i]=r.nextInt(5);
      eq("inclusive n="+n, Arrays.toString(BlellochScan.scanInclusive(v)), Arrays.toString(inclusiveRef(v)));
    }
    for(int t=0;t<3000;t++){
      int n=r.nextInt(400);
      int[] v=new int[n]; for(int i=0;i<n;i++) v[i]=r.nextInt(50)-25;
      eq("rand", Arrays.toString(BlellochScan.scan(v).values()), Arrays.toString(BlellochScan.scanSequential(v)));
    }
    for(int n : new int[]{1000,4000,16000,64000}){
      int[] v=new int[n]; Arrays.fill(v,1);
      var res=BlellochScan.scan(v);
      System.out.printf("n=%6d ops=%8d ops/n=%.2f depth=%d total=%d%n", n, res.operations(), (double)res.operations()/n, res.maxDepth(), res.total());
      eq("linear work n="+n, res.operations() < 4L*n, true);
      // Depth is logarithmic with a blocking cutoff, so it must stay well under
      // log2(n) and grow by about one step per 4x of n.
      eq("depth bounded n="+n, res.maxDepth() <= Math.log(n)/Math.log(2)+1, true);
      eq("depth small n="+n, res.maxDepth() <= 16, true);
    }
    eq("ranks", Arrays.toString(BlellochScan.ranks(new int[]{5,3,9,3,1})), "[3, 1, 4, 1, 0]");
    eq("ranks sorted", Arrays.toString(BlellochScan.ranks(new int[]{1,2,3,4})), "[0, 1, 2, 3]");
    eq("ranks empty", Arrays.toString(BlellochScan.ranks(new int[]{})), "[]");
    for(int t=0;t<2000;t++){
      int n=r.nextInt(60);
      int[] q=new int[n]; for(int i=0;i<n;i++) q[i]= r.nextBoolean()? r.nextInt(30) : r.nextInt(100000000);
      eq("ranks vs brute n="+n, Arrays.toString(BlellochScan.ranks(q)), Arrays.toString(bruteRanks(q)));
    }
    eq("op scan", BlellochScan.scan(Arrays.asList(1,2,3,4),(x,y)->x+y).toString(), "[null, 1, 3, 6]");
    eq("op scan max", BlellochScan.scan(Arrays.asList(3,1,4,1,5),(x,y)->Math.max(x,y)).toString(), "[null, 3, 3, 4, 4]");
    var pool=new ForkJoinPool(1);
    int[] big=new int[5000]; for(int i=0;i<big.length;i++) big[i]=i%97;
    eq("pool p=1", Arrays.toString(BlellochScan.scan(big,pool).values()), Arrays.toString(BlellochScan.scanSequential(big)));
    pool.shutdown();
    var p8=new ForkJoinPool(8);
    for(int t=0;t<50;t++){ int[] q=new int[10000+r.nextInt(20000)]; for(int i=0;i<q.length;i++) q[i]=r.nextInt();
      eq("pool p=8", BlellochScan.scan(q,p8).total(), BlellochScan.total(q)); }
    p8.shutdown();
    int[] z=new int[30000]; for(int i=0;i<z.length;i++) z[i]=r.nextInt(1000);
    int[] s1=BlellochScan.scan(z).values();
    ForkJoinPool p3=new ForkJoinPool(3); int[] s3=BlellochScan.scan(z,p3).values(); p3.shutdown();
    eq("pool size independent", Arrays.toString(s1), Arrays.toString(s3));
    int[] w=new int[2_000_000]; for(int i=0;i<w.length;i++) w[i]=i&1023;
    long t0=System.nanoTime(); int[] sq=BlellochScan.scanSequential(w); long t1=System.nanoTime();
    var par=BlellochScan.scan(w); long t2=System.nanoTime();
    eq("big agrees", Arrays.toString(par.values()), Arrays.toString(sq));
    System.out.printf("2M elements: sequential %.1f ms, blelloch %.1f ms, ops/n=%.2f, depth=%d%n",(t1-t0)/1e6,(t2-t1)/1e6,(double)par.operations()/w.length,par.maxDepth());
    System.out.println(fails==0?"M6BlellochScanTest ALL PASS":"M6BlellochScanTest FAILURES: "+fails);
  }
  static int[] bruteRanks(int[] v){ int[] o=new int[v.length]; for(int i=0;i<v.length;i++){ int c=0; for(int j=0;j<v.length;j++) if(v[j]<v[i]) c++; o[i]=c; } return o; }
  static int[] inclusiveRef(int[] v){ int[] o=new int[v.length]; int s=0; for(int i=0;i<v.length;i++){ s+=v[i]; o[i]=s; } return o; }
}
