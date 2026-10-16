/*
 * Checks unrestricted Damerau-Levenshtein against breadth first search over the edit graph.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M3DamerauAgainstBfsTest {
  public static void main(String[] z){
    Random r=new Random(5); int bad=0, checked=0;
    for(int t=0;t<250;t++){
      String a=rs(r), b=rs(r);
      if(a.length()+b.length()>7) continue;
      int g=bfs(a,b); checked++;
      if(DamerauLevenshtein.distance(a,b)!=g){ if(bad<5) System.out.println("'"+a+"'->'"+b+"' impl="+DamerauLevenshtein.distance(a,b)+" bfs="+g); bad++; }
    }
    System.out.println("checked="+checked+" unrestricted mismatches vs BFS="+bad);
  }
  static String rs(Random r){ int n=r.nextInt(4); StringBuilder s=new StringBuilder();
    for(int i=0;i<n;i++) s.append((char)('a'+r.nextInt(3))); return s.toString(); }
  // minimum number of single-char edits (incl. adjacent transposition) to turn a into b
  static int bfs(String a,String b){
    Map<String,Integer> dist=new HashMap<>(); Deque<String> q=new ArrayDeque<>();
    dist.put(a,0); q.add(a);
    while(!q.isEmpty()){
      String cur=q.poll(); int d=dist.get(cur);
      if(cur.equals(b)) return d;
      List<String> nxt=new ArrayList<>();
      for(int i=0;i<cur.length();i++) nxt.add(cur.substring(0,i)+cur.substring(i+1));
      for(int i=0;i<=cur.length();i++) for(char ch='a';ch<='c';ch++) nxt.add(cur.substring(0,i)+ch+cur.substring(i));
      for(int i=0;i<cur.length();i++) for(char ch='a';ch<='c';ch++) if(ch!=cur.charAt(i)) nxt.add(cur.substring(0,i)+ch+cur.substring(i+1));
      for(int i=0;i+1<cur.length();i++) nxt.add(cur.substring(0,i)+cur.charAt(i+1)+cur.charAt(i)+cur.substring(i+2));
      for(String nx:nxt) if(dist.putIfAbsent(nx,d+1)==null) q.add(nx);
    }
    return -1;
  }
}
