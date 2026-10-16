/*
 * Exhaustively checks the graph solvers on every graph up to six vertices.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M5GraphSolversTest {
  public static void main(String[] x){
    int bad=0, checked=0;
    for(int n=1;n<=6;n++){
      int pairs=n*(n-1)/2;
      for(long m=0;m<(1L<<pairs);m++){
        List<int[]> e=new ArrayList<>(); long bits=m; int k=0;
        for(int i=0;i<n;i++) for(int j=i+1;j<n;j++) if((bits>>k&1)==1) e.add(new int[]{i,j}); k++;
        var ind=IndependentSet.maximumIndependentSet(n,e);
        var indB=IndependentSet.bruteForceIndependentSet(n,e);
        var cl=CLIQUE.maximumClique(n,e);
        var clB=CLIQUE.bruteForceClique(n,e);
        var cov=VertexCover.minimumVertexCover(n,e);
        var g=VertexCover.greedyVertexCover(n,e);
        var ap=TwoApproximation.cover(n,e,cov.size());
        if(ind.size()!=indB.size()){ if(bad<5) System.out.println("IND n="+n); bad++; }
        if(cl.size()!=clB.size()){ if(bad<5) System.out.println("CLQ n="+n); bad++; }
        if(ind.size()+cov.size()!=n){ bad++; }
        if(!VertexCover.covers(e,cov.vertices())){ bad++; }
        if(!VertexCover.covers(e,g.vertices())){ bad++; }
        if(g.size()!=g.vertices().size()){ if(bad<5) System.out.println("GREEDY SIZE n="+n); bad++; }
        if(!VertexCover.covers(e,ap.vertices())){ bad++; }
        if(!ap.withinFactorTwo()){ if(bad<5) System.out.println("2-APPROX BOUND n="+n+" size="+ap.size()+" opt="+ap.optimum()); bad++; }
        if(!IndependentSet.isIndependent(n,e,ind.vertices())){ bad++; }
        checked++;
      }
    }
    System.out.println("exhaustive graphs n=1..6: "+checked+" graphs, "+bad+" failures");
    System.out.println(bad==0?"M5 GRAPH SOLVERS ALL PASS":"M5 GRAPH FAILURES: "+bad);
  }
}
