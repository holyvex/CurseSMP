package dev.cursesmp.event;

import dev.cursesmp.CurseSMP;
import dev.cursesmp.Fx;
import dev.cursesmp.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** /MythicEventStart: ShadowSlayerSwordEvent, SoulCrusherEvent, CursedShieldEvent. */
public final class MythicEvents {
    public static final List<String> NAMES = List.of("ShadowSlayerSwordEvent", "SoulCrusherEvent", "CursedShieldEvent");

    private final CurseSMP plugin;
    private final NamespacedKey assassinKey;
    private UUID assassinId;

    public MythicEvents(CurseSMP plugin) {
        this.plugin = plugin;
        this.assassinKey = new NamespacedKey(plugin, "assassin");
    }

    /** Returns an error message, or null on success. */
    public String start(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "shadowslayerswordevent" -> {
                announce("SHADOW SLAYER EVENT", "Shadow meteors are falling across the world!", NamedTextColor.DARK_PURPLE);
                startMeteors();
                return null;
            }
            case "soulcrusherevent" -> {
                announce("SOUL CRUSHER EVENT", "The Soul Crusher seeks a new master...", NamedTextColor.AQUA);
                startSoulCrusher();
                return null;
            }
            case "cursedshieldevent" -> {
                if (assassinId != null) {
                    Entity ex = Bukkit.getEntity(assassinId);
                    if (ex != null && ex.isValid()) return "The Cursed Assassin is already alive.";
                }
                announce("CURSED SHIELD EVENT", "The Cursed Assassin has appeared!", NamedTextColor.DARK_RED);
                startAssassin();
                return null;
            }
            default -> {
                return "Unknown event. Use: " + String.join(", ", NAMES);
            }
        }
    }

    private void announce(String title, String sub, NamedTextColor color) {
        Bukkit.getServer().broadcast(Component.text("[Mythic Event] ", NamedTextColor.GOLD)
                .append(Component.text(title + " - " + sub, color)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(Component.text(title, color), Component.text(sub, NamedTextColor.GRAY)));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.8f);
        }
    }

    private World overworld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) return w;
        }
        return Bukkit.getWorlds().get(0);
    }

    private Location randomSurface(World w, int min, int max) {
        Location sp = w.getSpawnLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 25; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = min + r.nextDouble() * Math.max(1, max - min);
            int x = sp.getBlockX() + (int) (Math.cos(ang) * dist);
            int z = sp.getBlockZ() + (int) (Math.sin(ang) * dist);
            int y = w.getHighestBlockYAt(x, z);
            if (w.getBlockAt(x, y, z).isLiquid()) continue;
            return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return sp;
    }

    // ---- Shadow Slayer: meteors -> shards -> (1%) handle ---------------------------

    private void startMeteors() {
        World w = overworld();
        int radius = plugin.getConfig().getInt("events.meteor-radius", 1200);
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            Bukkit.getScheduler().runTaskLater(plugin, () -> dropMeteor(w, radius, index), i * 160L);
        }
    }

    private void dropMeteor(World w, int radius, int index) {
        Location land = randomSurface(w, 100, radius);
        Vector from = new Vector(30, Math.min(w.getMaxHeight() - 2, land.getBlockY() + 120) - land.getY(), 30);
        Location startLoc = land.clone().add(from);
        Vector delta = land.toVector().subtract(startLoc.toVector());
        long ax = Math.round(land.getX() / 50.0) * 50;
        long az = Math.round(land.getZ() / 50.0) * 50;
        Bukkit.getServer().broadcast(Component.text("Meteor " + index + "/3 is crashing near X " + ax + ", Z " + az + "!",
                NamedTextColor.DARK_PURPLE));
        final int total = 120;
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                Location p = startLoc.clone().add(delta.clone().multiply(t / (double) total));
                Fx.dust(p, Color.fromRGB(70, 20, 120), 5f, 25, 1.2);
                Fx.particle(p, Particle.FLAME, 15, 0.8, 0.05);
                Fx.particle(p, Particle.LARGE_SMOKE, 10, 0.8, 0.05);
                if (t % 20 == 0) {
                    w.playSound(p, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 4f, 0.5f);
                }
                if (t >= total) {
                    cancel();
                    impact(land);
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void impact(Location l) {
        World w = l.getWorld();
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 3, 1, 1, 1, 0);
        Fx.dust(l, Color.fromRGB(70, 20, 120), 4f, 80, 2.5);
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 6f, 0.6f);
        int shards = plugin.getConfig().getInt("events.shards-per-meteor", 4);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < shards; i++) {
            Item it = w.dropItem(l.clone().add(r.nextDouble(-1.5, 1.5), 0.5, r.nextDouble(-1.5, 1.5)), Items.shadowShard(1));
            it.setGlowing(true);
            it.setUnlimitedLifetime(true);
            it.setInvulnerable(true);
            it.setVelocity(new Vector(r.nextDouble(-0.3, 0.3), 0.4, r.nextDouble(-0.3, 0.3)));
        }
    }

    /** Called when a player picks up shadow shards. */
    public void onShardPickup(Player p, int amount) {
        int glow = plugin.getConfig().getInt("events.shadow-glow-seconds", 600);
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glow * 20, 0, false, false, true));
        Bukkit.getServer().broadcast(Component.text(p.getName() + " picked up a Shadow Shard!", NamedTextColor.DARK_PURPLE));
        double chance = plugin.getConfig().getDouble("events.handle-chance", 0.01);
        for (int i = 0; i < amount; i++) {
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                giveHandle();
                break;
            }
        }
    }

    private void giveHandle() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return;
        Player lucky = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        lucky.getInventory().addItem(Items.shadowHandle()).values()
                .forEach(l -> lucky.getWorld().dropItemNaturally(lucky.getLocation(), l));
        Bukkit.getServer().broadcast(Component.text(lucky.getName() + " has obtained the Shadow Handle!", NamedTextColor.LIGHT_PURPLE));
        Fx.sound(lucky.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
    }

    // ---- Soul Crusher: random player, 5% ------------------------------------------

    private void startSoulCrusher() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);
        double chance = plugin.getConfig().getDouble("events.soul-crusher-chance", 0.05);
        for (Player p : players) {
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                p.getInventory().addItem(Items.soulCrusher()).values()
                        .forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
                Bukkit.getServer().broadcast(Component.text(p.getName() + " has been chosen by the Soul Crusher!", NamedTextColor.AQUA));
                Fx.sound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
                return;
            }
        }
        Bukkit.getServer().broadcast(Component.text("The Soul Crusher found no worthy owner this time.", NamedTextColor.GRAY));
    }

    // ---- Cursed Shield: Cursed Assassin gives coordinates -------------------------

    private void startAssassin() {
        World w = overworld();
        int radius = plugin.getConfig().getInt("events.assassin-radius", 1500);
        double hp = plugin.getConfig().getDouble("events.assassin-health", 150);
        Location l = randomSurface(w, 100, radius);
        WitherSkeleton a = w.spawn(l, WitherSkeleton.class, s -> {
            s.customName(Component.text("Cursed Assassin", NamedTextColor.DARK_RED));
            s.setCustomNameVisible(true);
            setAttr(s.getAttribute(Attribute.MAX_HEALTH), hp);
            s.setHealth(hp);
            setAttr(s.getAttribute(Attribute.ATTACK_DAMAGE), 10.0);
            setAttr(s.getAttribute(Attribute.MOVEMENT_SPEED), 0.34);
            s.setRemoveWhenFarAway(false);
            s.setPersistent(true);
            EntityEquipment eq = s.getEquipment();
            if (eq != null) {
                eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setItemInMainHandDropChance(0f);
                eq.setHelmetDropChance(0f);
                eq.setChestplateDropChance(0f);
            }
            s.getPersistentDataContainer().set(assassinKey, PersistentDataType.BYTE, (byte) 1);
        });
        assassinId = a.getUniqueId();
        long ax = Math.round(l.getX() / 100.0) * 100;
        long az = Math.round(l.getZ() / 100.0) * 100;
        Bukkit.getServer().broadcast(Component.text("The Cursed Assassin lurks near X " + ax + ", Z " + az
                + ". Slay him to learn where the Cursed Shield is hidden.", NamedTextColor.DARK_RED));
    }

    private void setAttr(AttributeInstance inst, double value) {
        if (inst != null) inst.setBaseValue(value);
    }

    /** Called for every entity death; handles the Cursed Assassin. */
    public void onEntityDeath(EntityDeathEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(assassinKey, PersistentDataType.BYTE)) return;
        assassinId = null;
        World w = e.getEntity().getWorld();
        Location chest = randomSurface(w, 300, plugin.getConfig().getInt("events.assassin-radius", 1500));
        Block b = chest.getBlock();
        b.setType(Material.CHEST);
        if (b.getState() instanceof Chest c) {
            c.getBlockInventory().addItem(Items.cursedShield());
        }
        e.getDrops().clear();
        e.getDrops().add(Items.shieldCoords(chest));
        Player killer = e.getEntity().getKiller();
        String who = killer != null ? killer.getName() : "Someone";
        Bukkit.getServer().broadcast(Component.text(who + " slew the Cursed Assassin and holds the coordinates to the Cursed Shield!",
                NamedTextColor.GOLD));
    }
}
