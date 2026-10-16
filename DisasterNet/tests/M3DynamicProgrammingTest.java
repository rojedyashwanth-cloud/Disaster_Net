/*
 * Exercises the M3 dynamic programs against brute force where one is affordable.
 *
 * Every assertion here compares the module under test against something independent of it:
 * a brute force search, a sequential implementation, or a second algorithm solving the same
 * problem. An assertion that only restates the implementation proves nothing.
 */
import java.util.*;
public class M3DynamicProgrammingTest {
  static int fails=0;
  static void eq(String w, Object a, Object b){ if(!Objects.equals(a,b)){System.out.println("FAIL "+w+"\n got  "+a+"\n want "+b);fails++;} }
  public static void main(String[] x){
    // Levenshtein vs known values
    eq("lev kitten", Levenshtein.distance("kitten","sitting"), 3);
    eq("lev same", Levenshtein.distance("flood","flood"), 0);
    eq("lev empty", Levenshtein.distance("",""), 0);
    eq("lev abc->", Levenshtein.distance("abc",""), 3);
    eq("lev ->abc", Levenshtein.distance("","abc"), 3);
    eq("lev fl", Levenshtein.distance("FL-O100","FL-0100"), 1);
    // symmetric + triangle on random strings
    Random r = new Random(3);
    for (int t=0;t<2000;t++){
      String a=rand(r), b=rand(r);
      int d1=Levenshtein.distance(a,b), d2=Levenshtein.distance(b,a);
      if(d1!=d2){System.out.println("FAIL asym "+a+" "+b+" "+d1+" "+d2);fails++;break;}
      // alignment cost must equal distance
      List<Levenshtein.Op> ops=Levenshtein.alignment(a,b);
      int c=0; for(Levenshtein.Op o:ops) if(!o.kind().equals("match")) c++;
      if(c!=d1){System.out.println("FAIL aligncost '"+a+"'->'"+b+"' ops="+c+" d="+d1+" "+ops);fails++;break;}
    }
    // bounded distance agrees when within threshold
    for (int t=0;t<500;t++){
      String a=rand(r), b=rand(r); int th=1+r.nextInt(4);
      eq("bounded", Levenshtein.distanceWithin(a,b,th)<=th, Levenshtein.distance(a,b)<=th);
    }
    // Damerau
    eq("dam transposition", DamerauLevenshtein.distance("FL-1000","FL-0100"), 1);
    eq("osa transposition", DamerauLevenshtein.osaDistance("FL-1000","FL-0100"), 1);
    eq("dam CA/ABC unrestricted", DamerauLevenshtein.distance("CA","ABC"), 2);
    eq("dam CA/ABC osa", DamerauLevenshtein.osaDistance("CA","ABC"), 3);
    eq("dam code", DamerauLevenshtein.distance("FL-O1O0","FL-0100"), 2);
    for (int t=0;t<1000;t++){
      String a=rand(r), b=rand(r);
      if(DamerauLevenshtein.distance(a,b)>Levenshtein.distance(a,b)){System.out.println("FAIL dam<lev "+a+" "+b);fails++;break;}
      if(DamerauLevenshtein.osaDistance(a,b)<DamerauLevenshtein.distance(a,b)){System.out.println("FAIL osa>dam "+a+" "+b);fails++;break;}
    }
    // weighted
    WeightedEditDistance.Costs field = WeightedEditDistance.fieldTranscriptionCosts(4);
    System.out.println("weighted FL-O100->FL-0100 = " + WeightedEditDistance.distance("FL-O100","FL-0100",field));
    System.out.println("weighted ops = " + WeightedEditDistance.alignment("FL-O100","FL-0100",field));
    eq("weighted costOf == dp", WeightedEditDistance.costOf(WeightedEditDistance.alignment("FL-O100","FL-0100",field),field),
       WeightedEditDistance.distance("FL-O100","FL-0100",field));
    for (int t=0;t<500;t++){
      String a=rand(r), b=rand(r);
      int dp=WeightedEditDistance.distance(a,b,field);
      int back=WeightedEditDistance.costOf(WeightedEditDistance.alignment(a,b,field),field);
      eq("weighted traceback "+a+"/"+b, back, dp);
      // identity: cost(x,x)=0
      eq("weighted identity", WeightedEditDistance.distance(a,a,field), 0);
    }
    // TSP vs brute force permutation
    for (int t=0;t<60;t++){
      int n=2+r.nextInt(6);
      double[][] d=new double[n][n];
      for(int i=0;i<n;i++) for(int j=0;j<n;j++) if(i!=j) d[i][j]=1+r.nextInt(20);
      BitmaskDP.Tour tour=BitmaskDP.shortestTour(d);
      int brute=bruteTsp(d);
      eq("tsp n="+n, tour.cost(), brute);
      eq("tsp visits all", BitmaskDP.maskOf(tour.order()), (1<<n)-1);
      // verify the tour length matches its own order
      int recomputed=0; for(int i=0;i<n;i++) recomputed+=(int)d[tour.order().get(i)][tour.order().get((i+1)%n)];
      eq("tsp recompute n="+n, recomputed, tour.cost());
    }
    // matrix chain vs brute force over all parenthesisations
    for (int t=0;t<200;t++){
      int n=2+r.nextInt(5);
      int[] p=new int[n+1];
      for(int i=0;i<=n;i++) p[i]=1+r.nextInt(9);
      MatrixChainMultiplication.Result res=MatrixChainMultiplication.optimize(p);
      eq("mcm n="+n+" "+java.util.Arrays.toString(p), res.cost(), bruteMcm(p));
    }
    System.out.println("mcm ex: " + MatrixChainMultiplication.optimize(new int[]{40,20,30,10,30}).order());
    System.out.println(fails==0?"M3 ALL PASS":"M3 FAILURES: "+fails);
  }
  static String rand(Random r){ int n=r.nextInt(7); StringBuilder sb=new StringBuilder();
    for(int i=0;i<n;i++) sb.append((char)('a'+r.nextInt(4))); return sb.toString(); }
  static int bruteTsp(double[][] d){
    int n=d.length; Integer[] idx=new Integer[n-1];
    for(int i=1;i<n;i++) idx[i-1]=i;
    List<List<Integer>> perms=new ArrayList<>(); permute(idx,0,perms);
    int best=Integer.MAX_VALUE;
    for(List<Integer> p:perms){ int c=(int)d[0][p.get(0)]; for(int i=0;i+1<p.size();i++) c+=(int)d[p.get(i)][p.get(i+1)]; c+=(int)d[p.get(p.size()-1)][0]; best=Math.min(best,c); }
    return best;
  }
  static void permute(Integer[] a,int k,List<List<Integer>> out){ if(k==a.length){out.add(new ArrayList<>(List.of(a)));return;}
    for(int i=k;i<a.length;i++){ Integer t=a[k];a[k]=a[i];a[i]=t; permute(a,k+1,out); t=a[k];a[k]=a[i];a[i]=t; } }
  static int bruteMcm(int[] p){ int n=p.length-1; return mcmRec(p,0,n-1); }
  static int mcmRec(int[] p,int i,int j){ if(i>=j) return 0; int best=Integer.MAX_VALUE;
    for(int k=i;k<j;k++) best=Math.min(best, mcmRec(p,i,k)+mcmRec(p,k+1,j)+p[i]*p[k+1]*p[j+1]); return best; }
}
