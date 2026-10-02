package dev.cursesmp.listener;

import dev.cursesmp.Abilities;
import dev.cursesmp.Combat;
import dev.cursesmp.CurseSMP;
import dev.cursesmp.Fx;
import dev.cursesmp.Items;
import dev.cursesmp.PlayerData;
import dev.cursesmp.Powers;
import dev.cursesmp.curse.Curse;
import dev.cursesmp.curse.Curses;
import dev.cursesmp.mythic.Mythic;
import dev.cursesmp.mythic.Mythics;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Combat, passives, death/energy rules, stun, ice zones, mythic ability triggers. */
public final class PowerListener implements Listener {
    private static final class Combo {
        int count;
        long last;
    }

    private static final Set<EntityPotionEffectEvent.Cause> ANGEL_CAUSES = EnumSet.of(
            EntityPotionEffectEvent.Cause.POTION_DRINK, EntityPotionEffectEvent.Cause.POTION_SPLASH,
            EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD, EntityPotionEffectEvent.Cause.ARROW,
            EntityPotionEffectEvent.Cause.FOOD);

    private final CurseSMP plugin;
    private final PlayerData data;
    private final Map<UUID, Combo> combos = new HashMap<>();

    public PowerListener(CurseSMP plugin) {
        this.plugin = plugin;
        this.data = plugin.data();
    }

    // ---- damage ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Entity damager = e.getDamager();

        // minions never hurt their owner / trusted players
        UUID minionOwner = Combat.minionOwner(damager);
        if (minionOwner != null && !Combat.isValidEnemy(minionOwner, victim)) {
            e.setCancelled(true);
            return;
        }

        // remember who hit a player (Dragon Endermen retaliate against this player)
        Player attacker = null;
        if (damager instanceof Player pl) attacker = pl;
        else if (damager instanceof Projectile pr && pr.getShooter() instanceof Player sp) attacker = sp;
        else if (minionOwner != null) attacker = Bukkit.getPlayer(minionOwner);
        if (victim instanceof Player vp && attacker != null && !attacker.equals(vp)) {
            Combat.recordHit(vp.getUniqueId(), attacker.getUniqueId());
        }

        if (!(damager instanceof Player p) || Combat.abilityDamage) return;
        ItemStack main = p.getInventory().getItemInMainHand();

        // mythic weapons + the Angel mace never hurt players who trust the wielder
        boolean mythicHeld = Mythics.fromItem(main) != null || Items.is(main, Items.TEMP_MACE);
        if (mythicHeld && !Combat.isValidEnemy(p.getUniqueId(), victim)) {
            e.setCancelled(true);
            return;
        }
        if (Items.is(main, Items.TEMP_MACE)) {
            e.setDamage(2.0); // every mace hit = 1 heart
            return;
        }

        Curse c = data.getCurse(p);
        if (c == null || !c.usesCombo() || !Combat.isValidEnemy(p.getUniqueId(), victim)) return;
        long timeout = plugin.getConfig().getInt("combo-timeout-seconds", 4) * 1000L;
        Combo cb = combos.computeIfAbsent(p.getUniqueId(), k -> new Combo());
        long now = System.currentTimeMillis();
        if (now - cb.last > timeout) cb.count = 0;
        cb.last = now;
        cb.count++;
        if (cb.count >= 5) {
            cb.count = 0;
            c.onCombo(p, victim, e);
        }
    }

    /** Taking damage breaks your combo. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getFinalDamage() > 0) combos.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p
                && Powers.hasFallImmunity(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    // ---- death / energy ---------------------------------------------------------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        data.setEnergy(victim, data.getEnergy(victim) - 1); // 1 death = -1 cursed energy
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            int en = data.getEnergy(killer);
            if (en < PlayerData.MAX_ENERGY) {
                data.setEnergy(killer, en + 1);
                Fx.msg(killer, "+1 Cursed Energy (" + (en + 1) + "/" + PlayerData.MAX_ENERGY + ")", NamedTextColor.LIGHT_PURPLE);
                if (en + 1 <= 3) Fx.msg(killer, "You unlocked ability " + (en + 1) + "!", NamedTextColor.LIGHT_PURPLE);
            }
        }
        Fx.msg(victim, "You lost 1 Cursed Energy.", NamedTextColor.GRAY);
        Powers.tempMaceOnDeath(victim, e.getDrops(), e.getKeepInventory());
        Powers.endFlight(victim, false);
        Combat.clearStun(victim);
        combos.remove(victim.getUniqueId());
        plugin.rituals().onDeath(victim);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (Combat.minionOwner(e.getEntity()) != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
        plugin.events().onEntityDeath(e);
    }

    // ---- join / quit ----------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Items.applyShieldHearts(e.getPlayer());
        plugin.rituals().onJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Powers.endTempMace(p, true);
        Powers.endFlight(p, false);
        Combat.clearStun(p);
        combos.remove(p.getUniqueId());
        plugin.rituals().onQuit(p);
    }

    // ---- movement: stun + Frost Walker --------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (Combat.isStunned(p)) {
            if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
                Location n = from.clone();
                n.setYaw(to.getYaw());
                n.setPitch(to.getPitch());
                if (to.getY() < from.getY()) n.setY(to.getY()); // still allow falling
                e.setTo(n);
            }
            return;
        }
        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            if (data.getCurse(p) == Curses.FREEZING) Curses.FREEZING.frostWalk(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!Combat.isStunned(e.getPlayer())) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || c == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            e.setCancelled(true);
        }
    }

    // ---- mythic weapons: F / Shift+F ---------------------------------------------------

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        Mythic m = Mythics.fromItem(p.getInventory().getItemInMainHand());
        if (m == null) return;
        e.setCancelled(true);
        Abilities.useMythic(p, m, p.isSneaking() ? 2 : 1);
    }

    // ---- potion passives (Angel longer effects, Dragon debuff immunity) -----------------

    @EventHandler(ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PotionEffect nw = e.getNewEffect();
        if (nw == null) return;
        Curse c = data.getCurse(p);
        if (c == Curses.DRAGON) {
            if (Combat.isDebuff(nw.getType())) e.setCancelled(true);
            return;
        }
        if (c != Curses.ANGEL) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.ADDED && e.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        if (!ANGEL_CAUSES.contains(e.getCause()) || Combat.isDebuff(nw.getType())) return;
        if (nw.getDuration() <= 0 || nw.getDuration() > 1_000_000) return;
        double mult = plugin.getConfig().getDouble("angel.potion-multiplier", 1.5);
        int dur = (int) Math.min(1_000_000, nw.getDuration() * mult);
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> p.addPotionEffect(
                new PotionEffect(nw.getType(), dur, nw.getAmplifier(), nw.isAmbient(), nw.hasParticles(), nw.hasIcon())));
    }

    // ---- Freezing ability 3: water placed in an ice zone freezes ----------------------

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (e.getBucket() != Material.WATER_BUCKET) return;
        Block b = e.getBlock();
        Powers.IceZone z = Powers.zoneAt(b.getLocation().add(0.5, 0.5, 0.5));
        if (z == null || z.owner().equals(e.getPlayer().getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (b.getType() != Material.WATER) return;
            b.setType(Material.ICE);
            Fx.particle(b.getLocation().add(0.5, 0.5, 0.5), Particle.SNOWFLAKE, 20, 0.4, 0.05);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (b.getType() == Material.ICE) b.setType(Material.AIR);
            }, 200L); // lasts 10 seconds
        }, 1L);
    }

    // ---- minions never target their owner or trusted players ---------------------------

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        UUID owner = Combat.minionOwner(e.getEntity());
        if (owner == null || e.getTarget() == null) return;
        if (!Combat.isValidEnemy(owner, e.getTarget())) e.setCancelled(true);
    }
}
