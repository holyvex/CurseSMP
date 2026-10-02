package dev.cursesmp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Cooldowns {
    private final CurseSMP plugin;
    private final Map<UUID, Map<String, Long>> map = new HashMap<>();

    public Cooldowns(CurseSMP plugin) {
        this.plugin = plugin;
    }

    public long remaining(UUID id, String key) {
        Map<String, Long> m = map.get(id);
        if (m == null) return 0;
        Long end = m.get(key);
        if (end == null) return 0;
        long r = end - System.currentTimeMillis();
        if (r <= 0) {
            m.remove(key);
            return 0;
        }
        return r;
    }

    public void set(UUID id, String key, int seconds) {
        map.computeIfAbsent(id, k -> new HashMap<>()).put(key, System.currentTimeMillis() + seconds * 1000L);
    }

    /** Cooldown length in seconds (config override: cooldowns.&lt;key&gt;). */
    public int seconds(String key, int def) {
        return plugin.getConfig().getInt("cooldowns." + key, def);
    }

    public void clear(UUID id) {
        map.remove(id);
    }
}
