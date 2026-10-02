package dev.cursesmp.mythic;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public abstract class Mythic {
    public final String id;
    public final String name;
    public final NamedTextColor color;
    public final String[] abilities;
    public final int[] defaultCd;

    protected Mythic(String id, String name, NamedTextColor color, String[] abilities, int[] defaultCd) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.abilities = abilities;
        this.defaultCd = defaultCd;
    }

    public String cooldownKey(int slot) {
        return id + ".a" + slot;
    }

    /** slot 1 = F (swap hands), slot 2 = Shift+F. Return false if nothing happened. */
    public abstract boolean cast(Player p, int slot);
}
