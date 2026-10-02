package dev.cursesmp.mythic;

import dev.cursesmp.Combat;
import dev.cursesmp.Fx;
import dev.cursesmp.Items;
import dev.cursesmp.Powers;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class SoulCrusher extends Mythic {
    private static final Color SOUL = Color.fromRGB(0, 200, 210);

    public SoulCrusher() {
        super(Items.SOUL_CRUSHER, "Soul Crusher", NamedTextColor.AQUA,
                new String[]{"Skyfall Smash", "Repulse"}, new int[]{120, 30});
    }

    @Override
    public boolean cast(Player p, int slot) {
        if (slot == 1) {
            Powers.slam(p, 1.6, 6.0, 4.5, SOUL, true); // 3 hearts
            return true;
        }
        return repulse(p);
    }

    private boolean repulse(Player p) {
        Location c = p.getLocation().add(0, 1, 0);
        Fx.sound(c, Sound.ENTITY_BREEZE_WIND_BURST, 2f, 0.8f);
        Fx.particle(c, Particle.EXPLOSION_EMITTER, 1, 0, 0);
        for (double r = 1; r <= 4; r += 1) {
            Fx.ring(c, r, (int) (r * 10), l -> Fx.dust(l, SOUL, 1.5f, 1, 0.1));
        }
        for (LivingEntity le : p.getWorld().getNearbyLivingEntities(c, 4.5)) {
            if (!Combat.allow(p, le, false)) continue;
            Vector away = le.getLocation().toVector().subtract(p.getLocation().toVector());
            away.setY(0);
            if (away.lengthSquared() < 0.01) away = p.getEyeLocation().getDirection().setY(0);
            away.normalize().multiply(1.0).setY(0.42); // ~5 blocks
            le.setVelocity(away);
        }
        return true;
    }
}
