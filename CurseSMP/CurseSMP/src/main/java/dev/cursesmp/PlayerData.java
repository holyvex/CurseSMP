package dev.cursesmp;

import dev.cursesmp.curse.Curse;
import dev.cursesmp.curse.Curses;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Curse + energy live in the player's persistent data (saved with the player).
 * Trust links live in plugins/CurseSMP/trust.yml.
 */
public final class PlayerData {
    public static final int MAX_ENERGY = 3;

    private final CurseSMP plugin;
    private final NamespacedKey curseKey;
    private final NamespacedKey energyKey;
    private final NamespacedKey prevKey;
    /** owner -> players the owner trusts (those players deal 0 damage to the owner). */
    private final Map<UUID, Set<UUID>> trust = new HashMap<>();
    private final File trustFile;

    public PlayerData(CurseSMP plugin) {
        this.plugin = plugin;
        this.curseKey = new NamespacedKey(plugin, "curse");
        this.energyKey = new NamespacedKey(plugin, "energy");
        this.prevKey = new NamespacedKey(plugin, "prev_curse");
        this.trustFile = new File(plugin.getDataFolder(), "trust.yml");
        loadTrust();
    }

    // ---- curse ----------------------------------------------------------------

    public Curse getCurse(Player p) {
        return read(p.getPersistentDataContainer(), curseKey);
    }

    public Curse getPrev(Player p) {
        return read(p.getPersistentDataContainer(), prevKey);
    }

    private Curse read(PersistentDataContainer pdc, NamespacedKey key) {
        String id = pdc.get(key, PersistentDataType.STRING);
        return id == null ? null : Curses.byId(id);
    }

    private void write(Player p, NamespacedKey key, Curse c) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (c == null) pdc.remove(key);
        else pdc.set(key, PersistentDataType.STRING, c.id);
    }

    public boolean hasEgg(Player p) {
        return p.getInventory().contains(Material.DRAGON_EGG);
    }

    private boolean dragonActive(Player p) {
        return getCurse(p) == Curses.DRAGON && hasEgg(p);
    }

    /** Equip a normal curse. While the Dragon curse is active the curse is stored for when the egg is lost. */
    public void equip(Player p, Curse c) {
        if (dragonActive(p)) write(p, prevKey, c);
        else write(p, curseKey, c);
    }

    public void loseCurse(Player p) {
        if (dragonActive(p)) write(p, prevKey, null);
        else write(p, curseKey, null);
    }

    /** Holding the Dragon Egg grants the Dragon curse; losing it restores the previous curse. */
    public void syncDragon(Player p) {
        boolean egg = hasEgg(p);
        Curse cur = getCurse(p);
        if (egg && cur != Curses.DRAGON) {
            write(p, prevKey, cur);
            write(p, curseKey, Curses.DRAGON);
            Fx.msg(p, "The Dragon Egg awakens... you now wield the Curse of The Dragon!", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE);
        } else if (!egg && cur == Curses.DRAGON) {
            write(p, curseKey, getPrev(p));
            write(p, prevKey, null);
            Fx.msg(p, "You lost the Dragon Egg. The Curse of The Dragon fades.", net.kyori.adventure.text.format.NamedTextColor.GRAY);
        }
    }

    // ---- energy ---------------------------------------------------------------

    public int getEnergy(Player p) {
        return p.getPersistentDataContainer().getOrDefault(energyKey, PersistentDataType.INTEGER, 0);
    }

    public void setEnergy(Player p, int value) {
        int v = Math.max(0, Math.min(MAX_ENERGY, value));
        p.getPersistentDataContainer().set(energyKey, PersistentDataType.INTEGER, v);
    }

    // ---- trust ----------------------------------------------------------------

    public boolean addTrust(UUID owner, UUID other) {
        boolean changed = trust.computeIfAbsent(owner, k -> new HashSet<>()).add(other);
        if (changed) saveTrust();
        return changed;
    }

    /** Does {@code owner} trust {@code other}? (other deals 0 damage to owner) */
    public boolean trusts(UUID owner, UUID other) {
        Set<UUID> s = trust.get(owner);
        return s != null && s.contains(other);
    }

    /** Removes "a trusts b" and "b trusts a". Returns how many links were removed. */
    public int removeTrustBetween(UUID a, UUID b) {
        int n = 0;
        if (removeOne(a, b)) n++;
        if (removeOne(b, a)) n++;
        if (n > 0) saveTrust();
        return n;
    }

    private boolean removeOne(UUID owner, UUID other) {
        Set<UUID> s = trust.get(owner);
        return s != null && s.remove(other);
    }

    public void saveTrust() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> e : trust.entrySet()) {
            y.set(e.getKey().toString(), e.getValue().stream().map(UUID::toString).toList());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(trustFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save trust.yml: " + ex.getMessage());
        }
    }

    private void loadTrust() {
        if (!trustFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(trustFile);
        for (String key : y.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(key);
                Set<UUID> set = new HashSet<>();
                for (String s : y.getStringList(key)) set.add(UUID.fromString(s));
                trust.put(owner, set);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
