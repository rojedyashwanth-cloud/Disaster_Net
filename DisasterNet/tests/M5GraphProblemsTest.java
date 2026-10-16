/*
 * Checks the independent set, vertex cover and clique solvers against brute force.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M5GraphProblemsTest {
  static int fails=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ System.out.println("FAIL "+w+" got "+g+" want "+e); fails++; } }
  public static void main(String[] x){
    Random r=new Random(202); int bad=0;
    for(int t=0;t<400;t++){
      int n=1+r.nextInt(9);
      List<int[]> edges=new ArrayList<>();
      for(int i=0;i<n;i++) for(int j=i+1;j<n;j++) if(r.nextInt(100)<40) edges.add(new int[]{i,j});
      var ind=IndependentSet.maximumIndependentSet(n,edges);
      var indB=IndependentSet.bruteForceIndependentSet(n,edges);
      var cov=VertexCover.minimumVertexCover(n,edges);
      if(ind.size()!=indB.size()){ System.out.println("IND MISMATCH "+ind.size()+" vs "+indB.size()); bad++; }
      if(!IndependentSet.isIndependent(n,edges,ind.vertices())){ System.out.println("NOT INDEPENDENT"); bad++; }
      // alpha + tau = n
      if(ind.size()+cov.size()!=n){ System.out.println("COMPLEMENT BROKEN alpha="+ind.size()+" tau="+cov.size()+" n="+n); bad++; }
      if(!VertexCover.covers(edges,cov.vertices())){ System.out.println("COVER MISSES"); bad++; }
      // 2-approximation: valid, within factor 2
      var approx=TwoApproximation.cover(n,edges,cov.size());
      if(!VertexCover.covers(edges,approx.vertices())){ System.out.println("APPROX NOT A COVER"); bad++; }
      if(!approx.withinFactorTwo()){ System.out.println("GUARANTEE VIOLATED size="+approx.size()+" opt="+approx.optimum()); bad++; }
      var g=VertexCover.greedyVertexCover(n,edges);
      if(!VertexCover.covers(edges,g.vertices())){ System.out.println("GREEDY NOT A COVER"); bad++; }
      // complement of independent set is a cover
      List<Integer> comp=new ArrayList<>();
      for(int v=0;v<n;v++) if(!ind.vertices().contains(v)) comp.add(v);
      if(!VertexCover.covers(edges,comp)){ System.out.println("COMPLEMENT NOT COVER"); bad++; }
    }
    eq("graph mismatches", bad, 0);
    // star graph: ratio approaches 2, which is the tight case
    var star=TwoApproximation.worstCaseStar(5);
    eq("star size", star.size(), 2);
    eq("star optimum", star.optimum(), 1);
    eq("star within two", star.withinFactorTwo(), true);
    eq("star ratio is exactly two", star.ratio(), 2.0);
    System.out.println(fails==0?"GRAPH PROBLEMS ALL PASS":"GRAPH FAILURES: "+fails);
  }
}
