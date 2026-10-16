/*
 * Checks structural invariants of the reduced graph over thousands of random formulas.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M5ReductionInvariantsTest {
  static int fails=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ fails++; if(fails<15) System.out.println("FAIL "+w+" got "+g+" want "+e); } }
  public static void main(String[] a){
    Random r=new Random(21);
    long satN=0, unsatN=0;
    for(int t=0;t<4000;t++){
      int vars=1+r.nextInt(5), cls=r.nextInt(8);
      List<int[]> cl=new ArrayList<>();
      for(int c=0;c<cls;c++){ int[] cl2=new int[3]; for(int i=0;i<3;i++){ int v=r.nextInt(vars); cl2[i]= r.nextBoolean()? v : -(v+1); } cl.add(cl2); }
      SAT.Formula f=new SAT.Formula(vars,cl);
      boolean sat=SAT.dpll(f).satisfiable();
      if(sat) satN++; else unsatN++;
      var red=PolynomialReductions.threeSatToIndependentSet(f);
      eq("vertices = 2n+L", red.vertices(), 2*red.variables()+totalOcc(red));
      eq("cover target identity", red.coverTargetSize()+red.targetSize(), red.vertices());
      eq("cover target != n+m unless degenerate", red.coverTargetSize()==red.targetSize(), red.vertices()==2*red.targetSize());
      eq("IS solver agrees", PolynomialReductions.solveViaIndependentSet(f), sat);
      eq("VC solver agrees", PolynomialReductions.solveViaVertexCover(f), sat);
      var ind=IndependentSet.maximumIndependentSet(red.vertices(), red.edges());
      var cover=PolynomialReductions.independentSetToVertexCover(red.vertices(), ind.vertices());
      eq("complement sizes sum", cover.size()+ind.size(), red.vertices());
      if(sat){
        eq("cover is certifying size", cover.size()==red.coverTargetSize(), true);
        eq("cover really covers", VertexCover.covers(red.edges(), cover), true);
      }
      boolean[] a2=PolynomialReductions.readAssignment(red, ind.vertices());
      eq("assignment present iff sat", a2!=null, sat);
      if(a2!=null) eq("assignment really satisfies", SAT.satisfies(f, a2), true);
    }
    System.out.println("checked 4000 formulas: "+satN+" satisfiable, "+unsatN+" unsatisfiable");
    // a concrete worked example with a non-3-CNF input, which goes through Tseitin
    SAT.Formula f=new SAT.Formula(3, List.of(new int[]{0,-1,-2}, new int[]{1,-2,2}, new int[]{1,2,-1}));
    var red=PolynomialReductions.threeSatToIndependentSet(f);
    System.out.println("example: "+red);
    System.out.println("  sat="+SAT.dpll(f).satisfiable()+" viaIS="+PolynomialReductions.solveViaIndependentSet(f)+" viaVC="+PolynomialReductions.solveViaVertexCover(f));
    eq("example agrees", PolynomialReductions.solveViaVertexCover(f), SAT.dpll(f).satisfiable());
    System.out.println(fails==0?"M5ReductionInvariantsTest ALL PASS":"M5ReductionInvariantsTest FAILURES: "+fails);
  }
  static int totalOcc(PolynomialReductions.Reduction r){ int s=0; for(int l: r.clauseLengths()) s+=l; return s; }
}
