package dev.cursesmp;

import dev.cursesmp.curse.Curses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Targeting, trust, Cursed Shield blocking, stun and damage rules shared by every ability. */
public final class Combat {
    private Combat() {}

    private static CurseSMP plugin;
    private static NamespacedKey minionKey;
    /** True while WE are dealing ability damage (so combo listeners ignore it). */
    public static boolean abilityDamage = false;

    private static final Map<UUID, Long> STUNNED = new HashMap<>();
    private record Hit(UUID attacker, long at) {}
    private static final Map<UUID, Hit> LAST_HIT = new HashMap<>();
    private static final Set<PotionEffectType> DEBUFFS = new HashSet<>();

    static void init(CurseSMP p) {
        plugin = p;
        minionKey = new NamespacedKey(p, "minion");
        DEBUFFS.addAll(List.of(
                PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.NAUSEA,
                PotionEffectType.BLINDNESS, PotionEffectType.HUNGER, PotionEffectType.WEAKNESS,
                PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.LEVITATION,
                PotionEffectType.DARKNESS));
    }

    public static boolean isDebuff(PotionEffectType t) {
        return DEBUFFS.contains(t);
    }

    // ---- minions --------------------------------------------------------------

    public static void tagMinion(Entity e, UUID owner) {
        e.getPersistentDataContainer().set(minionKey, PersistentDataType.STRING, owner.toString());
    }

    public static UUID minionOwner(Entity e) {
        String s = e.getPersistentDataContainer().get(minionKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ---- validity / trust -----------------------------------------------------

    /** Players and hostile mobs can be hit by abilities (never pets, villagers, armor stands...). */
    public static boolean isTargetable(Entity e) {
        if (e instanceof Player p) return p.getGameMode() != GameMode.SPECTATOR;
        return e instanceof Monster || e instanceof Slime || e instanceof Phantom;
    }

    /** Pure check (no side effects): may {@code ownerId}'s powers affect {@code e}? */
    public static boolean isValidEnemy(UUID ownerId, Entity e) {
        if (!(e instanceof LivingEntity le) || e instanceof ArmorStand) return false;
        if (le.isDead() || !le.isValid() || !isTargetable(e)) return false;
        if (e.getUniqueId().equals(ownerId)) return false;
        UUID mo = minionOwner(e);
        if (mo != null && (mo.equals(ownerId) || plugin.data().trusts(mo, ownerId))) return false;
        if (e instanceof Player p && plugin.data().trusts(p.getUniqueId(), ownerId)) return false;
        return true;
    }

    private static boolean debuffImmune(Player p) {
        return plugin.data().getCurse(p) == Curses.DRAGON;
    }

    /** Full check incl. Cursed Shield 25% block and Dragon debuff immunity. May have side effects (block fx). */
    public static boolean allow(Player attacker, LivingEntity victim, boolean debuff) {
        if (!isValidEnemy(attacker.getUniqueId(), victim)) return false;
        if (victim instanceof Player vp) {
            if (vp.getGameMode() == GameMode.CREATIVE) return false;
            if (shieldBlocks(vp, attacker)) return false;
            if (debuff && debuffImmune(vp)) return false;
        }
        return true;
    }

    private static boolean shieldBlocks(Player victim, Player attacker) {
        if (!victim.isBlocking()) return false;
        if (!Items.is(victim.getActiveItem(), Items.SHIELD)) return false;
        if (ThreadLocalRandom.current().nextDouble() >= 0.25) return false;
        Location l = victim.getLocation().add(0, 1, 0);
        Fx.sound(l, Sound.ITEM_SHIELD_BLOCK, 1f, 0.7f);
        Fx.particle(l, Particle.ENCHANT, 25, 0.5, 0.3);
        Component c = Component.text("The Cursed Shield blocked a cursed ability!", NamedTextColor.DARK_PURPLE);
        victim.sendActionBar(c);
        attacker.sendActionBar(c);
        return true;
    }

    // ---- damage ---------------------------------------------------------------

    public static boolean damage(Player attacker, LivingEntity victim, double amount) {
        if (!allow(attacker, victim, false)) return false;
        hurt(attacker, victim, amount);
        return true;
    }

    /** Unchecked damage (call allow() first). Honors damage.ignore-armor. */
    public static void hurt(Player attacker, LivingEntity victim, double amount) {
        if (victim.isDead()) return;
        if (!plugin.getConfig().getBoolean("damage.ignore-armor", true)) {
            normalDamage(attacker, victim, amount);
            return;
        }
        double abs = victim.getAbsorptionAmount();
        if (abs > 0) {
            double take = Math.min(abs, amount);
            victim.setAbsorptionAmount(abs - take);
            amount -= take;
        }
        if (amount <= 0) {
            victim.playHurtAnimation(0f);
            return;
        }
        if (victim.getHealth() - amount <= 0.0) {
            normalDamage(attacker, victim, 10000.0); // lethal: go through the real damage path for kill credit/totems
        } else {
            victim.setHealth(victim.getHealth() - amount);
            victim.playHurtAnimation(0f);
        }
    }

    private static void normalDamage(Player attacker, LivingEntity victim, double amount) {
        abilityDamage = true;
        try {
            victim.setNoDamageTicks(0);
            victim.damage(amount, attacker);
        } finally {
            abilityDamage = false;
        }
    }

    // ---- stun -----------------------------------------------------------------

    public static boolean stun(Player attacker, LivingEntity victim, int ticks) {
        if (!allow(attacker, victim, true)) return false;
        forceStun(victim, ticks);
        return true;
    }

    public static void forceStun(LivingEntity victim, int ticks) {
        if (victim instanceof Player p) {
            STUNNED.put(p.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        } else {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 9, false, false, false));
        }
    }

    public static boolean isStunned(Player p) {
        Long end = STUNNED.get(p.getUniqueId());
        if (end == null) return false;
        if (end < System.currentTimeMillis()) {
            STUNNED.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    public static void clearStun(Player p) {
        STUNNED.remove(p.getUniqueId());
    }

    // ---- targeting ------------------------------------------------------------

    /** The enemy the player is looking at (line of sight, up to range blocks), or null. */
    public static LivingEntity lookTarget(Player p, double range) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World w = p.getWorld();
        RayTraceResult r = w.rayTraceEntities(eye, dir, range, 0.8,
                ent -> !ent.equals(p) && isValidEnemy(p.getUniqueId(), ent));
        if (r == null || !(r.getHitEntity() instanceof LivingEntity le)) return null;
        double dist = eye.distance(le.getEyeLocation());
        RayTraceResult block = w.rayTraceBlocks(eye, dir, Math.max(0.5, dist - 0.3), FluidCollisionMode.NEVER, true);
        return block == null ? le : null;
    }

    public static void recordHit(UUID victim, UUID attacker) {
        LAST_HIT.put(victim, new Hit(attacker, System.currentTimeMillis()));
    }

    public static Player recentAttacker(UUID victim) {
        Hit h = LAST_HIT.get(victim);
        if (h == null || System.currentTimeMillis() - h.at() > 10_000) return null;
        return Bukkit.getPlayer(h.attacker());
    }

    /** Best target for minions: whoever hit the owner recently, otherwise the nearest enemy player. */
    public static LivingEntity pickTarget(Player owner, Location from, double range) {
        Player recent = recentAttacker(owner.getUniqueId());
        if (recent != null && recent.getWorld().equals(owner.getWorld())
                && isValidEnemy(owner.getUniqueId(), recent)
                && recent.getLocation().distanceSquared(owner.getLocation()) < range * range * 4) {
            return recent;
        }
        Player best = null;
        double bestD = range * range;
        for (Player pl : owner.getWorld().getPlayers()) {
            if (!isValidEnemy(owner.getUniqueId(), pl)) continue;
            double d = pl.getLocation().distanceSquared(from);
            if (d < bestD) {
                bestD = d;
                best = pl;
            }
        }
        return best;
    }

    /** Keeps summoned mobs on target, follows the owner, and removes them after lifeTicks. */
    public static void runMinions(Player owner, List<? extends Mob> mobs, int lifeTicks, double range) {
        List<Mob> list = new ArrayList<>(mobs);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= lifeTicks || !owner.isOnline()) {
                    for (Mob m : list) {
                        if (m.isValid()) {
                            Fx.particle(m.getLocation().add(0, 1, 0), Particle.POOF, 12, 0.3, 0.02);
                            m.remove();
                        }
                    }
                    cancel();
                    return;
                }
                if (t % 10 == 0) {
                    for (Mob m : list) {
                        if (!m.isValid()) continue;
                        LivingEntity cur = m.getTarget();
                        if (cur == null || cur.isDead() || !isValidEnemy(owner.getUniqueId(), cur)) {
                            LivingEntity best = pickTarget(owner, m.getLocation(), range);
                            m.setTarget(best);
                            if (best == null && m.getWorld().equals(owner.getWorld())
                                    && m.getLocation().distanceSquared(owner.getLocation()) > 144) {
                                m.getPathfinder().moveTo(owner.getLocation());
                            }
                        }
                    }
                }
                t += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}
