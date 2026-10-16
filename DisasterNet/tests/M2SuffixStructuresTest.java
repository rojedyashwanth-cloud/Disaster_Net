/*
 * Cross-checks the suffix array against a naive sort and exercises LCP, suffix tree and suffix automaton.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M2SuffixStructuresTest {
  public static void main(String[] x) {
    Random r = new Random(7);
    StringBuilder sb = new StringBuilder();
    String[] words = {"flood","evacuate","rescue","bridge","water","level","critical","shelter","medical","landslide","smoke","warning"};
    for (int i = 0; i < 20000; i++) sb.append(words[r.nextInt(words.length)]).append(' ');
    String s = sb.toString();
    long t0=System.nanoTime(); int[] sa1 = SuffixArray.build(s).sa(); long t1=System.nanoTime();
    int[] sa2 = SAIS.build(s); long t2=System.nanoTime();
    System.out.println("n=" + s.length());
    System.out.println("doubling " + (t1-t0)/1_000_000 + "ms  SAIS " + (t2-t1)/1_000_000 + "ms");
    System.out.println("identical: " + Arrays.equals(sa1, sa2));
    int[] l1 = Kasai.compute(s, sa1), l2 = Kasai.compute(s, sa2);
    System.out.println("lcp identical: " + Arrays.equals(l1, l2));
    System.out.println("lcp equals naive: " + Arrays.equals(l2, LCPArray.naive(s, sa2)));
    SuffixTree t = SuffixTree.build(s);
    System.out.println("tree nodes=" + t.nodeCount() + " maxDepth=" + t.maxDepth() + " distinctSubstrings=" + t.distinctSubstringCount());
    for (String p : new String[]{"evacuate","flood warning","zzz","bridge water"}) {
      List<Integer> occ = t.occurrencesOf(p);
      int brute = 0;
      for (int i = 0; i + p.length() <= s.length(); i++) if (s.startsWith(p, i)) brute++;
      System.out.println("occ(" + p + ") tree=" + occ.size() + " brute=" + brute + " match=" + (occ.size()==brute));
    }
    SuffixAutomaton sam = new SuffixAutomaton(s);
    System.out.println("SAM states=" + sam.stateCount() + " (<= 2n=" + (2*s.length()) + ")");
    for (String p : new String[]{"evacuate","flood warning","zzz"}) {
      int brute = 0;
      for (int i = 0; i + p.length() <= s.length(); i++) if (s.startsWith(p, i)) brute++;
      System.out.println("sam occ(" + p + ")=" + sam.occurrencesOf(p) + " brute=" + brute);
    }
    List<LCPArray.Repeated> rep = Kasai.repeatedSubstrings(s, 10);
    System.out.println("top repeats: " + rep.subList(0, Math.min(5, rep.size())));
  }
}
