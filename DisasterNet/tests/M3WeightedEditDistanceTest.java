/*
 * Checks the weighted edit distance tables and traceback against unrestricted distance.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M3WeightedEditDistanceTest {
  static WeightedEditDistance.Costs C = WeightedEditDistance.fieldTranscriptionCosts(4);
  public static void main(String[] x){
    Random r=new Random(11);
    int dpBad=0, trBad=0;
    for(int t=0;t<3000;t++){
      String a=rs(r), b=rs(r);
      int dp=WeightedEditDistance.distance(a,b,C);
      int brute=brute(a,b);
      int tr=WeightedEditDistance.costOf(WeightedEditDistance.alignment(a,b,C),C);
      if(dp!=brute){ if(dpBad<3) System.out.println("DP!=BRUTE '"+a+"' '"+b+"' dp="+dp+" brute="+brute); dpBad++; }
      if(tr!=dp){ if(trBad<3) System.out.println("TR!=DP '"+a+"' '"+b+"' trace="+tr+" dp="+dp+" ops="+WeightedEditDistance.alignment(a,b,C)); trBad++; }
    }
    System.out.println("dp mismatches="+dpBad+" traceback mismatches="+trBad);
  }
  static String rs(Random r){ int n=r.nextInt(4); StringBuilder s=new StringBuilder();
    for(int i=0;i<n;i++) s.append((char)('a'+r.nextInt(3))); return s.toString(); }
  // brute force: BFS over alignment states (i,j,lastOpKind) keeping min cost
  static int brute(String a,String b){
    int n=a.length(), m=b.length();
    // state: (i,j) with the affine gap model. Do plain DFS over all edit scripts.
    return dfs(a,0,b,0,false,false);
  }
  static int dfs(String a,int i,String b,int j,boolean inDel,boolean inIns){
    if(i==a.length() && j==b.length()) return 0;
    int best=1_000_000;
    // match/substitute
    if(i<a.length() && j<b.length()){
      int c = (a.charAt(i)==b.charAt(j))?0:C.substitution(a.charAt(i),b.charAt(j));
      best=Math.min(best, c+dfs(a,i+1,b,j+1,false,false));
    }
    // delete a[i] (extends a delete run)
    if(i<a.length()){
      int c = inDel ? C.gapExtend() : C.gapOpen();
      best=Math.min(best, c+dfs(a,i+1,b,j,true,false));
    }
    // insert b[j] (extends an insert run)
    if(j<b.length()){
      int c = inIns ? C.gapExtend() : C.gapOpen();
      best=Math.min(best, c+dfs(a,i,b,j+1,false,true));
    }
    return best;
  }
}
