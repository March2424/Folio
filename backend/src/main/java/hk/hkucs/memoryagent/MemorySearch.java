package hk.hkucs.memoryagent;

import static hk.hkucs.memoryagent.Models.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

/** Bounded lexical retrieval, with Chinese bigrams. Age only adjusts ranking; never erases content. */
@Service
public class MemorySearch {
    private final MemoryStore store;
    public MemorySearch(MemoryStore store) { this.store = store; }
    public List<Memory> search(String query, String from, String to, String kind, int limit) {
        if (query.length() > 2000) throw new IllegalArgumentException("query too long");
        if (from != null) LocalDate.parse(from);
        if (to != null) LocalDate.parse(to);
        if (from != null && to != null && from.compareTo(to) > 0) throw new IllegalArgumentException("date range");
        String q = query.toLowerCase(Locale.ROOT).strip();
        Set<String> terms = terms(q);
        record Hit(Memory memory, double score) {}
        return store.all().stream()
            .filter(m -> from == null || (m.occurredOn() != null && m.occurredOn().compareTo(from) >= 0))
            .filter(m -> to == null || (m.occurredOn() != null && m.occurredOn().compareTo(to) <= 0))
            .filter(m -> kind == null || kind.equals(m.kind()))
            .map(m -> {
                String title = m.title().toLowerCase(Locale.ROOT);
                String text = (m.summary() + "\n" + m.content()).toLowerCase(Locale.ROOT);
                double relevance = q.isEmpty() ? 1 : (title.contains(q) ? 20 : 0) + (text.contains(q) ? 8 : 0);
                for (String t : terms) relevance += (title.contains(t) ? 4 : 0) + (text.contains(t) ? 1 : 0);
                if (relevance == 0) return new Hit(m, 0);
                long last = Math.max(m.createdAt(), m.recalledAt());
                double ageDays = Math.max(0, System.currentTimeMillis() - last) / 86400000.0;
                return new Hit(m, relevance + (m.pinned() ? 0.7 : 0) + 0.3 / (1 + ageDays / 90));
            })
            .filter(h -> h.score() > 0)
            .sorted(Comparator.comparingDouble(Hit::score).reversed().thenComparing(h -> h.memory().id()))
            .limit(Math.max(1, Math.min(limit, 8))).map(Hit::memory).toList();
    }
    private Set<String> terms(String q) {
        Set<String> terms = new LinkedHashSet<>();
        for (String word : q.split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty()) continue;
            if (word.matches(".*[\\p{IsHan}].*")) {
                if (word.length() == 1) terms.add(word);
                else for (int i = 0; i < word.length() - 1; i++) terms.add(word.substring(i, i + 2));
            } else if (word.length() >= 2) terms.add(word);
        }
        return terms;
    }
}
