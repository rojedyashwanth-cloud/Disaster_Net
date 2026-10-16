/*
 * Checks unrestricted Damerau-Levenshtein against optimal string alignment.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M3DamerauLevenshteinTest {
  public static void main(String[] z){
    System.out.println("ccac->dc  unrestricted impl="+DamerauLevenshtein.distance("ccac","dc")
      +" osa impl="+DamerauLevenshtein.osaDistance("ccac","dc")
      +" bfs="+bfs("ccac","dc")+" osaRef="+osaRef("ccac","dc"));
    Random r=new Random(7); int badU=0, badO=0;
    for(int t=0;t<400;t++){
      String a=rs(r), b=rs(r);
      if(DamerauLevenshtein.distance(a,b)!=bfs(a,b)){ if(badU<3) System.out.println("UNRESTRICTED '"+a+"' '"+b+"' impl="+DamerauLevenshtein.distance(a,b)+" bfs="+bfs(a,b)); badU++; }
      if(DamerauLevenshtein.osaDistance(a,b)!=osaRef(a,b)){ if(badO<3) System.out.println("OSA '"+a+"' '"+b+"' impl="+DamerauLevenshtein.osaDistance(a,b)+" ref="+osaRef(a,b)); badO++; }
      if(DamerauLevenshtein.osaDistance(a,b)<DamerauLevenshtein.distance(a,b)){ System.out.println("ORDER VIOLATION '"+a+"' '"+b+"'"); badU++; }
    }
    System.out.println("unrestricted mismatches="+badU+" osa mismatches="+badO);
  }
  static String rs(Random r){ int n=r.nextInt(5); StringBuilder s=new StringBuilder();
    for(int i=0;i<n;i++) s.append((char)('a'+r.nextInt(3))); return s.toString(); }
  // ground truth: minimum number of edit operations applied to the current string
  static int bfs(String a,String b){
    Set<String> seen=new HashSet<>(); Deque<String> q=new ArrayDeque<>(); Map<String,Integer> dist=new HashMap<>();
    dist.put(a,0); q.add(a);
    while(!q.isEmpty()){
      String cur=q.poll(); int d=dist.get(cur);
      if(cur.equals(b)) return d;
      if(d>=6) continue;
      for(int i=0;i<cur.length();i++){                                  // delete
        String nx=cur.substring(0,i)+cur.substring(i+1);
        if(dist.putIfAbsent(nx,d+1)==null) q.add(nx); }
      for(int i=0;i<=cur.length();i++) for(char ch='a';ch<='c';ch++){    // insert
        String nx=cur.substring(0,i)+ch+cur.substring(i);
        if(dist.putIfAbsent(nx,d+1)==null) q.add(nx); }
      for(int i=0;i<cur.length();i++) for(char ch='a';ch<='c';ch++){    // substitute
        if(ch==cur.charAt(i)) continue;
        String nx=cur.substring(0,i)+ch+cur.substring(i+1);
        if(dist.putIfAbsent(nx,d+1)==null) q.add(nx); }
      for(int i=0;i+1<cur.length();i++){                                 // transpose adjacent
        String nx=cur.substring(0,i)+cur.charAt(i+1)+cur.charAt(i)+cur.substring(i+2);
        if(dist.putIfAbsent(nx,d+1)==null) q.add(nx); }
    }
    return -1;
  }
  // OSA recursion straight from the Wikipedia definition
  static int osaRef(String a,String b){
    int m=a.length(), n=b.length(); int[][] d=new int[m+1][n+1];
    for(int i=0;i<=m;i++) d[i][0]=i;
    for(int j=0;j<=n;j++) d[0][j]=j;
    for(int i=1;i<=m;i++) for(int j=1;j<=n;j++){
      int cost = a.charAt(i-1)==b.charAt(j-1)?0:1;
      d[i][j]=Math.min(Math.min(d[i-1][j]+1,d[i][j-1]+1),d[i-1][j-1]+cost);
      if(i>1&&j>1&&a.charAt(i-1)==b.charAt(j-2)&&a.charAt(i-2)==b.charAt(j-1))
        d[i][j]=Math.min(d[i][j],d[i-2][j-2]+cost);
    }
    return d[m][n];
  }
}
