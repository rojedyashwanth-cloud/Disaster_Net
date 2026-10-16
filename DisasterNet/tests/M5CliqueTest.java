/*
 * Checks maximum clique against brute force.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M5CliqueTest {
  static int fails=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ System.out.println("FAIL "+w+" got "+g+" want "+e); fails++; } }
  public static void main(String[] x){
    // Petersen graph: max clique 2, chromatic-ish structure
    List<int[]> petersen=new ArrayList<>();
    int[][] pe={{0,1},{1,2},{2,3},{3,4},{4,0},{5,7},{7,9},{9,6},{6,8},{8,5},{0,5},{1,6},{2,7},{3,8},{4,9}};
    for(int[] e: pe) petersen.add(e);
    eq("petersen max clique", CLIQUE.maximumClique(10,petersen).size(), 2);
    eq("petersen brute", CLIQUE.bruteForceClique(10,petersen).size(), 2);
    // C5 has clique 2; K5 has clique 5; empty has 1
    List<int[]> c5=List.of(new int[]{0,1},new int[]{1,2},new int[]{2,3},new int[]{3,4},new int[]{4,0});
    eq("C5 clique", CLIQUE.maximumClique(5,c5).size(), 2);
    List<int[]> k5=new ArrayList<>();
    for(int i=0;i<5;i++) for(int j=i+1;j<5;j++) k5.add(new int[]{i,j});
    eq("K5 clique", CLIQUE.maximumClique(5,k5).size(), 5);
    eq("empty graph clique", CLIQUE.maximumClique(4,List.of()).size(), 1);
    eq("no edges brute", CLIQUE.bruteForceClique(4,List.of()).size(), 1);
    // differential test: branch and bound vs exhaustive
    Random r=new Random(101); int bad=0;
    for(int t=0;t<500;t++){
      int n=1+r.nextInt(9);
      List<int[]> edges=new ArrayList<>();
      for(int i=0;i<n;i++) for(int j=i+1;j<n;j++) if(r.nextInt(100)<45) edges.add(new int[]{i,j});
      int fast=CLIQUE.maximumClique(n,edges).size(), slow=CLIQUE.bruteForceClique(n,edges).size();
      if(fast!=slow){ if(bad<4) System.out.println("CLIQUE MISMATCH n="+n+" fast="+fast+" brute="+slow+" edges="+edges); bad++; }
      var cl=CLIQUE.maximumClique(n,edges);
      if(!CLIQUE.isClique(n,edges,cl.vertices())){ System.out.println("NOT A CLIQUE"); bad++; }
    }
    eq("clique mismatches", bad, 0);
    System.out.println(fails==0?"CLIQUE ALL PASS":"CLIQUE FAILURES: "+fails);
  }
}
