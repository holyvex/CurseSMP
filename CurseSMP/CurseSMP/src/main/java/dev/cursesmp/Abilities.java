package dev.cursesmp;

import dev.cursesmp.curse.Curse;
import dev.cursesmp.mythic.Mythic;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Central place that checks energy / cooldowns before running a curse or mythic ability. */
public final class Abilities {
    private Abilities() {}

    public static void useCurse(Player p, int slot) {
        CurseSMP pl = CurseSMP.get();
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return;
        Curse c = pl.data().getCurse(p);
        if (c == null) {
            Fx.msg(p, "You don't have a curse.", NamedTextColor.RED);
            return;
        }
        if (slot < 1 || slot > c.abilityCount()) {
            Fx.msg(p, c.displayName + " has no ability " + slot + ".", NamedTextColor.RED);
            return;
        }
        if (!c.isMythic() && pl.data().getEnergy(p) < slot) {
            Fx.msg(p, "Ability " + slot + " is locked. You need " + slot + " Cursed Energy.", NamedTextColor.RED);
            return;
        }
        String key = c.cooldownKey(slot);
        long rem = pl.cooldowns().remaining(p.getUniqueId(), key);
        if (rem > 0) {
            Fx.msg(p, c.abilities[slot - 1] + " is on cooldown: " + Fx.time(rem), NamedTextColor.RED);
            return;
        }
        if (c.cast(p, slot)) {
            pl.cooldowns().set(p.getUniqueId(), key, pl.cooldowns().seconds(key, c.defaultCd[slot - 1]));
        }
    }

    public static void useMythic(Player p, Mythic m, int slot) {
        CurseSMP pl = CurseSMP.get();
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return;
        String key = m.cooldownKey(slot);
        long rem = pl.cooldowns().remaining(p.getUniqueId(), key);
        if (rem > 0) {
            Fx.msg(p, m.abilities[slot - 1] + " is on cooldown: " + Fx.time(rem), NamedTextColor.RED);
            return;
        }
        if (m.cast(p, slot)) {
            pl.cooldowns().set(p.getUniqueId(), key, pl.cooldowns().seconds(key, m.defaultCd[slot - 1]));
        }
    }
}
