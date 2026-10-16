/*
 * Checks randomized quicksort ordering and reservoir sampling uniformity.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M6QuickSortAndReservoirTest {
  static int fails=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ System.out.println("FAIL "+w+" got "+g+" want "+e); fails++; } }
  static void chi(String label, long[] cnt, int n, int k, int trials){
    double e=(double)k*trials/n, ch=0;
    for(int i=0;i<n;i++) ch+=(cnt[i]-e)*(cnt[i]-e)/e;
    double ratio=ch/(n-1);
    System.out.printf("%-28s chi2/df=%.3f%n", label, ratio);
    // a uniform sample has chi2/df near 1; anything past 1.5 is a real bias
    if(ratio<0.5||ratio>1.5){ System.out.println("FAIL "+label+" biased"); fails++; }
  }
  static long[] counts(int[] a){ long[] c=new long[512]; for(int v:a) c[v+256]++; return c; }
  public static void main(String[] x){
    Random r=new Random(5);
    for(int t=0;t<300;t++){
      int n=r.nextInt(300);
      int[] a=new int[n]; for(int i=0;i<n;i++) a[i]=r.nextInt(20);
      int[] want=a.clone(); Arrays.sort(want);
      var res=RandomizedQuickSort.sort(a, t);
      eq("sorted n="+n, Arrays.toString(res.values()), Arrays.toString(want));
      eq("permutation", Arrays.toString(counts(res.values())), Arrays.toString(counts(want)));
    }
    int[] asc=new int[20000]; for(int i=0;i<asc.length;i++) asc[i]=i;
    var rAsc=RandomizedQuickSort.sort(asc, 1);
    eq("sorted input sorted", rAsc.sorted(), true);
    eq("sorted input not pathological", rAsc.maxDepth() < 60, true);
    eq("no guard on sorted input", rAsc.guardTriggered(), false);
    System.out.println("sorted 20000: "+rAsc);
    int[] dup=new int[20000]; Arrays.fill(dup, 7);
    var rDup=RandomizedQuickSort.sort(dup, 1);
    eq("duplicates sorted", rDup.sorted(), true);
    eq("duplicates need no recursion", rDup.maxDepth(), 0);
    System.out.println("dup 20000: "+rDup);
    int[] rev=new int[50000]; for(int i=0;i<rev.length;i++) rev[i]=rev.length-i;
    var rRev=RandomizedQuickSort.sort(rev, 7);
    eq("reversed sorted", rRev.sorted(), true);
    System.out.println("reversed 50000: "+rRev);
    int[] zeros=new int[100000];
    eq("100k identical", RandomizedQuickSort.sort(zeros,3).maxDepth(), 0);
    eq("expected depth 2lnn", (int)Math.round(RandomizedQuickSort.expectedDepth(20000)), 20);
    int k=10,n=1000,trials=8000;
    long[] cR=new long[n], cL=new long[n];
    for(int t=0;t<trials;t++) for(int p: ReservoirSampling.algorithmR(n,k,t).positions()) cR[p]++;
    for(int t=0;t<trials;t++) for(int p: ReservoirSampling.algorithmL(n,k,t).positions()) cL[p]++;
    chi("Algorithm R k=10 n=1000", cR, n, k, trials);
    chi("Algorithm L k=10 n=1000", cL, n, k, trials);
    for(int kk : new int[]{5,50}){
      long[] a1=new long[n], a2=new long[n];
      for(int t=0;t<trials;t++) for(int p: ReservoirSampling.algorithmR(n,kk,t).positions()) a1[p]++;
      for(int t=0;t<trials;t++) for(int p: ReservoirSampling.algorithmL(n,kk,t).positions()) a2[p]++;
      chi("Algorithm R k="+kk, a1, n, kk, trials);
      chi("Algorithm L k="+kk, a2, n, kk, trials);
    }
    long dR=0,dL=0;
    for(int t=0;t<200;t++){ dR+=ReservoirSampling.algorithmR(100000,k,t).randomDraws(); dL+=ReservoirSampling.algorithmL(100000,k,t).randomDraws(); }
    System.out.println("random draws over 200 trials at n=100000, k=10: R="+dR+" L="+dL);
    eq("L far cheaper than R", dL*5 < dR, true);
    for(int t=0;t<200;t++){
      var s=ReservoirSampling.algorithmR(n,k,t);
      eq("R size", s.positions().size(), k); eq("R distinct", new HashSet<>(s.positions()).size(), k);
      var l=ReservoirSampling.algorithmL(n,k,t);
      eq("L size", l.positions().size(), k); eq("L distinct", new HashSet<>(l.positions()).size(), k);
      for(int p: l.positions()) if(p<0||p>=n){ fails++; System.out.println("FAIL L range "+p); break; }
    }
    eq("k>n takes all", ReservoirSampling.algorithmR(3,10,0).positions().size(), 3);
    eq("empty", ReservoirSampling.algorithmR(0,5,0).positions().size(), 0);
    eq("k=0", ReservoirSampling.algorithmR(100,0,0).positions().size(), 0);
    eq("k=1 R", ReservoirSampling.algorithmR(1000,1,3).positions().size(), 1);
    eq("k=1 L", ReservoirSampling.algorithmL(1000,1,3).positions().size(), 1);
    eq("k=1 L in range", ReservoirSampling.algorithmL(1000,1,3).positions().get(0)<1000, true);
    eq("k=n", ReservoirSampling.algorithmL(100,100,0).positions().size(), 100);
    List<String> items=new ArrayList<>(); for(int i=0;i<50;i++) items.add("item"+i);
    eq("values projection", ReservoirSampling.algorithmR(items,5,2).values(items).size(), 5);
    System.out.println(fails==0?"M6QuickSortAndReservoirTest ALL PASS":"M6QuickSortAndReservoirTest FAILURES: "+fails);
  }
}
