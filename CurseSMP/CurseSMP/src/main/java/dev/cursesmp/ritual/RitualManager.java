package dev.cursesmp.ritual;

import dev.cursesmp.CurseSMP;
import dev.cursesmp.Fx;
import dev.cursesmp.Items;
import dev.cursesmp.curse.Curse;
import dev.cursesmp.curse.Curses;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reroll Ritual: protect your reroll for N seconds. Dying (or quitting) loses your curse. */
public final class RitualManager {
    private static final class Ritual {
        UUID owner;
        String name;
        int total;
        int remaining;
        BossBar bar;
        ItemDisplay altar;
        BukkitTask task;
    }

    private final CurseSMP plugin;
    private final Map<UUID, Ritual> active = new HashMap<>();

    public RitualManager(CurseSMP plugin) {
        this.plugin = plugin;
    }

    public boolean isActive(UUID id) {
        return active.containsKey(id);
    }

    public boolean start(Player p) {
        if (active.containsKey(p.getUniqueId())) {
            Fx.msg(p, "You already have a ritual running!", NamedTextColor.RED);
            return false;
        }
        if (plugin.data().getCurse(p) == Curses.DRAGON) {
            Fx.msg(p, "The Curse of The Dragon cannot be rerolled.", NamedTextColor.RED);
            return false;
        }
        Items.consumeOne(p);

        Ritual r = new Ritual();
        r.owner = p.getUniqueId();
        r.name = p.getName();
        r.total = plugin.getConfig().getInt("ritual.seconds", 300);
        r.remaining = r.total;
        r.bar = BossBar.bossBar(title(r), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        for (Player o : Bukkit.getOnlinePlayers()) o.showBossBar(r.bar);
        r.altar = p.getWorld().spawn(p.getLocation().add(0, 2.2, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.NETHER_STAR));
            d.setBillboard(Display.Billboard.CENTER);
            d.setGlowing(true);
            d.setPersistent(false);
        });
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, r.total * 20 + 40, 0, false, false, true));
        Bukkit.getServer().broadcast(Component.text(p.getName() + " has started a Reroll Ritual! Stop them before it ends!",
                NamedTextColor.LIGHT_PURPLE));
        Fx.sound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.5f, 1f);
        r.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(r), 20L, 20L);
        active.put(r.owner, r);
        return true;
    }

    private Component title(Ritual r) {
        return Component.text("Reroll Ritual: " + r.name + " - " + Fx.time(r.remaining * 1000L), NamedTextColor.LIGHT_PURPLE);
    }

    private void tick(Ritual r) {
        Player p = Bukkit.getPlayer(r.owner);
        if (p == null || !p.isOnline()) return; // quit is handled in onQuit
        r.remaining--;
        r.bar.name(title(r));
        r.bar.progress(Math.max(0f, Math.min(1f, (float) r.remaining / r.total)));
        if (!p.hasPotionEffect(PotionEffectType.GLOWING)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, r.remaining * 20 + 40, 0, false, false, true));
        }
        if (r.altar != null && r.altar.isValid()) {
            Fx.particle(r.altar.getLocation(), Particle.ENCHANT, 15, 0.8, 0.5);
        }
        if (r.remaining <= 0) complete(r, p);
    }

    private void cleanup(Ritual r) {
        active.remove(r.owner);
        if (r.task != null) r.task.cancel();
        if (r.altar != null && r.altar.isValid()) r.altar.remove();
        for (Player o : Bukkit.getOnlinePlayers()) o.hideBossBar(r.bar);
        Player p = Bukkit.getPlayer(r.owner);
        if (p != null) p.removePotionEffect(PotionEffectType.GLOWING);
    }

    private void complete(Ritual r, Player p) {
        cleanup(r);
        Curse old = plugin.data().getCurse(p);
        Curse fresh = Curses.roll(old);
        plugin.data().equip(p, fresh);
        Fx.sound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
        Fx.particle(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 60, 0.6, 0.4);
        Bukkit.getServer().broadcast(Component.text(p.getName() + " completed the Reroll Ritual and received the ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(fresh.displayName, fresh.color)).append(Component.text("!", NamedTextColor.LIGHT_PURPLE)));
    }

    private void fail(Ritual r, Player p, String why) {
        cleanup(r);
        plugin.data().loseCurse(p);
        Bukkit.getServer().broadcast(Component.text(p.getName() + "'s Reroll Ritual failed (" + why + ") and their curse was lost!",
                NamedTextColor.DARK_RED));
    }

    public void onDeath(Player p) {
        Ritual r = active.get(p.getUniqueId());
        if (r != null) fail(r, p, "died");
    }

    public void onQuit(Player p) {
        Ritual r = active.get(p.getUniqueId());
        if (r == null) return;
        if (plugin.getConfig().getBoolean("ritual.quit-penalty", true)) fail(r, p, "left the server");
        else cleanup(r);
    }

    public void onJoin(Player p) {
        for (Ritual r : active.values()) p.showBossBar(r.bar);
    }

    public void shutdown() {
        for (Ritual r : new ArrayList<>(active.values())) cleanup(r);
    }
}
