package io.leikvolle.tileindicators;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

/** Cached name matching, independent of NPC selection priority. */
final class NpcExclusions
{
    private static final int MAX_CACHED_NAMES = 2048;
    private String configuredNames;
    private List<String> patterns = Collections.emptyList();
    private final Map<String, Boolean> matches = new HashMap<>();

    void setNames(String names)
    {
        if (Objects.equals(configuredNames, names)) return;
        configuredNames = names;
        patterns = names == null || names.trim().isEmpty()
                ? Collections.emptyList() : Text.fromCSV(names);
        matches.clear();
    }

    boolean matches(String name)
    {
        if (name == null || patterns.isEmpty()) return false;
        Boolean cached = matches.get(name);
        if (cached != null) return cached;
        boolean excluded = false;
        for (String entry : patterns)
        {
            String pattern = entry.trim();
            if (!pattern.isEmpty() && WildcardMatcher.matches(pattern, name))
            {
                excluded = true;
                break;
            }
        }
        // Bound retained names across scene changes; matching never loads models.
        if (matches.size() >= MAX_CACHED_NAMES) matches.clear();
        matches.put(name, excluded);
        return excluded;
    }
}
