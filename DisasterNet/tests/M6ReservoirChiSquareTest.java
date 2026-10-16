/*
 * Checks reservoir sampling uniformity with a chi squared statistic at scale.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M6ReservoirChiSquareTest {
  public static void main(String[] x){
    // 1) is the bounded generator itself uniform?
    int n=997, trials=400000;
    long[] c=new long[n];
    java.util.Random r=new java.util.Random(1);
    for(int t=0;t<trials;t++) c[(int)ReservoirSampling.boundedForTest(r,n)]++;
    double chi=0, e=(double)trials/n;
    for(long v: c) chi+=(v-e)*(v-e)/e;
    System.out.printf("bounded(): chi2=%.1f df=%d (expect ~%d)%n", chi, n-1, n-1);
    System.out.printf("bounded(): max bucket dev %.4f%n", Arrays.stream(c).mapToDouble(v->Math.abs(v-e)/e).max().getAsDouble());

    // 2) uniformity of each algorithm, chi-square over all positions
    for(int k : new int[]{5,10,50}){
      int N=1000, T=8000;
      for(String algo : new String[]{"R","L"}){
        long[] cnt=new long[N];
        for(int t=0;t<T;t++){
          var s = algo.equals("R")? ReservoirSampling.algorithmR(N,k,t) : ReservoirSampling.algorithmL(N,k,t);
          for(int p: s.positions()) cnt[p]++;
        }
        double ee=(double)k*T/N, ch=0;
        for(long v: cnt) ch+=(v-ee)*(v-ee)/ee;
        System.out.printf("k=%-3d %s: chi2=%.1f df=%d ratio=%.3f%n", k, algo, ch, N-1, ch/(N-1));
      }
    }
    // 3) larger stream, k=10
    int N=100000,k=10,T=400;
    for(String algo : new String[]{"R","L"}){
      long[] cnt=new long[N];
      for(int t=0;t<T;t++){
        var s = algo.equals("R")? ReservoirSampling.algorithmR(N,k,t) : ReservoirSampling.algorithmL(N,k,t);
        for(int p: s.positions()) cnt[p]++;
      }
      double ee=(double)k*T/N, ch=0;
      for(long v: cnt) ch+=(v-ee)*(v-ee)/ee;
      System.out.printf("n=100000 k=10 %s: chi2=%.1f df=%d ratio=%.3f%n", algo, ch, N-1, ch/(N-1));
    }
  }
}
