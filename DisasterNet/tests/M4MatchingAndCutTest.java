/*
 * Checks bipartite matching, Koenig covers and minimum cuts on those fixtures.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M4MatchingAndCutTest {
  static int fails=0;
  static void eq(String w,Object g,Object e){ if(!Objects.equals(g,e)){ System.out.println("FAIL "+w+" got "+g+" want "+e); fails++; } }
  public static void main(String[] x){
    // ---- minimum cut on the CLRS network: value must equal flow value
    FlowNetwork net=M4MatchingFixture.clrs();
    var cmp=MaximumFlow.compare(net,"s","t");
    eq("algorithms agree", cmp.agree(), true);
    eq("flow value", cmp.value(), 23);
    var cut=MinimumCut.compute(net,"s","t");
    eq("cut capacity == flow", cut.capacity(), 23);
    eq("cut separates s", cut.sourceSide().contains("s"), true);
    eq("cut excludes t", cut.sourceSide().contains("t"), false);
    // removing the cut edges really does disconnect s from t
    Set<String> cutSet=new HashSet<>(cut.crossingEdges());
    FlowNetwork pruned=new FlowNetwork();
    for(int arc=0;arc<net.arcCount();arc+=2){
      String e=net.name(net.tail(arc))+" -> "+net.name(net.head(arc));
      if(!cutSet.contains(e)) pruned.addEdge(net.name(net.tail(arc)),net.name(net.head(arc)),net.declaredCapacity(arc));
    }
    eq("flow after removing cut", Dinic.maxFlow(pruned,"s","t").value(), 0);

    // ---- bipartite matching: HK vs flow reduction vs brute force
    Random r=new Random(21); int bad=0;
    for(int t=0;t<400;t++){
      int nl=1+r.nextInt(5), nr=1+r.nextInt(5);
      List<String> L=new ArrayList<>(), R=new ArrayList<>(), all=new ArrayList<>();
      for(int i=0;i<nl;i++) L.add("T"+i);
      for(int j=0;j<nr;j++) R.add("D"+j);
      all.addAll(L); all.addAll(R);
      List<String[]> edges=new ArrayList<>();
      for(String a:L) for(String b:R) if(r.nextInt(100)<35) edges.add(new String[]{a,b});
      var hk=BipartiteMatching.hopcroftKarp(L,R,edges);
      var mf=BipartiteMatching.viaMaxFlow(L,R,edges);
      int bf=brute(L,R,edges);
      var cover=KonigTheorem.minVertexCover(L,R,edges);
      if(hk.size()!=bf||mf.size()!=bf){ if(bad<3) System.out.println("SIZE "+hk.size()+" "+mf.size()+" brute="+bf); bad++; }
      // matching is a valid matching
      Set<String> usedL=new HashSet<>(), usedR=new HashSet<>();
      for(var e: hk.pairs().entrySet()){ if(!usedL.add(e.getKey())||!usedR.add(e.getValue())) { System.out.println("INVALID MATCHING"); bad++; } }
      for(var e: hk.pairs().entrySet()){ boolean found=false; for(String[] ed:edges) if(ed[0].equals(e.getKey())&&ed[1].equals(e.getValue())) found=true;
        if(!found){ System.out.println("MATCHING NOT AN EDGE"); bad++; } }
      // Konig: cover covers everything, is minimal, independent set is a valid complement
      if(!KonigTheorem.covers(edges, cover.vertices())){ System.out.println("COVER MISSES EDGE"); bad++; }
      eq("konig size == matching size", cover.size(), hk.size());
      Set<String> indep=KonigTheorem.complementIndependentSet(all, cover);
      if(!KonigTheorem.isIndependent(edges, indep)){ System.out.println("INDEPENDENT SET INVALID"); bad++; }
    }
    eq("matching/cover mismatches", bad, 0);
    System.out.println(fails==0?"M4 MATCHING+CUT ALL PASS":"M4 FAILURES: "+fails);
  }
  // Kuhn's algorithm as an independent reference
  static int brute(List<String> L,List<String> R,List<String[]> edges){
    int[] mr=new int[R.size()]; Arrays.fill(mr,-1); int size=0;
    for(String u:L){ boolean[] vis=new boolean[R.size()]; if(kuhn(u,edges,L,R,mr,vis)) size++; }
    return size;
  }
  static boolean kuhn(String u,List<String[]> edges,List<String> L,List<String> R,int[] mr,boolean[] vis){
    for(String[] e: edges){
      if(!e[0].equals(u)) continue;
      int j=R.indexOf(e[1]);
      if(vis[j]) continue;
      vis[j]=true;
      if(mr[j]==-1||kuhn(L.get(mr[j]),edges,L,R,mr,vis)){ mr[j]=L.indexOf(u); return true; }
    }
    return false;
  }
}
