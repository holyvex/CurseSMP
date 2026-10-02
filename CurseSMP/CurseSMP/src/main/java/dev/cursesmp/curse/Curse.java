package dev.cursesmp.curse;

import dev.cursesmp.CurseSMP;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public abstract class Curse {
    public final String id;
    public final String displayName;
    public final NamedTextColor color;
    public final Material icon;
    public final Color dust;
    public final String passive;
    public final String[] abilities;
    public final int[] defaultCd;

    protected Curse(String id, String displayName, NamedTextColor color, Material icon, Color dust,
                    String passive, String[] abilities, int[] defaultCd) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.dust = dust;
        this.passive = passive;
        this.abilities = abilities;
        this.defaultCd = defaultCd;
    }

    public boolean isMythic() {
        return false;
    }

    public int abilityCount() {
        return abilities.length;
    }

    public String cooldownKey(int slot) {
        return id + ".a" + slot;
    }

    /** Cast ability {@code slot} (1-based). Return false if nothing happened (no cooldown is applied). */
    public abstract boolean cast(Player p, int slot);

    /** Called every 5 ticks while the curse is equipped. */
    public void passiveTick(Player p) {
    }

    /** True if this curse uses the 5-hit combo counter. */
    public boolean usesCombo() {
        return false;
    }

    /** Called on every 5th consecutive hit (see usesCombo). The event can be modified (bonus damage). */
    public void onCombo(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
    }

    protected static CurseSMP plugin() {
        return CurseSMP.get();
    }
}
