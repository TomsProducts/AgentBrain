package io.agentbrain.memory.service;

import io.agentbrain.memory.domain.EpisodicMemory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ClusteringService {

    private static final double JACCARD_THRESHOLD = 0.35;
    private static final int MIN_WORD_LENGTH = 3;

    public Map<Integer, List<EpisodicMemory>> cluster(List<EpisodicMemory> episodes) {
        Map<Integer, List<EpisodicMemory>> clusters = new LinkedHashMap<>();
        Map<Integer, Set<String>> clusterTokens = new LinkedHashMap<>();
        int nextClusterId = 0;

        for (EpisodicMemory episode : episodes) {
            Set<String> tokens = tokenize(episode.getContent());
            if (tokens.isEmpty()) {
                continue;
            }

            int bestCluster = -1;
            double bestScore = 0.0;

            for (Map.Entry<Integer, Set<String>> entry : clusterTokens.entrySet()) {
                double score = jaccard(tokens, entry.getValue());
                if (score > bestScore) {
                    bestScore = score;
                    bestCluster = entry.getKey();
                }
            }

            if (bestScore >= JACCARD_THRESHOLD) {
                clusters.get(bestCluster).add(episode);
                clusterTokens.get(bestCluster).addAll(tokens);
            } else {
                clusters.put(nextClusterId, new ArrayList<>(List.of(episode)));
                clusterTokens.put(nextClusterId, new HashSet<>(tokens));
                nextClusterId++;
            }
        }

        return clusters;
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> tokens = new HashSet<>();
        for (String word : text.toLowerCase().split("[\\s\\W]+")) {
            if (word.length() > MIN_WORD_LENGTH) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    public String extractClaim(List<EpisodicMemory> cluster) {
        Map<String, Integer> freq = new HashMap<>();
        for (EpisodicMemory ep : cluster) {
            for (String word : tokenize(ep.getContent())) {
                freq.merge(word, 1, Integer::sum);
            }
        }
        List<String> topWords = freq.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
        if (topWords.isEmpty()) {
            return cluster.get(0).getContent().substring(0, Math.min(200, cluster.get(0).getContent().length()));
        }
        return String.join(" ", topWords);
    }
}
