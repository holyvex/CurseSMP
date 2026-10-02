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
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CurseOfFreezing extends Curse {
    private static final Color ICE = Color.fromRGB(150, 220, 255);

    public CurseOfFreezing() {
        super("freezing", "Curse of Freezing", NamedTextColor.AQUA, Material.PRISMARINE_CRYSTALS, ICE,
                "Frost Walker: water freezes under your feet.",
                new String[]{"Ice Domain", "Ice Shards", "Ice Zone"}, new int[]{150, 60, 90});
    }

    /** Frost Walker passive (called when the player moves to a new block). */
    public void frostWalk(Player p) {
        if (!p.isOnGround()) return;
        Block base = p.getLocation().getBlock();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) continue;
                Block b = base.getRelative(dx, -1, dz);
                if (b.getType() != Material.WATER) continue;
                if (!(b.getBlockData() instanceof Levelled lv) || lv.getLevel() != 0) continue;
                if (!b.getRelative(0, 1, 0).getType().isAir()) continue;
                b.setType(Material.FROSTED_ICE);
                long delay = 60 + ThreadLocalRandom.current().nextInt(60);
                Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                    if (b.getType() == Material.FROSTED_ICE) b.setType(Material.WATER);
                }, delay);
            }
        }
    }

    @Override
    public boolean cast(Player p, int slot) {
        return switch (slot) {
            case 1 -> domain(p);
            case 2 -> shards(p);
            case 3 -> zone(p);
            default -> false;
        };
    }

    private boolean domain(Player p) {
        double radius = plugin().getConfig().getDouble("freezing.domain-radius", 7);
        Location c = p.getLocation().clone();
        int duration = 300; // 15 seconds
        Fx.sound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 2f, 0.6f);
        Fx.sound(c, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
        new BukkitRunnable() {
            int t = 0;
            final Set<UUID> hit = new HashSet<>();

            @Override
            public void run() {
                if (t >= duration || !p.isOnline()) {
                    cancel();
                    return;
                }
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 70; i++) {
                    double u = r.nextDouble() * Math.PI * 2;
                    double v = Math.acos(r.nextDouble());
                    Location l = c.clone().add(radius * Math.sin(v) * Math.cos(u), radius * Math.cos(v),
                            radius * Math.sin(v) * Math.sin(u));
                    if (i % 2 == 0) Fx.particle(l, Particle.SNOWFLAKE, 1, 0, 0);
                    else Fx.dust(l, ICE, 1.2f, 1, 0);
                }
                Fx.ring(c.clone().add(0, 0.2, 0), radius, 40, l -> Fx.dust(l, ICE, 1.3f, 1, 0));
                for (LivingEntity le : c.getWorld().getNearbyLivingEntities(c, radius)) {
                    if (le.getLocation().distanceSquared(c) > radius * radius) continue;
                    if (!Combat.isValidEnemy(p.getUniqueId(), le) || !hit.add(le.getUniqueId())) continue;
                    int remain = duration - t;
                    if (Combat.stun(p, le, remain)) le.setFreezeTicks(140);
                }
                t += 5;
            }
        }.runTaskTimer(plugin(), 0L, 5L);
        return true;
    }

    private boolean shards(Player p) {
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (!p.isOnline()) return;
                Vector d = p.getEyeLocation().getDirection();
                Location start = p.getEyeLocation().add(d.clone().multiply(1.0)).add(0, -0.3, 0);
                Fx.sound(start, Sound.ENTITY_SNOWBALL_THROW, 1.2f, 0.7f);
                Powers.shoot(p, start, d, 1.5, 35, 0.9, Material.BLUE_ICE, 0.7f,
                        l -> Fx.particle(l, Particle.SNOWFLAKE, 3, 0.1, 0.01),
                        le -> {
                            if (!Combat.isValidEnemy(p.getUniqueId(), le)) return false;
                            Combat.damage(p, le, 2.0); // 1 heart each
                            return true;
                        },
                        l -> {
                            Fx.particle(l, Particle.SNOWFLAKE, 25, 0.4, 0.05);
                            Fx.sound(l, Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
                        });
            }, i * 5L);
        }
        return true;
    }

    private boolean zone(Player p) {
        LivingEntity t = Combat.lookTarget(p, 30);
        if (t == null) {
            Fx.msg(p, "No target in sight.", NamedTextColor.RED);
            return false;
        }
        double radius = 5;
        int seconds = plugin().getConfig().getInt("freezing.zone-seconds", 20);
        Location c = t.getLocation().clone();
        Powers.addZone(new Powers.IceZone(c.getWorld(), c.toVector(), radius,
                System.currentTimeMillis() + seconds * 1000L, p.getUniqueId()));
        Fx.sound(c, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= seconds * 20) {
                    cancel();
                    return;
                }
                Fx.ring(c.clone().add(0, 0.2, 0), radius, 48, l -> {
                    Fx.particle(l, Particle.SNOWFLAKE, 1, 0, 0);
                    Fx.dust(l, ICE, 1.2f, 1, 0);
                });
                ticks += 10;
            }
        }.runTaskTimer(plugin(), 0L, 10L);
        return true;
    }
}
