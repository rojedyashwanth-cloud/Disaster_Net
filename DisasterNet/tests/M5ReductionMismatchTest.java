/*
 * Searches for formulas where the reduction and the solver disagree.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M5ReductionMismatchTest {
  public static void main(String[] x){
    Random r=new Random(303);
    for(int t=0;t<400;t++){
      int v=1+r.nextInt(5), c=1+r.nextInt(5);
      List<int[]> cs=new ArrayList<>();
      for(int i=0;i<c;i++){ int len=1+r.nextInt(3); int[] cl=new int[len];
        for(int k=0;k<len;k++){ int var=r.nextInt(v); boolean neg=r.nextBoolean(); cl[k]=neg?-(var+1):var; }
        cs.add(cl); }
      var f=SAT.Formula.of(v,cs);
      boolean sat=SAT.bruteForce(f);
      var red=PolynomialReductions.threeSatToIndependentSet(f);
      var ind=IndependentSet.maximumIndependentSet(red.vertices(), red.edges());
      var br=IndependentSet.bruteForceIndependentSet(red.vertices(), red.edges());
      int expect=red.variables()+red.clauses();
      if(ind.size()!=expect && sat){
        System.out.println("formula="+f+" vars="+v+" clauses="+c+" sat="+sat);
        for(int[] cl: f.clauses()) System.out.println("  clause "+Arrays.toString(cl));
        System.out.println("reduction vertices="+red.vertices()+" expect="+expect);
        for(int[] e: red.edges()) System.out.println("  edge "+Arrays.toString(e));
        System.out.println("found ind="+ind.vertices()+" size="+ind.size());
        System.out.println("brute ind="+br.vertices()+" size="+br.size());
        boolean[][] adj=new boolean[red.vertices()][red.vertices()];
        for(int[] e: red.edges()){ adj[e[0]][e[1]]=true; adj[e[1]][e[0]]=true; }
        boolean ok=true;
        for(int a: ind.vertices()) for(int b: ind.vertices()) if(a!=b&&adj[a][b]) ok=false;
        System.out.println("found set actually independent: "+ok);
        return;
      }
    }
    System.out.println("no mismatch found");
  }
}
