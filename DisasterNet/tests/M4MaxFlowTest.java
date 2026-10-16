/*
 * Checks all three max flow implementations against each other and against a hand-built answer.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
public class M4MaxFlowTest {
  static int fails=0;
  static void eq(String what, Object got, Object want){ if(!java.util.Objects.equals(got,want)){ System.out.println("FAIL "+what+" got "+got+" want "+want); fails++; } }
  public static void main(String[] x){
    // CLRS network
    FlowNetwork net = new FlowNetwork();
    net.addEdge("s","v1",16); net.addEdge("s","v2",13); net.addEdge("v1","v2",10);
    net.addEdge("v2","v1",4); net.addEdge("v1","v3",12); net.addEdge("v3","v2",9);
    net.addEdge("v2","v4",14); net.addEdge("v4","v3",7); net.addEdge("v3","t",20);
    net.addEdge("v4","t",4);
    eq("ff value", FordFulkerson.maxFlow(net,"s","t").value(), 23);
    eq("ek value", EdmondsKarp.maxFlow(net,"s","t").value(), 23);
    var d = Dinic.maxFlow(net,"s","t");
    eq("dinic value", d.value(), 23);
    // flow conservation + integrality on every edge
    for(int e=0;e<net.arcCount();e+=2){
      int u=net.tail(e), v=net.head(e);
      if(u==net.node("s")||v==net.node("t")) continue;
      // only forward arcs carry a flow; flowOn(reverse) is leftover capacity
      int outU=0, inU=0, outV=0, inV=0;
      for(int a=0;a<net.arcCount();a+=2){
        if(net.tail(a)==u) outU+=net.flowOn(a);
        if(net.head(a)==u) inU+=net.flowOn(a);
        if(net.tail(a)==v) outV+=net.flowOn(a);
        if(net.head(a)==v) inV+=net.flowOn(a);
      }
      eq("conservation at "+net.name(u), outU, inU);
      eq("conservation at "+net.name(v), outV, inV);
      if(net.flowOn(e)<0||net.flowOn(e)>net.declaredCapacity(e)) { System.out.println("FAIL bounds "+net.describe(e)); fails++; }
    }
    // brute-force comparison on random graphs
    java.util.Random r=new java.util.Random(1); int bad=0;
    for(int t=0;t<300;t++){
      int n=2+r.nextInt(5);
      FlowNetwork g=new FlowNetwork();
      for(int i=0;i<n;i++) g.addNode("n"+i);
      for(int i=0;i<n;i++) for(int j=0;j<n;j++) if(i!=j&&r.nextBoolean()) g.addEdge("n"+i,"n"+j,1+r.nextInt(9));
      int bf=brute(g,n);
      int a=FordFulkerson.maxFlow(g,"n0","n"+(n-1)).value();
      int b=EdmondsKarp.maxFlow(g,"n0","n"+(n-1)).value();
      int c=Dinic.maxFlow(g,"n0","n"+(n-1)).value();
      if(a!=bf||b!=bf||c!=bf){ if(bad<3) System.out.println("MISMATCH brute="+bf+" ff="+a+" ek="+b+" dinic="+c); bad++; }
    }
    eq("random graph mismatches", bad, 0);
    System.out.println(fails==0?"M4 FLOW CORE ALL PASS":"M4 FAILURES: "+fails);
  }
  // exact max flow = min cut = cheapest cut over all node subsets
  static int brute(FlowNetwork g,int n){
    int s=g.node("n0"), tt=g.node("n"+(n-1)); int best=Integer.MAX_VALUE;
    for(int mask=0;mask<(1<<n);mask++){
      if((mask>>s&1)==0||(mask>>tt&1)==1) continue;
      int cut=0;
      for(int e=0;e<g.arcCount();e+=2){
        boolean inTail=(mask>>g.tail(e)&1)!=0, inHead=(mask>>g.head(e)&1)!=0;
        if(inTail&&!inHead) cut+=g.declaredCapacity(e);
      }
      best=Math.min(best,cut);
    }
    return best;
  }
}
