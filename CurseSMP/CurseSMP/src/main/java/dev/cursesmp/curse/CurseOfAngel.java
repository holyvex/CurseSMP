package dev.cursesmp.curse;

import dev.cursesmp.Fx;
import dev.cursesmp.Powers;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class CurseOfAngel extends Curse {
    private static final Color GOLD = Color.fromRGB(255, 215, 80);

    public CurseOfAngel() {
        super("angel", "Curse of Angel", NamedTextColor.GOLD, Material.GLOWSTONE_DUST, GOLD,
                "Beneficial potion effects last longer.",
                new String[]{"Holy Beam", "Heaven's Smash", "Angel's Mace"}, new int[]{90, 120, 150});
    }

    @Override
    public boolean cast(Player p, int slot) {
        switch (slot) {
            case 1 -> {
                Fx.sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f);
                Powers.holdBeam(p, 8, 25, 0.8, 1.0, l -> { // 0.5 hearts per second
                    Fx.dust(l, GOLD, 1.3f, 1, 0.02);
                    Fx.dust(l, Color.WHITE, 1.0f, 1, 0.05);
                    if (ThreadLocalRandom.current().nextDouble() < 0.1) Fx.particle(l, Particle.END_ROD, 1, 0.05, 0);
                }, Sound.BLOCK_BEACON_AMBIENT);
                return true;
            }
            case 2 -> {
                Powers.slam(p, 1.7, 7.0, 4.5, GOLD, true); // 3.5 hearts, hold sneak to dive
                return true;
            }
            case 3 -> {
                Powers.giveTempMace(p, 200); // 10 seconds, each hit = 1 heart (handled in PowerListener)
                Fx.sound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.2f);
                Fx.dust(p.getLocation().add(0, 1, 0), GOLD, 1.5f, 30, 0.6);
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
