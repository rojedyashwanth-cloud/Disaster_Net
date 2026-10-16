# DisasterNet

A disaster-response log analysis system built as a vehicle for six algorithm
modules. Each module is implemented from scratch, exercised by a test that
compares it against something independent of itself, and then used by a pipeline
that produces an actual relief plan from the sample data.

The organising idea is that a module nobody calls is a module nobody should
trust. Every class here is reachable from `src/Main.java` or from a test, and the
pipeline prints the cross-checks it relies on so a reader can see the agreement
rather than take it on faith.

```
36 module classes, ~10,800 lines, 25 test classes
```

## Quick start

```bash
./build.sh            # compile everything into out/
./build.sh test       # compile, then run all 25 module tests
./build.sh run        # compile, then run the pipeline over data/ into output/
./build.sh menu       # compile, then start the interactive menu
./build.sh all        # compile, test, and run the pipeline
./build.sh test Brent # only tests whose name contains "Brent"
```

Running the program without `--pipeline` opens an interactive menu; `--pipeline`
runs all six stages end to end (this is what `./build.sh run` uses).

Requires a JDK 21 or later and nothing else; there are no external dependencies.
The build runs with `-Xlint:all` and is expected to be warning free.

```
ok     M1StringMatchingTest                      276 ms
...
25 of 25 tests passed in 28195 ms
```

## Layout

```
M1_String_Algorithms/     KMP, Z-algorithm, Rabin-Karp, Aho-Corasick
M2_Suffix_Structures/     suffix array, SA-IS, Kasai LCP, suffix tree, suffix automaton
M3_Dynamic_Programming/   Levenshtein, Damerau-Levenshtein, weighted edit distance,
                          bitmask DP, matrix chain
M4_Network_Flow/          Ford-Fulkerson, Edmonds-Karp, Dinic, minimum cut,
                          bipartite matching, Koenig's theorem
M5_NP_Approximation/      SAT/DPLL, 3-SAT, clique, independent set, vertex cover,
                          2-approximation, polynomial reductions
M6_Randomized_Parallel/   randomized quicksort, reservoir sampling, Miller-Rabin,
                          Blelloch scan, Fork/Join reduction, Brent cycle detection
src/                      the pipeline: Main, InputHandler, OutputHandler
tests/                    one test class per concern, plus a runner
data/                     sample incident reports, telemetry and resource data
output/                   pipeline artifacts, grouped by kind
```

## The pipeline

`Main` runs six stages and writes artifacts to `output/`. Each stage names the
module it exercises.

| Stage | Module | What it does |
| --- | --- | --- |
| 1 | M1 | Scans every report against all 53 registry keywords with Aho-Corasick and compares the result with the code declared in the report header. Counts fixed phrases with KMP, finds duplicated narratives with Rabin-Karp, measures cross-report similarity with the Z algorithm. |
| 2 | M2 | Builds a suffix array per report and checks it against a naive sort, then uses Kasai LCP to find repeated boilerplate, a suffix tree for the longest phrase two reports share, and a suffix automaton for keyword frequencies. |
| 3 | M3 | Repairs field-typed incident codes under unrestricted Damerau-Levenshtein and under a weighted transcription model, printing the alignment. Packs pooled district demand into convoys with bitmask DP and solves the assessment tour with Held-Karp. |
| 4 | M4 | Runs three max flow algorithms per commodity and requires them to agree before trusting the minimum cut. Places teams by bipartite matching, cross-checked through max flow, and derives the Koenig cover. |
| 5 | M5 | Compiles the stage-4 placement problem to 3-SAT, maximises the assignment, and requires it to agree with the matching. Decides the same formula again through the independent-set and vertex-cover readings of the reduction. Takes a genuine 2-approximation for the team conflict cover. |
| 6 | M6 | Blelloch scans the sensor series for the flood threshold crossing, reduces casualty totals over a Fork/Join pool, sorts with a randomized quicksort that reports its comparison count, draws an audit sample with both reservoir algorithms, tests district populations for primality, and finds the period of the legacy incident-code generator with Brent, cross-checked against Floyd and a hash table. |

## Interactive menu

`Menu` wraps the same stages in an operator shell. Options 1 to 6 run one module
each, option 7 browses and searches the CSV data, option 8 appends new incidents,
resources, teams and observations to `data/`, and option 9 compares warehouse
supply against district demand. The menu reloads the dataset after every write,
so new records are visible immediately.

```
========================================              DisasterNet
========================================

1. String Searching
2. Suffix Structures
3. Data Cleaning & Optimization
4. Network Flow & Team Matching
5. Constraints & Approximation
6. Randomized / Parallel Analytics
7. Browse/Search CSV Data
8. Add Incident / Resource / Team / Observation
9. View Available Resource Inventory
0. Exit
```

## How correctness is established

The tests do not restate the implementation. Each one compares against an
independent answer:

- **String matching** is checked against a brute-force substring scan over roughly
  20,000 text and pattern pairs, including overlapping and adversarial cases.
- **Suffix structures** are checked against a naive `String.compareTo` sort, and
  the pipeline prints that comparison on every run.
- **Edit distance** is checked against breadth-first search over the edit graph,
  which finds the true minimum because every edge costs one.
- **Max flow** is checked by requiring Ford-Fulkerson, Edmonds-Karp and Dinic to
  agree, plus a hand-computed value on the CLRS network.
- **Matching and cuts** are checked against Koenig's theorem: the cover size must
  equal the matching size, and the complement must be an independent set.
- **SAT and the reductions** are checked against brute force, and the whole graph
  solver family is checked exhaustively on every graph up to six vertices.
- **Cycle detection** is checked against Floyd and against a hash table, so the
  Brent counter offset is pinned from three directions.

A current run of the pipeline prints these agreements rather than asserting them:

```
algorithms agree: yes            (per commodity, all three flow algorithms)
the two agree: yes               (Hopcroft-Karp against max-flow matching)
the two agree: yes               (largest SAT assignment against max matching)
cover equals matching size: yes (this is Koenig's theorem)
reduction agrees with DPLL: yes
methods agree: yes               (Brent against Floyd against hash table)
Brent saved: 1733 evaluations against Floyd (1.5x)
```

## Data

The sample data is small but deliberately awkward, and the parser handles the
awkwardness rather than assuming it away:

- `incident_codes/codes.txt` is pipe separated so that descriptions and keyword
  lists may both contain commas.
- `incident_codes/noisy_codes.txt` holds the same codes as they arrive over the
  radio, with `O` for zero and missing vowels.
- `resource_data/rescue_teams.csv` has an **unquoted comma inside the
  capabilities field** (`water-rescue,medical`), so a naive CSV split yields six
  fields where the header declares five. The parser anchors on the id, the shift
  hours and the semicolon-separated sector list instead of on field count.
- `resource_data/routes.csv` marks three corridors `BLOCKED`. They are modelled as
  zero-capacity edges rather than deleted, so they still appear in the cut.

`InputHandler` validates across files and reports anything questionable as a
warning with its file and origin, including corridors pointing at unknown nodes,
team status entries naming teams that are not on the roster, and duplicate ids. A
silently dropped district would quietly weaken every later stage while the
resulting plan still looked plausible, so nothing is dropped quietly.

## Known limitations

These are real and worth stating:

- **The three commodity plans are not jointly feasible.** Corridor capacity is
  shared between commodities in reality, but each commodity is solved as its own
  single-commodity flow. Finding a plan that respects shared road capacity is
  multi-commodity flow, which module M5 exists to remind us is NP-hard.
- **Description-based code confirmation fails on badly mangled text.** For four of
  the eleven noisy codes the free-text description is too corrupted to identify
  the code, so the pipeline reports the code repair as confirmed by code text but
  *not* by description. It distinguishes that from a genuine disagreement between
  the two reads rather than merging the two cases.
- **No optimality is claimed for the NP-hard stages.** The conflict-graph cover is
  a 2-approximation; it is only called optimal when the exact solver confirms it.
- **Matrix chain multiplication is not used by the pipeline.** Nothing in relief
  logistics is a parenthesization problem, and inventing a use would be worse than
  leaving the class as a tested algorithm with no caller.
