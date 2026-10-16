/*
 * Walks one small formula through the reduction and prints every intermediate result.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M5ReductionWalkthroughTest {
  public static void main(String[] x){
    var f=SAT.Formula.parse("x1");
    var red=PolynomialReductions.threeSatToIndependentSet(f);
    System.out.println("formula: "+f+" vars="+f.variables()+" clauses="+f.clauses().size());
    System.out.println("reduction: "+red);
    System.out.println("edges: "+red.edges());
    var comp=IndependentSet.complement(red.vertices(), red.edges());
    System.out.println("complement edges: "+comp);
    System.out.println("max clique in complement: "+CLIQUE.maximumClique(red.vertices(), comp));
    System.out.println("max indep: "+IndependentSet.maximumIndependentSet(red.vertices(), red.edges()));
    System.out.println("brute indep: "+IndependentSet.bruteForceIndependentSet(red.vertices(), red.edges()));
    System.out.println("expected size = "+red.variables()+"+"+red.clauses());
  }
}
