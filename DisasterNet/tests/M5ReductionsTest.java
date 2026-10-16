/*
 * Checks the 3-SAT reductions in both directions over random formulas.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M5ReductionsTest {
  static int fails=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ System.out.println("FAIL "+w+" got "+g+" want "+e); fails++; } }
  public static void main(String[] x){
    eq("reduction solver on sat formula", PolynomialReductions.solveViaIndependentSet(SAT.Formula.parse("x1 x2 | -x1 x3 | -x2 x3")), true);
    eq("reduction solver on unsat", PolynomialReductions.solveViaIndependentSet(ThreeSAT.minimalUnsatisfiable()), false);

    Random r=new Random(303); int bad=0;
    for(int t=0;t<600;t++){
      int v=1+r.nextInt(5), c=1+r.nextInt(5);
      List<int[]> cs=new ArrayList<>();
      for(int i=0;i<c;i++){ int len=1+r.nextInt(3); int[] cl=new int[len];
        for(int k=0;k<len;k++){ int var=r.nextInt(v); boolean neg=r.nextBoolean(); cl[k]=neg?-(var+1):var; }
        cs.add(cl); }
      var f=SAT.Formula.of(v,cs);
      boolean sat=SAT.bruteForce(f);
      var red=PolynomialReductions.threeSatToIndependentSet(f);
      var ind=IndependentSet.maximumIndependentSet(red.vertices(), red.edges());
      boolean viaGraph = ind.size()==red.targetSize();
      if(viaGraph!=sat){ if(bad<4) System.out.println("REDUCTION MISMATCH sat="+sat+" alpha="+ind.size()+" target="+red.targetSize()+" : "+f); bad++; }
      if(!IndependentSet.isIndependent(red.vertices(), red.edges(), ind.vertices())){ System.out.println("NOT INDEPENDENT"); bad++; }
      if(sat){
        boolean[] as=PolynomialReductions.readAssignment(red, ind.vertices());
        if(as==null){ System.out.println("NO ASSIGNMENT READ: "+f); bad++; }
        else if(!SAT.satisfies(f, as)){ System.out.println("RECOVERED ASSIGNMENT FAILS: "+f+" -> "+Arrays.toString(as)); bad++; }
      } else {
        if(PolynomialReductions.readAssignment(red, ind.vertices())!=null){ System.out.println("SPURIOUS ASSIGNMENT for unsat "+f); bad++; }
      }
      // cover form: complement of the certifying IS has size |V| - (n+m)
      List<Integer> comp=PolynomialReductions.independentSetToVertexCover(red.vertices(), ind.vertices());
      if(!VertexCover.covers(red.edges(), comp)){ System.out.println("COMPLEMENT NOT COVER"); bad++; }
      eq("complement size", comp.size(), red.vertices()-ind.size());
    }
    eq("reduction mismatches", bad, 0);

    // exhaustive: every 2-variable formula with up to 3 clauses of length 1..3
    int[] lits={0,-1,1,-2};
    List<int[]> pool=new ArrayList<>();
    for(int a: lits) pool.add(new int[]{a});
    for(int a: lits) for(int b: lits) pool.add(new int[]{a,b});
    for(int a: lits) for(int b: lits) for(int cc: lits) pool.add(new int[]{a,b,cc});
    int checked=0,bad2=0;
    for(int[] c1: pool) for(int[] c2: pool) for(int[] c3: pool){
      var f=SAT.Formula.of(2,List.of(c1,c2,c3));
      boolean sat=SAT.bruteForce(f);
      if(PolynomialReductions.solveViaIndependentSet(f)!=sat){ if(bad2<4) System.out.println("EXH "+f+" sat="+sat); bad2++; }
      checked++;
    }
    System.out.println("exhaustive 2-var/3-clause via reduction: "+checked+" formulas, "+bad2+" wrong");
    eq("exhaustive mismatches", bad2, 0);

    int n=6; Random r2=new Random(9); List<int[]> edges=new ArrayList<>();
    for(int i=0;i<n;i++) for(int j=i+1;j<n;j++) if(r2.nextInt(100)<50) edges.add(new int[]{i,j});
    eq("omega(G) + alpha(G) = n", CLIQUE.maximumClique(n,edges).size()+IndependentSet.maximumIndependentSet(n,edges).size(), n);
    eq("cover<->independent involution",
       PolynomialReductions.vertexCoverToIndependentSet(n, PolynomialReductions.independentSetToVertexCover(n, List.of(0,2,4))), List.of(0,2,4));
    System.out.println(fails==0?"REDUCTIONS ALL PASS":"REDUCTIONS FAILURES: "+fails);
  }
}
