package dev.cursesmp;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Reusable ability building blocks: projectiles, beams, slams, pulls, cages, flight, temp mace, ice zones. */
public final class Powers {
    private Powers() {}

    private static CurseSMP plugin;

    private static final Set<UUID> SLAMMING = new HashSet<>();
    private static final Map<UUID, Long> NO_FALL_UNTIL = new HashMap<>();
    private static final Map<UUID, FlightState> FLIGHTS = new HashMap<>();
    private static final Map<UUID, TempMace> TEMP = new HashMap<>();
    private static final List<IceZone> ZONES = new ArrayList<>();

    private record FlightState(boolean prevAllow, BukkitTask task, double dmg, double radius, Color color) {}
    private record TempMace(int slot, ItemStack old, BukkitTask task) {}
    public record IceZone(World world, Vector center, double radius, long endMs, UUID owner) {}

    static void init(CurseSMP p) {
        plugin = p;
    }

    public static void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            endTempMace(p, true);
            endFlight(p, false);
        }
    }

    // ---- projectile (visual item display, manual collision) ---------------------

    /**
     * @param onHit returns true if the entity counts as hit (projectile ends). Return false to fly through.
     */
    public static void shoot(Player owner, Location start, Vector dir, double speed, double range, double radius,
                             Material displayItem, float scale, Consumer<Location> trail,
                             Predicate<LivingEntity> onHit, Consumer<Location> onEnd) {
        World w = start.getWorld();
        Vector d = dir.clone().normalize();
        ItemDisplay disp = null;
        if (displayItem != null) {
            disp = w.spawn(start, ItemDisplay.class, ent -> {
                ent.setItemStack(new ItemStack(displayItem));
                ent.setBillboard(Display.Billboard.CENTER);
                ent.setPersistent(false);
                ent.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(scale, scale, scale), new AxisAngle4f()));
                ent.setTeleportDuration(1);
            });
        }
        final ItemDisplay display = disp;
        new BukkitRunnable() {
            final Location pos = start.clone();
            double travelled = 0;

            private void end(Location at) {
                cancel();
                if (display != null) display.remove();
                if (onEnd != null && at != null) onEnd.accept(at.clone());
            }

            @Override
            public void run() {
                if (!owner.isOnline()) {
                    end(null);
                    return;
                }
                RayTraceResult br = w.rayTraceBlocks(pos, d, speed, FluidCollisionMode.NEVER, true);
                if (br != null) {
                    Location hit = br.getHitPosition().toLocation(w);
                    if (trail != null) trail.accept(hit);
                    end(hit);
                    return;
                }
                Location mid = pos.clone().add(d.clone().multiply(speed / 2));
                pos.add(d.clone().multiply(speed));
                for (Location sample : new Location[]{mid, pos}) {
                    for (LivingEntity le : w.getNearbyLivingEntities(sample, radius)) {
                        if (le.equals(owner)) continue;
                        if (onHit.test(le)) {
                            end(sample);
                            return;
                        }
                    }
                }
                if (trail != null) trail.accept(pos);
                if (display != null) display.teleport(pos);
                travelled += speed;
                if (travelled >= range) end(pos);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- instant beam -------------------------------------------------------------

    /** Walks a line (stopped by blocks). onHit returns true when the entity counts as hit. */
    public static void beam(Player owner, Location start, Vector dir, double range, double radius,
                            Consumer<Location> trail, Predicate<LivingEntity> onHit, boolean pierce) {
        World w = start.getWorld();
        Vector d = dir.clone().normalize();
        RayTraceResult br = w.rayTraceBlocks(start, d, range, FluidCollisionMode.NEVER, true);
        double len = br == null ? range : start.distance(br.getHitPosition().toLocation(w));
        Set<UUID> seen = new HashSet<>();
        boolean done = false;
        for (double t = 0; t <= len && !done; t += 0.5) {
            Location p = start.clone().add(d.clone().multiply(t));
            if (trail != null) trail.accept(p);
            for (LivingEntity le : w.getNearbyLivingEntities(p, radius)) {
                if (le.equals(owner) || !seen.add(le.getUniqueId())) continue;
                if (onHit.test(le) && !pierce) {
                    done = true;
                    break;
                }
            }
        }
    }

    public static Location chestStart(Player p, Vector dir) {
        return p.getEyeLocation().add(dir.clone().multiply(0.8)).add(0, -0.3, 0);
    }

    /** A beam that follows the owner's aim for `seconds`, hitting everything in it once per second. */
    public static void holdBeam(Player owner, int seconds, double range, double radius, double damagePerSecond,
                                Consumer<Location> trail, Sound loop) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!owner.isOnline() || owner.isDead() || t >= seconds * 20) {
                    cancel();
                    return;
                }
                Vector d = owner.getEyeLocation().getDirection();
                Location start = chestStart(owner, d);
                final boolean dmg = t % 20 == 10;
                beam(owner, start, d, range, radius, trail, le -> {
                    if (!Combat.isValidEnemy(owner.getUniqueId(), le)) return false;
                    if (dmg) Combat.damage(owner, le, damagePerSecond);
                    return true;
                }, true);
                if (t % 20 == 0 && loop != null) Fx.sound(owner.getLocation(), loop, 1f, 1f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- slam (launch up / dive down, smash on landing) ----------------------------

    public static boolean hasFallImmunity(UUID id) {
        if (SLAMMING.contains(id)) return true;
        Long until = NO_FALL_UNTIL.get(id);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            NO_FALL_UNTIL.remove(id);
            return false;
        }
        return true;
    }

    /**
     * @param launchY upward velocity (0 = just fall)
     * @param sneakDive hold sneak to dive faster
     */
    public static void slam(Player p, double launchY, double damage, double radius, Color color, boolean sneakDive) {
        UUID id = p.getUniqueId();
        if (!SLAMMING.add(id)) return;
        if (launchY > 0) {
            p.setVelocity(new Vector(0, launchY, 0));
            Fx.sound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.2f, 0.8f);
        } else {
            p.setVelocity(new Vector(0, -1.8, 0));
        }
        new BukkitRunnable() {
            int t = 0;

            private void finish() {
                SLAMMING.remove(id);
                NO_FALL_UNTIL.put(id, System.currentTimeMillis() + 1500);
                cancel();
            }

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t > 200) {
                    finish();
                    return;
                }
                t++;
                if (t > 6 && (p.isOnGround() || p.isInWater())) {
                    smash(p, damage, radius, color);
                    finish();
                    return;
                }
                if (sneakDive && p.isSneaking() && t > 4) {
                    Vector v = p.getVelocity();
                    p.setVelocity(new Vector(v.getX() * 0.8, -1.6, v.getZ() * 0.8));
                }
                Fx.dust(p.getLocation().add(0, 0.5, 0), color, 1.4f, 4, 0.3);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void smash(Player p, double damage, double radius, Color color) {
        Location l = p.getLocation();
        Fx.sound(l, Sound.ITEM_MACE_SMASH_GROUND, 1.5f, 0.8f);
        Fx.sound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        Fx.particle(l, Particle.EXPLOSION_EMITTER, 1, 0, 0);
        for (double r = 1; r <= radius; r += 1) {
            Fx.ring(l.clone().add(0, 0.2, 0), r, (int) (r * 10), pt -> Fx.dust(pt, color, 1.6f, 1, 0.1));
        }
        for (LivingEntity le : l.getWorld().getNearbyLivingEntities(l, radius)) {
            if (le.getLocation().distanceSquared(l) > radius * radius) continue;
            if (!Combat.isValidEnemy(p.getUniqueId(), le)) continue;
            if (Combat.damage(p, le, damage)) {
                le.setVelocity(le.getVelocity().add(new Vector(0, 0.55, 0)));
            }
        }
    }

    // ---- pull -----------------------------------------------------------------------

    /** Drags target toward owner with a chain/vine visual; runs done when it arrives (or times out). */
    public static void pull(Player owner, LivingEntity target, double stopDistance, int maxTicks,
                            Consumer<Location> chain, Runnable done) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!owner.isOnline() || !target.isValid() || target.isDead()
                        || !owner.getWorld().equals(target.getWorld())) {
                    cancel();
                    return;
                }
                Location a = owner.getLocation().add(0, 1, 0);
                Location b = target.getLocation().add(0, 1, 0);
                double dist = a.distance(b);
                Fx.line(a, b, 0.5, chain);
                if (dist <= stopDistance || t++ > maxTicks) {
                    cancel();
                    if (done != null) done.run();
                    return;
                }
                Vector v = a.toVector().subtract(b.toVector()).normalize().multiply(Math.min(1.5, 0.45 + dist * 0.07));
                v.setY(Math.max(v.getY(), 0.12));
                target.setVelocity(v);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- cage visual -------------------------------------------------------------------

    public static void cage(Location center, double radius, double height, Color color, int ticks) {
        Location c = center.clone();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ticks) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    for (double y = 0; y <= height; y += 0.4) {
                        Fx.dust(c.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius), color, 1.2f, 1, 0);
                    }
                }
                for (double y : new double[]{0.1, height}) {
                    Fx.ring(c.clone().add(0, y, 0), radius, 16, p -> Fx.dust(p, color, 1.2f, 1, 0));
                }
                t += 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    // ---- flight (Dragon) ----------------------------------------------------------------

    public static void startFlight(Player p, int ticks, double slamDamage, double slamRadius, Color color) {
        endFlight(p, false);
        boolean prev = p.getAllowFlight();
        p.setAllowFlight(true);
        p.setVelocity(new Vector(0, 0.5, 0));
        p.setFlying(true);
        BukkitTask task = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    endFlight(p, false);
                    return;
                }
                Fx.particle(p.getLocation().add(0, 0.8, 0), Particle.REVERSE_PORTAL, 6, 0.4, 0.05);
                t += 2;
                if (t >= ticks) endFlight(p, true);
            }
        }.runTaskTimer(plugin, 0L, 2L);
        FLIGHTS.put(p.getUniqueId(), new FlightState(prev, task, slamDamage, slamRadius, color));
    }

    public static void endFlight(Player p, boolean slamIfAirborne) {
        FlightState st = FLIGHTS.remove(p.getUniqueId());
        if (st == null) return;
        st.task().cancel();
        GameMode gm = p.getGameMode();
        if (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) {
            p.setFlying(false);
            p.setAllowFlight(st.prevAllow());
        }
        if (slamIfAirborne && p.isOnline() && !p.isDead() && !p.isOnGround()) {
            slam(p, 0, st.dmg(), st.radius(), st.color(), false);
        }
    }

    // ---- temporary mace (Angel ability 3) ----------------------------------------------

    public static void giveTempMace(Player p, int ticks) {
        endTempMace(p, true);
        PlayerInventory inv = p.getInventory();
        int slot = inv.getHeldItemSlot();
        ItemStack old = inv.getItem(slot);
        inv.setItem(slot, Items.tempMace());
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> endTempMace(p, true), ticks);
        TEMP.put(p.getUniqueId(), new TempMace(slot, old == null ? null : old.clone(), task));
    }

    public static void endTempMace(Player p, boolean restore) {
        TempMace t = TEMP.remove(p.getUniqueId());
        if (t == null) return;
        t.task().cancel();
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (Items.is(inv.getItem(i), Items.TEMP_MACE)) inv.setItem(i, null);
        }
        if (Items.is(p.getItemOnCursor(), Items.TEMP_MACE)) p.setItemOnCursor(null);
        if (restore && t.old() != null && !t.old().getType().isAir()) {
            ItemStack cur = inv.getItem(t.slot());
            if (cur == null || cur.getType().isAir()) {
                inv.setItem(t.slot(), t.old());
            } else {
                inv.addItem(t.old()).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
            }
        }
    }

    /** Called from the death listener: never duplicate or leak the temp mace / replaced item. */
    public static void tempMaceOnDeath(Player p, List<ItemStack> drops, boolean keepInventory) {
        TempMace t = TEMP.get(p.getUniqueId());
        if (t == null) return;
        drops.removeIf(i -> Items.is(i, Items.TEMP_MACE));
        if (keepInventory) {
            endTempMace(p, true);
        } else {
            TEMP.remove(p.getUniqueId());
            t.task().cancel();
            if (t.old() != null && !t.old().getType().isAir()) drops.add(t.old());
        }
    }

    // ---- ice zones (Freezing ability 3) ---------------------------------------------------

    public static void addZone(IceZone z) {
        ZONES.add(z);
    }

    public static IceZone zoneAt(Location l) {
        long now = System.currentTimeMillis();
        ZONES.removeIf(z -> z.endMs() < now);
        for (IceZone z : ZONES) {
            if (z.world().equals(l.getWorld()) && z.center().distanceSquared(l.toVector()) <= z.radius() * z.radius()) {
                return z;
            }
        }
        return null;
    }
}
