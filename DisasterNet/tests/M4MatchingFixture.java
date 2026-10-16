/*
 * Builds the bipartite fixtures the matching and cut tests share.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
public class M4MatchingFixture {
  static FlowNetwork clrs(){
    FlowNetwork net=new FlowNetwork();
    net.addEdge("s","v1",16); net.addEdge("s","v2",13); net.addEdge("v1","v2",10);
    net.addEdge("v2","v1",4); net.addEdge("v1","v3",12); net.addEdge("v3","v2",9);
    net.addEdge("v2","v4",14); net.addEdge("v4","v3",7); net.addEdge("v3","t",20);
    net.addEdge("v4","t",4);
    return net;
  }
  public static void main(String[] x){
    System.out.println("ff..."); System.out.flush();
    System.out.println(FordFulkerson.maxFlow(clrs(),"s","t").value()); System.out.flush();
    System.out.println("ek..."); System.out.flush();
    System.out.println(EdmondsKarp.maxFlow(clrs(),"s","t").value()); System.out.flush();
    System.out.println("dinic..."); System.out.flush();
    System.out.println(Dinic.maxFlow(clrs(),"s","t").value()); System.out.flush();
    System.out.println("done");
  }
}
