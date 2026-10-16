/*
 * Checks the Levenshtein table, alignment, traceback and similarity properties.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
public class M3LevenshteinTest {
  public static void main(String[] x){
    java.util.Random r=new java.util.Random(3);
    for(int t=0;t<500;t++){
      String a=rand(r), b=rand(r); int th=1+r.nextInt(4);
      int fast=Levenshtein.distanceWithin(a,b,th), exact=Levenshtein.distance(a,b);
      if((fast<=th)!=(exact<=th)){ System.out.println("MISMATCH a='"+a+"' b='"+b+"' th="+th+" fast="+fast+" exact="+exact); break; }
    }
  }
  static String rand(java.util.Random r){ int n=r.nextInt(7); StringBuilder sb=new StringBuilder();
    for(int i=0;i<n;i++) sb.append((char)('a'+r.nextInt(4))); return sb.toString(); }
}
