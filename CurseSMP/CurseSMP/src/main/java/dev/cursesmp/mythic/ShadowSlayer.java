package dev.cursesmp.mythic;

import dev.cursesmp.Combat;
import dev.cursesmp.CurseSMP;
import dev.cursesmp.Fx;
import dev.cursesmp.Items;
import dev.cursesmp.Powers;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class ShadowSlayer extends Mythic {
    private static final Color BLACK = Color.fromRGB(10, 10, 15);

    public ShadowSlayer() {
        super(Items.SHADOW_SLAYER, "Shadow Slayer", NamedTextColor.DARK_PURPLE,
                new String[]{"Wither Heads", "Shadow Beam"}, new int[]{60, 120});
    }

    @Override
    public boolean cast(Player p, int slot) {
        return slot == 1 ? witherHeads(p) : shadowBeam(p);
    }

    private boolean witherHeads(Player p) {
        for (int i = 0; i < 2; i++) {
            final double offset = i == 0 ? -0.08 : 0.08;
            Bukkit.getScheduler().runTaskLater(CurseSMP.get(), () -> {
                if (!p.isOnline()) return;
                Vector d = p.getEyeLocation().getDirection().rotateAroundY(offset);
                Location start = p.getEyeLocation().add(d.clone().multiply(1.0)).add(0, -0.3, 0);
                Fx.sound(start, Sound.ENTITY_WITHER_SHOOT, 1f, 1.3f);
                Powers.shoot(p, start, d, 1.3, 40, 0.9, Material.WITHER_SKELETON_SKULL, 0.8f,
                        l -> {
                            Fx.particle(l, Particle.SMOKE, 3, 0.1, 0.01);
                            Fx.particle(l, Particle.SOUL, 1, 0.05, 0.01);
                        },
                        le -> {
                            if (!Combat.isValidEnemy(p.getUniqueId(), le)) return false;
                            Combat.damage(p, le, 4.0); // 2 hearts each
                            return true;
                        },
                        l -> {
                            Fx.particle(l, Particle.EXPLOSION_EMITTER, 1, 0, 0);
                            Fx.dust(l, BLACK, 2f, 20, 0.5);
                            Fx.sound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                        });
            }, i * 6L);
        }
        return true;
    }

    private boolean shadowBeam(Player p) {
        Fx.sound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 0.8f);
        Bukkit.getScheduler().runTaskLater(CurseSMP.get(), () -> {
            if (!p.isOnline()) return;
            Vector d = p.getEyeLocation().getDirection();
            Location s = Powers.chestStart(p, d);
            Fx.sound(s, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.8f);
            Powers.beam(p, s, d, 30, 1.0, l -> {
                Fx.dust(l, BLACK, 2f, 1, 0.05);
                if (((int) (s.distance(l) * 2)) % 6 == 0) Fx.particle(l, Particle.SONIC_BOOM, 1, 0, 0);
            }, le -> {
                if (!Combat.isValidEnemy(p.getUniqueId(), le)) return false;
                Combat.damage(p, le, 8.0); // 4 hearts
                return true;
            }, false);
        }, 10L);
        return true;
    }
}
