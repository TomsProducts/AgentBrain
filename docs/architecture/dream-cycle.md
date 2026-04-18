# Dream Cycle

The Dream Cycle is AgentBrain's nightly memory consolidation process. It runs at 03:00 every night (configurable), clusters recent episodic memories using Jaccard similarity, extracts candidate lessons from each cluster, and stages them for human review.

**There are no LLM calls in the Dream Cycle.** It is entirely mechanical and deterministic.

---

## Overview

```
03:00 cron trigger
    │
    ▼
1. Load unstaged episodic memories from last 24h
    │
    ▼
2. Tokenize each entry → word sets
    │
    ▼
3. Jaccard single-linkage clustering (threshold = 0.35)
    │
    ▼
4. For each cluster → extract common claim via n-gram frequency
    │
    ▼
5. Validate candidates (not blank, not exact duplicate)
    │
    ▼
6. Save valid candidates as Lesson (status = STAGED)
    │
    ▼
7. Apply sigmoid salience decay to all ACCEPTED lessons
    │
    ▼
8. Mark processed episodes as staged = true
    │
    ▼
9. Publish DREAM_COMPLETE event to WebSocket feed
```

---

## Clustering Algorithm

### Step 1 — Tokenization

Each episodic memory's `content` field is tokenized:

```java
Set<String> tokenize(String content) {
    return Arrays.stream(content.toLowerCase().split("\\W+"))
        .filter(w -> w.length() > 3)       // skip short words
        .collect(Collectors.toSet());
}
```

Words shorter than 4 characters are dropped to reduce noise (`the`, `a`, `is`, `on`, etc.).

### Step 2 — Jaccard Similarity

For two episodes A and B with token sets `T_A` and `T_B`:

```
Jaccard(A, B) = |T_A ∩ T_B| / |T_A ∪ T_B|
```

Range: `[0.0, 1.0]`  
- `0.0` = completely different content  
- `1.0` = identical token sets  
- Threshold: `0.35` (configurable)

### Step 3 — Single-Linkage Greedy Clustering

```
clusters = []

for each episode E:
    best_cluster = null
    best_score   = 0.0

    for each existing cluster C:
        // compare E against the representative of C (first member)
        score = jaccard(E, C.representative)
        if score > best_score:
            best_score   = score
            best_cluster = C

    if best_score >= 0.35:
        best_cluster.add(E)
    else:
        clusters.add(new Cluster(E))   // E becomes new cluster's representative
```

This is O(n × k) where n = episodes and k = clusters. Fast enough for typical session volumes (< 1000 episodes per night).

### Step 4 — Claim Extraction

For each cluster, the "claim" is extracted as the most information-dense sentence fragment using n-gram frequency:

```java
String extractClaim(List<EpisodicMemory> cluster) {
    // 1. Collect all content
    String combined = cluster.stream()
        .map(EpisodicMemory::getContent)
        .collect(joining(" "));

    // 2. Split into sentences
    String[] sentences = combined.split("[.!?]");

    // 3. Score each sentence by token frequency
    Map<String, Long> freq = tokenize(combined).stream()
        .collect(groupingBy(identity(), counting()));

    // 4. Pick sentence with highest average token frequency
    return Arrays.stream(sentences)
        .max(Comparator.comparingDouble(s ->
            tokenize(s).stream()
                .mapToLong(t -> freq.getOrDefault(t, 0L))
                .average().orElse(0)))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .orElse(cluster.get(0).getContent().substring(0, Math.min(200, ...)));
}
```

---

## Salience Decay

After staging candidates, the Dream Cycle applies sigmoid decay to all `ACCEPTED` lessons:

```java
double sigmoidDecay(double salience) {
    // Decay factor: ~2% per night, approaches 0 asymptotically
    double x = salience * 10 - 5;          // map [0,1] → [-5,5]
    double sigmoid = 1.0 / (1.0 + Math.exp(-x));
    return salience * (0.95 + 0.05 * sigmoid);
}
```

A lesson with `salience = 1.0`:
- After 30 nights: ~`0.85`
- After 90 nights: ~`0.65`
- After 180 nights: ~`0.45`
- After 365 nights: ~`0.25`

Lessons that fall below a useful threshold should be re-evaluated or re-graduated to reset their salience.

---

## Manual Trigger

The Dream Cycle can be triggered manually without waiting for 03:00:

```bash
POST /api/dream/run
```

This runs the same logic synchronously and returns:

```json
{
  "stagedCount": 3,
  "processedEpisodes": 12,
  "clustersFound": 4,
  "runAt": "2026-04-18T14:00:00"
}
```

Check the last run result:

```bash
GET /api/dream/last
```

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `agentbrain.dream.cron` | `0 0 3 * * *` | Cron expression for nightly run |
| Jaccard threshold | `0.35` (hardcoded) | Minimum similarity to join a cluster |
| Min token length | `4` chars (hardcoded) | Tokens shorter than this are discarded |

---

## Why No LLM?

**Speed and reliability.** LLM calls add latency, cost, and a network dependency. The Dream Cycle runs at 03:00 in the background — it should always complete, even with no internet.

**Determinism.** The same input always produces the same clusters. Easy to debug, reason about, and trust.

**Sufficient quality.** The goal is to surface *candidate* lessons for human review, not to produce perfect summaries. A human always makes the final decision via the Lesson Review Board.

If you want richer summaries, you can always edit the candidate claim before graduating it.
