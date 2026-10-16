/*
 * Checks 3-CNF detection and Tseitin conversion.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M5ThreeSatTest {
  static int fails=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ System.out.println("FAIL "+w+" got "+g+" want "+e); fails++; } }
  public static void main(String[] x){
    eq("minimal unsat is unsat", SAT.dpll(ThreeSAT.minimalUnsatisfiable()).satisfiable(), false);
    eq("minimal unsat is 3cnf", ThreeSAT.isThreeCNF(ThreeSAT.minimalUnsatisfiable()), true);
    // dropping one clause makes it satisfiable (minimal unsatisfiability)
    var f=ThreeSAT.minimalUnsatisfiable();
    var less=new SAT.Formula(f.variables(), f.clauses().subList(0,7));
    eq("one clause short is sat", SAT.dpll(less).satisfiable(), true);
    // Tseitin conversion preserves satisfiability in both directions
    Random r=new Random(77); int bad=0;
    for(int t=0;t<800;t++){
      int v=1+r.nextInt(5), c=1+r.nextInt(6);
      List<int[]> cs=new ArrayList<>();
      for(int i=0;i<c;i++){ int len=1+r.nextInt(6); int[] cl=new int[len];
        for(int k=0;k<len;k++){ int var=r.nextInt(v); boolean neg=r.nextBoolean(); cl[k]=neg?-(var+1):var; }
        cs.add(cl); }
      var orig=SAT.Formula.of(v,cs);
      var conv=ThreeSAT.toThreeCNF(orig);
      if(!ThreeSAT.isThreeCNF(conv)){ System.out.println("NOT 3CNF"); bad++; }
      boolean a=SAT.bruteForce(orig), b=SAT.bruteForce(conv);
      if(a!=b){ if(bad<4) System.out.println("CONVERSION MISMATCH sat "+a+"->"+b+" : "+orig); bad++; }
    }
    eq("tseitin mismatches", bad, 0);
    // model enumeration
    // "|" separates clauses, so this is x1 AND x2: exactly one model
    var m=ThreeSAT.allModels(SAT.Formula.parse("x1 | x2"), 10);
    eq("models of x1 and x2", m.size(), 1);
    // whitespace separates literals inside a clause, so this is (x1 OR x2 OR x3)
    var m3=ThreeSAT.allModels(SAT.Formula.parse("x1 x2 x3"), 20);
    eq("models of (x1|x2|x3)", m3.size(), 7);
    // a tautological clause is satisfied by every assignment
    var m2=ThreeSAT.allModels(SAT.Formula.parse("x1 -x1"), 10);
    eq("models of tautology over 1 var", m2.size(), 2);
    eq("models of contradiction", ThreeSAT.allModels(SAT.Formula.parse("x1 | -x1"), 10).size(), 0);
    // verify each enumerated model satisfies the original
    // (x1|x2) and (-x1|x2) and (x1|-x2) force x1 and x2 both true
    var f2=SAT.Formula.parse("x1 x2 | -x1 x2 | x1 -x2");
    for(boolean[] as: ThreeSAT.allModels(f2, 20)) eq("enumerated model satisfies", SAT.satisfies(f2, as), true);
    eq("models of that formula", ThreeSAT.allModels(f2,20).size(), 1);
    System.out.println(fails==0?"3SAT ALL PASS":"3SAT FAILURES: "+fails);
  }
}
