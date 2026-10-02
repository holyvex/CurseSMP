package dev.cursesmp.curse;

import dev.cursesmp.Combat;
import dev.cursesmp.Fx;
import dev.cursesmp.Powers;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public final class CurseOfBleeding extends Curse {
    private static final Color BLOOD = Color.fromRGB(170, 0, 0);

    public CurseOfBleeding() {
        super("bleeding", "Curse of Bleeding", NamedTextColor.DARK_RED, Material.GHAST_TEAR, BLOOD,
                "Land a 5 hit combo without taking damage for a chance at a 10% damage bonus hit.",
                new String[]{"Blood Cage", "Blood Drain", "Blood Blast"}, new int[]{45, 60, 105});
    }

    @Override
    public boolean usesCombo() {
        return true;
    }

    @Override
    public void onCombo(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        double chance = plugin().getConfig().getDouble("bleeding.bonus-chance", 0.30);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        event.setDamage(event.getDamage() * plugin().getConfig().getDouble("bleeding.bonus-multiplier", 1.10));
        Location l = victim.getLocation().add(0, 1, 0);
        Fx.dust(l, Color.RED, 1.6f, 30, 0.4);
        Fx.sound(l, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
    }

    @Override
    public boolean cast(Player p, int slot) {
        return switch (slot) {
            case 1 -> bloodCage(p);
            case 2 -> bloodDrain(p);
            case 3 -> bloodBlast(p);
            default -> false;
        };
    }

    private boolean bloodCage(Player p) {
        LivingEntity t = Combat.lookTarget(p, 20);
        if (t == null) {
            Fx.msg(p, "No target in sight.", NamedTextColor.RED);
            return false;
        }
        Fx.sound(t.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1.5f);
        if (Combat.stun(p, t, 100)) {
            Powers.cage(t.getLocation(), 1.1, 2.2, BLOOD, 100);
            Fx.sound(t.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.7f);
        }
        return true;
    }

    private boolean bloodDrain(Player p) {
        LivingEntity t = Combat.lookTarget(p, 20);
        if (t == null) {
            Fx.msg(p, "No target in sight.", NamedTextColor.RED);
            return false;
        }
        Fx.sound(t.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 1.6f);
        if (!Combat.allow(p, t, false)) return true;
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t.isDead() || !t.isValid() || n >= 100) {
                    cancel();
                    return;
                }
                if (p.getWorld().equals(t.getWorld())) {
                    Fx.line(t.getLocation().add(0, 1, 0), p.getLocation().add(0, 1, 0), 0.6,
                            l -> Fx.dust(l, Color.RED, 1f, 1, 0));
                }
                if (n % 10 == 9) {
                    Combat.hurt(p, t, 1.0); // 1 HP per second for 10 seconds
                    Fx.dust(t.getLocation().add(0, 1, 0), Color.RED, 1.4f, 12, 0.3);
                }
                n++;
            }
        }.runTaskTimer(plugin(), 0L, 2L);
        return true;
    }

    private boolean bloodBlast(Player p) {
        Vector d = p.getEyeLocation().getDirection();
        Location start = Powers.chestStart(p, d);
        Fx.sound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 1.1f);
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            if (!p.isOnline()) return;
            Vector dir = p.getEyeLocation().getDirection();
            Location s = Powers.chestStart(p, dir);
            Fx.sound(s, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.0f);
            Powers.beam(p, s, dir, 20, 1.0, l -> {
                Fx.dust(l, Color.RED, 1.8f, 1, 0.05);
                if (((int) (s.distance(l) * 2)) % 6 == 0) Fx.particle(l, Particle.SONIC_BOOM, 1, 0, 0);
            }, le -> {
                if (!Combat.isValidEnemy(p.getUniqueId(), le)) return false;
                Combat.damage(p, le, 8.0); // 4 hearts
                return true;
            }, false);
        }, 10L);
        Fx.dust(start, Color.RED, 2f, 20, 0.3);
        return true;
    }
}
