/*
 * Checks DPLL against brute force over small formulas.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M5SatTest {
  static int fails=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ System.out.println("FAIL "+w+" got "+g+" want "+e); fails++; } }
  public static void main(String[] x){
    eq("simple sat", SAT.dpll(SAT.Formula.parse("x1 x2")).satisfiable(), true);
    eq("x1|-x1 unsat", SAT.dpll(SAT.Formula.parse("x1 | -x1")).satisfiable(), false);
    String unsat8 = "x1 x2 x3 | -x1 -x2 -x3 | x1 x2 -x3 | x1 -x2 x3 | -x1 x2 x3 | x1 -x2 -x3 | -x1 x2 -x3 | -x1 -x2 x3";
    eq("known unsat 8-clause", SAT.dpll(SAT.Formula.parse(unsat8)).satisfiable(), false);
    eq("known unsat matches brute", SAT.bruteForce(SAT.Formula.parse(unsat8)), false);
    var f=SAT.Formula.parse("x1 -x2 x3"); var s=SAT.dpll(f);
    eq("sat found", s.satisfiable(), true);
    eq("returned assignment satisfies", SAT.satisfies(f, s.assignment()), true);
    eq("brute agrees on parse", SAT.bruteForce(f), s.satisfiable());
    Random r=new Random(31); int bad=0;
    for(int t=0;t<1500;t++){
      int v=1+r.nextInt(7), c=1+r.nextInt(10);
      List<int[]> cs=new ArrayList<>();
      for(int i=0;i<c;i++){ int len=1+r.nextInt(3); int[] cl=new int[len];
        for(int k=0;k<len;k++){ int var=r.nextInt(v); boolean neg=r.nextBoolean(); cl[k]=neg?-(var+1):var; }
        cs.add(cl); }
      var fo=SAT.Formula.of(v,cs);
      boolean bf=SAT.bruteForce(fo), dp=SAT.dpll(fo).satisfiable();
      if(bf!=dp){ if(bad<4) System.out.println("MISMATCH v="+v+" "+fo+" brute="+bf+" dpll="+dp); bad++; }
      if(dp && !SAT.satisfies(fo, SAT.dpll(fo).assignment())){ System.out.println("BAD ASSIGNMENT"); bad++; }
    }
    eq("dpll/brute mismatches", bad, 0);
    System.out.println(fails==0?"SAT ALL PASS":"SAT FAILURES: "+fails);
  }
}
