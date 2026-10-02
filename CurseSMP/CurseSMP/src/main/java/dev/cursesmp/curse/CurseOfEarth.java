package dev.cursesmp.curse;

import dev.cursesmp.Combat;
import dev.cursesmp.Fx;
import dev.cursesmp.Powers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Set;

public final class CurseOfEarth extends Curse {
    private static final Color GREEN = Color.fromRGB(60, 190, 60);
    private static final Set<Material> GROUND = EnumSet.of(Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.PODZOL, Material.MYCELIUM, Material.DIRT_PATH, Material.FARMLAND);

    public CurseOfEarth() {
        super("earth", "Curse of Earth", NamedTextColor.GREEN, Material.SLIME_BALL, GREEN,
                "Speed II while walking on grass and dirt.",
                new String[]{"Cage of Vines", "Vine Pull", "Vine Warden"}, new int[]{60, 85, 165});
    }

    @Override
    public void passiveTick(Player p) {
        Material under = p.getLocation().subtract(0, 0.1, 0).getBlock().getType();
        if (!GROUND.contains(under)) return;
        PotionEffect cur = p.getPotionEffect(PotionEffectType.SPEED);
        if (cur != null && (cur.getAmplifier() > 1 || (cur.getAmplifier() == 1 && cur.getDuration() > 30))) return;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, true, false, false));
    }

    @Override
    public boolean cast(Player p, int slot) {
        return switch (slot) {
            case 1 -> cage(p);
            case 2 -> pull(p);
            case 3 -> warden(p);
            default -> false;
        };
    }

    private boolean cage(Player p) {
        LivingEntity t = Combat.lookTarget(p, 20);
        if (t == null) {
            Fx.msg(p, "No target in sight.", NamedTextColor.RED);
            return false;
        }
        Fx.sound(t.getLocation(), Sound.BLOCK_GRASS_BREAK, 2f, 0.5f);
        if (Combat.stun(p, t, 100)) {
            Powers.cage(t.getLocation(), 1.1, 2.2, GREEN, 100);
            Fx.particle(t.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 25, 0.6, 0.1);
        }
        return true;
    }

    private boolean pull(Player p) {
        LivingEntity t = Combat.lookTarget(p, 25);
        if (t == null) {
            Fx.msg(p, "No target in sight.", NamedTextColor.RED);
            return false;
        }
        Fx.sound(p.getLocation(), Sound.BLOCK_GRASS_BREAK, 2f, 0.7f);
        if (!Combat.allow(p, t, true)) return true;
        Powers.pull(p, t, 2.5, 25, l -> Fx.dust(l, GREEN, 1.3f, 1, 0.03), () -> {
            if (t instanceof Player || t.isValid()) Combat.forceStun(t, 100);
            Powers.cage(t.getLocation(), 1.1, 2.2, GREEN, 100);
        });
        return true;
    }

    private boolean warden(Player p) {
        final LivingEntity target = Combat.lookTarget(p, 40);
        Vector flat = p.getEyeLocation().getDirection().setY(0);
        if (flat.lengthSquared() < 0.01) flat = new Vector(1, 0, 0);
        flat.normalize();
        final Vector facing = flat.clone();
        Location wl = p.getLocation().add(flat.multiply(3));
        wl.setDirection(facing);
        Warden w = p.getWorld().spawn(wl, Warden.class, s -> {
            s.setAI(false);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setPersistent(false);
            s.setRemoveWhenFarAway(true);
            s.customName(Component.text("Vine Warden", NamedTextColor.GREEN));
            s.setCustomNameVisible(true);
            Combat.tagMinion(s, p.getUniqueId());
        });
        Fx.sound(wl, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 1.0f);
        Fx.particle(wl.clone().add(0, 1, 0), Particle.HAPPY_VILLAGER, 40, 0.8, 0.1);
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            if (!w.isValid()) return;
            Location chest = w.getLocation().add(0, 1.9, 0);
            Vector d;
            if (target != null && target.isValid() && target.getWorld().equals(w.getWorld())) {
                d = target.getLocation().add(0, 1, 0).toVector().subtract(chest.toVector()).normalize();
            } else if (p.isOnline()) {
                d = p.getEyeLocation().getDirection();
            } else {
                d = facing;
            }
            Fx.sound(chest, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 1.0f);
            Powers.beam(p, chest, d, 40, 1.2, l -> {
                Fx.dust(l, GREEN, 2f, 1, 0.05);
                if (((int) (chest.distance(l) * 2)) % 6 == 0) Fx.particle(l, Particle.SONIC_BOOM, 1, 0, 0);
            }, le -> {
                if (!Combat.isValidEnemy(p.getUniqueId(), le)) return false;
                Combat.damage(p, le, 8.0); // 4 hearts
                return true;
            }, false);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (w.isValid()) {
                    Fx.particle(w.getLocation().add(0, 1, 0), Particle.POOF, 30, 0.6, 0.05);
                    w.remove();
                }
            }, 10L);
        }, 30L);
        // safety net: never leave a warden behind
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            if (w.isValid()) w.remove();
        }, 120L);
        return true;
    }
}
