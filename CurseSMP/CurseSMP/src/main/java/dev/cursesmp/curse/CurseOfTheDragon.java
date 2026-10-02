package dev.cursesmp.curse;

import dev.cursesmp.Combat;
import dev.cursesmp.Fx;
import dev.cursesmp.Powers;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Mythic curse: granted while holding the Dragon Egg, needs no cursed energy, immune to debuffs. */
public final class CurseOfTheDragon extends Curse {
    private static final Color PURPLE = Color.fromRGB(150, 40, 220);

    public CurseOfTheDragon() {
        super("dragon", "Curse of The Dragon", NamedTextColor.LIGHT_PURPLE, Material.DRAGON_EGG, PURPLE,
                "Cannot be affected by any debuffs.",
                new String[]{"Dragon Breath", "Ender Guard", "Dragon Flight", "Dragon Dash"},
                new int[]{105, 150, 180, 5});
    }

    @Override
    public boolean isMythic() {
        return true;
    }

    @Override
    public void passiveTick(Player p) {
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
            if (Combat.isDebuff(e.getType())) p.removePotionEffect(e.getType());
        }
        Combat.clearStun(p);
    }

    @Override
    public boolean cast(Player p, int slot) {
        return switch (slot) {
            case 1 -> breath(p);
            case 2 -> guard(p);
            case 3 -> flight(p);
            case 4 -> dash(p);
            default -> false;
        };
    }

    private boolean breath(Player p) {
        Fx.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.2f);
        Powers.holdBeam(p, 3, 25, 1.0, 2.0, l -> { // 1 heart per second for 3 seconds
            Fx.dust(l, PURPLE, 1.6f, 1, 0.05);
            if (ThreadLocalRandom.current().nextDouble() < 0.2) Fx.particle(l, Particle.PORTAL, 2, 0.1, 0.1);
        }, Sound.ENTITY_ENDER_DRAGON_FLAP);
        return true;
    }

    private boolean guard(Player p) {
        World w = p.getWorld();
        List<Mob> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5;
            Location l = p.getLocation().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
            Enderman en = w.spawn(l, Enderman.class, s -> {
                s.setPersistent(false);
                s.setRemoveWhenFarAway(false);
                Combat.tagMinion(s, p.getUniqueId());
            });
            list.add(en);
            Fx.particle(l.clone().add(0, 1, 0), Particle.PORTAL, 30, 0.4, 0.5);
        }
        Fx.sound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.6f);
        Combat.runMinions(p, list, 200, 25); // despawn after 10 seconds, attack whoever hits the owner
        return true;
    }

    private boolean flight(Player p) {
        Fx.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.8f);
        Powers.startFlight(p, 300, 8.0, 6.0, PURPLE); // 15 s, then a 4 heart slam if still airborne
        return true;
    }

    private boolean dash(Player p) {
        if (Combat.isStunned(p)) return false;
        World w = p.getWorld();
        Location from = p.getLocation();
        Vector d = p.getEyeLocation().getDirection().normalize();
        double max = 5.0;
        for (double h : new double[]{0.3, 1.5}) {
            Location o = from.clone().add(0, h, 0);
            RayTraceResult r = w.rayTraceBlocks(o, d, 5.0, FluidCollisionMode.NEVER, true);
            if (r != null) max = Math.min(max, Math.max(0, o.distance(r.getHitPosition().toLocation(w)) - 0.7));
        }
        Location dest = null;
        for (double dist = max; dist >= 1.0; dist -= 0.5) {
            Location c = from.clone().add(d.clone().multiply(dist));
            if (c.getBlock().isPassable() && c.clone().add(0, 1, 0).getBlock().isPassable()) {
                dest = c;
                break;
            }
        }
        if (dest == null) {
            Fx.msg(p, "Something blocks your dash.", NamedTextColor.RED);
            return false;
        }
        dest.setYaw(from.getYaw());
        dest.setPitch(from.getPitch());
        Fx.line(from.clone().add(0, 1, 0), dest.clone().add(0, 1, 0), 0.4, l -> {
            Fx.dust(l, PURPLE, 1.4f, 2, 0.1);
            Fx.particle(l, Particle.REVERSE_PORTAL, 1, 0.1, 0.02);
        });
        p.teleport(dest);
        Fx.sound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 1.3f);
        return true;
    }
}
