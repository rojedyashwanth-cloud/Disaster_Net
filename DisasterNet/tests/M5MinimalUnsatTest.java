/*
 * Checks that a documented unsatisfiable formula is unsatisfiable and minimally so.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M5MinimalUnsatTest {
  public static void main(String[] a){
    var f=ThreeSAT.minimalUnsatisfiable();
    System.out.println("unsat as documented: "+!SAT.bruteForce(f));
    List<int[]> cs=new java.util.ArrayList<>(f.clauses());
    boolean allMinimal=true;
    for(int drop=0;drop<cs.size();drop++){
      var copy=new java.util.ArrayList<>(cs); copy.remove(drop);
      if(!SAT.bruteForce(SAT.Formula.of(f.variables(), copy))) allMinimal=false;
    }
    System.out.println("every one-clause deletion is sat: "+allMinimal);
  }
}
