package dev.cursesmp.curse;

import dev.cursesmp.Combat;
import dev.cursesmp.Fx;
import dev.cursesmp.Powers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class CurseOfTheReaper extends Curse {
    private static final Color DARK = Color.fromRGB(35, 35, 45);
    private static final Color SOUL = Color.fromRGB(120, 60, 180);

    public CurseOfTheReaper() {
        super("reaper", "Curse of The Reaper", NamedTextColor.DARK_GRAY, Material.NETHERITE_SCRAP, DARK,
                "Every 5 hit combo removes 5 seconds from each effect on the target.",
                new String[]{"Reaper Skeletons", "Soul Slash", "Reaper Chain"}, new int[]{90, 60, 90});
    }

    @Override
    public boolean usesCombo() {
        return true;
    }

    @Override
    public void onCombo(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        List<PotionEffect> effects = new ArrayList<>(victim.getActivePotionEffects());
        boolean changed = false;
        for (PotionEffect pe : effects) {
            if (pe.getDuration() < 0) continue; // infinite
            int left = pe.getDuration() - 100;
            victim.removePotionEffect(pe.getType());
            if (left > 0) {
                victim.addPotionEffect(new PotionEffect(pe.getType(), left, pe.getAmplifier(),
                        pe.isAmbient(), pe.hasParticles(), pe.hasIcon()));
            }
            changed = true;
        }
        if (changed) {
            Location l = victim.getLocation().add(0, 1, 0);
            Fx.dust(l, SOUL, 1.5f, 25, 0.4);
            Fx.particle(l, Particle.SOUL, 8, 0.3, 0.05);
            Fx.sound(l, Sound.PARTICLE_SOUL_ESCAPE, 1f, 1f);
        }
    }

    @Override
    public boolean cast(Player p, int slot) {
        return switch (slot) {
            case 1 -> summon(p);
            case 2 -> slash(p);
            case 3 -> chain(p);
            default -> false;
        };
    }

    private boolean summon(Player p) {
        World w = p.getWorld();
        List<Mob> skeletons = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5;
            Location l = p.getLocation().add(Math.cos(a) * 2, 0.2, Math.sin(a) * 2);
            WitherSkeleton ws = w.spawn(l, WitherSkeleton.class, s -> {
                s.customName(Component.text("Reaper Skeleton", NamedTextColor.DARK_GRAY));
                s.setCustomNameVisible(true);
                AttributeInstance hp = s.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(6.0); // 3 hearts
                s.setHealth(6.0);
                if (s.getEquipment() != null) {
                    s.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                    s.getEquipment().setItemInMainHandDropChance(0f);
                }
                s.setRemoveWhenFarAway(false);
                s.setPersistent(false);
                Combat.tagMinion(s, p.getUniqueId());
            });
            skeletons.add(ws);
            Fx.particle(l.clone().add(0, 1, 0), Particle.SOUL_FIRE_FLAME, 15, 0.3, 0.05);
        }
        Fx.sound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.5f);
        int seconds = plugin().getConfig().getInt("reaper.skeleton-seconds", 30);
        Combat.runMinions(p, skeletons, seconds * 20, 30);
        return true;
    }

    private boolean slash(Player p) {
        Vector flat = p.getEyeLocation().getDirection().setY(0);
        if (flat.lengthSquared() < 0.01) flat = new Vector(1, 0, 0);
        flat.normalize();
        Location origin = p.getLocation().add(0, 1.1, 0);
        Fx.sound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
        Fx.sound(origin, Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.8f);
        for (double ang = -60; ang <= 60; ang += 6) {
            for (double r = 2; r <= 6; r += 1) {
                Vector v = flat.clone().rotateAroundY(Math.toRadians(ang)).multiply(r);
                Location l = origin.clone().add(v);
                Fx.dust(l, ((int) r) % 2 == 0 ? DARK : SOUL, 1.5f, 1, 0.05);
                if (r >= 6) Fx.particle(l, Particle.SWEEP_ATTACK, 1, 0, 0);
            }
        }
        for (LivingEntity le : p.getWorld().getNearbyLivingEntities(origin, 6.5)) {
            if (!Combat.isValidEnemy(p.getUniqueId(), le)) continue;
            Vector to = le.getLocation().toVector().subtract(p.getLocation().toVector());
            to.setY(0);
            if (to.lengthSquared() > 0.01 && to.normalize().dot(flat) < 0.5) continue;
            Combat.damage(p, le, 6.0); // 3 hearts
        }
        return true;
    }

    private boolean chain(Player p) {
        LivingEntity t = Combat.lookTarget(p, 20);
        if (t == null) {
            Fx.msg(p, "No target in sight.", NamedTextColor.RED);
            return false;
        }
        Fx.sound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.6f);
        if (!Combat.allow(p, t, true)) return true;
        double dist = t.getLocation().distance(p.getLocation());
        double perBlock = plugin().getConfig().getDouble("reaper.chain-damage-per-block", 0.4);
        double dmg = Math.min(8.0, dist * perBlock);
        Powers.pull(p, t, 2.5, 30, l -> Fx.dust(l, DARK, 1.2f, 1, 0.02), () -> {
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
            Combat.hurt(p, t, dmg);
            Fx.particle(t.getLocation().add(0, 1, 0), Particle.CRIT, 20, 0.3, 0.2);
        });
        return true;
    }
}
