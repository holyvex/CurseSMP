package dev.cursesmp;

import dev.cursesmp.curse.Curse;
import dev.cursesmp.mythic.Mythic;
import dev.cursesmp.mythic.Mythics;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Action-bar cooldown display for curses and mythic weapons. */
public final class Hud {
    private Hud() {}

    public static void show(Player p) {
        CurseSMP pl = CurseSMP.get();
        UUID id = p.getUniqueId();
        Mythic m = Mythics.fromItem(p.getInventory().getItemInMainHand());
        Component bar;
        if (m != null) {
            bar = Component.text(m.name + "  ", m.color);
            for (int i = 1; i <= m.abilities.length; i++) {
                String label = (i == 1 ? "[F] " : "[Shift+F] ") + m.abilities[i - 1];
                bar = bar.append(entry(pl, id, m.cooldownKey(i), label, false));
                if (i < m.abilities.length) bar = bar.append(Component.text("  |  ", NamedTextColor.DARK_GRAY));
            }
        } else {
            Curse c = pl.data().getCurse(p);
            if (c == null) return;
            int energy = pl.data().getEnergy(p);
            bar = Component.text(c.displayName + "  ", c.color);
            for (int i = 1; i <= c.abilityCount(); i++) {
                boolean locked = !c.isMythic() && energy < i;
                bar = bar.append(entry(pl, id, c.cooldownKey(i), "A" + i + " " + c.abilities[i - 1], locked));
                if (i < c.abilityCount()) bar = bar.append(Component.text("  |  ", NamedTextColor.DARK_GRAY));
            }
            if (!c.isMythic()) {
                bar = bar.append(Component.text("   " + "●".repeat(energy) + "○".repeat(PlayerData.MAX_ENERGY - energy),
                        NamedTextColor.LIGHT_PURPLE));
            }
        }
        p.sendActionBar(bar);
    }

    private static Component entry(CurseSMP pl, UUID id, String key, String label, boolean locked) {
        if (locked) return Component.text(label + " LOCKED", NamedTextColor.GRAY);
        long rem = pl.cooldowns().remaining(id, key);
        if (rem > 0) return Component.text(label + " " + Fx.time(rem), NamedTextColor.RED);
        return Component.text(label + " READY", NamedTextColor.GREEN);
    }
}
