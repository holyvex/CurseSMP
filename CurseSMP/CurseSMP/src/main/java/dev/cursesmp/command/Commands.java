package dev.cursesmp.command;

import dev.cursesmp.Abilities;
import dev.cursesmp.CurseSMP;
import dev.cursesmp.Fx;
import dev.cursesmp.Items;
import dev.cursesmp.PlayerData;
import dev.cursesmp.curse.Curse;
import dev.cursesmp.curse.Curses;
import dev.cursesmp.event.MythicEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Commands implements CommandExecutor, TabCompleter {
    private static final List<String> GIVE_ITEMS = List.of("bleeding", "reaper", "freezing", "earth", "angel",
            "energy", "reroll", "shard", "handle", "shadowslayer", "soulcrusher", "shield");

    private final CurseSMP plugin;

    public Commands(CurseSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "cursesmp" -> admin(s, a);
            case "mythiceventstart" -> {
                if (a.length < 1) {
                    s.sendMessage(Component.text("Usage: /mythiceventstart <" + String.join("|", MythicEvents.NAMES) + ">", NamedTextColor.RED));
                } else {
                    String err = plugin.events().start(a[0]);
                    if (err != null) s.sendMessage(Component.text(err, NamedTextColor.RED));
                }
            }
            case "cursetrust" -> trust(s, a);
            case "cursetrustremove" -> untrust(s, a);
            case "ability" -> {
                if (!(s instanceof Player p)) return true;
                if (a.length < 1) {
                    Fx.msg(p, "Usage: /ability <1-4>", NamedTextColor.RED);
                    return true;
                }
                try {
                    Abilities.useCurse(p, Integer.parseInt(a[0]));
                } catch (NumberFormatException ex) {
                    Fx.msg(p, "Usage: /ability <1-4>", NamedTextColor.RED);
                }
            }
            case "curseinfo" -> info(s);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---- /cursesmp ------------------------------------------------------------

    private void admin(CommandSender s, String[] a) {
        if (a.length == 0) {
            err(s, "Usage: /cursesmp <give|start|randomcurse|energy|reload>");
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(s, a);
            case "start" -> {
                for (Player p : Bukkit.getOnlinePlayers()) rollFor(p);
                Bukkit.getServer().broadcast(Component.text("The Cursed SMP has begun! Every player has been cursed.", NamedTextColor.DARK_PURPLE));
            }
            case "randomcurse" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) err(s, "Usage: /cursesmp randomcurse <player>");
                else rollFor(t);
            }
            case "energy" -> {
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) {
                    err(s, "Usage: /cursesmp energy <player> <0-3>");
                    return;
                }
                try {
                    plugin.data().setEnergy(t, Integer.parseInt(a[2]));
                    s.sendMessage(Component.text(t.getName() + " now has " + plugin.data().getEnergy(t) + " Cursed Energy.", NamedTextColor.GREEN));
                } catch (NumberFormatException ex) {
                    err(s, "Energy must be a number from 0 to 3.");
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                s.sendMessage(Component.text("CurseSMP config reloaded.", NamedTextColor.GREEN));
            }
            default -> err(s, "Unknown subcommand.");
        }
    }

    private void rollFor(Player p) {
        Curse c = Curses.roll(null);
        plugin.data().equip(p, c);
        p.sendMessage(Component.text("You have been cursed with the ", NamedTextColor.GRAY)
                .append(Component.text(c.displayName, c.color)).append(Component.text("!", NamedTextColor.GRAY)));
        Fx.sound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 0.7f);
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 2) {
            err(s, "Usage: /cursesmp give <" + String.join("|", GIVE_ITEMS) + "> [player]");
            return;
        }
        Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player p ? p : null);
        if (t == null) {
            err(s, "Player not found.");
            return;
        }
        String what = a[1].toLowerCase(Locale.ROOT);
        ItemStack item = switch (what) {
            case "energy" -> Items.cursedEnergy(1);
            case "reroll" -> Items.rerollSystem();
            case "shard" -> Items.shadowShard(1);
            case "handle" -> Items.shadowHandle();
            case "shadowslayer" -> Items.shadowSlayer();
            case "soulcrusher" -> Items.soulCrusher();
            case "shield" -> Items.cursedShield();
            default -> {
                Curse c = Curses.byId(what);
                yield (c == null || c.isMythic()) ? null : Items.curse(c);
            }
        };
        if (item == null) {
            err(s, "Unknown item. Options: " + String.join(", ", GIVE_ITEMS));
            return;
        }
        t.getInventory().addItem(item).values().forEach(l -> t.getWorld().dropItemNaturally(t.getLocation(), l));
        s.sendMessage(Component.text("Gave " + what + " to " + t.getName() + ".", NamedTextColor.GREEN));
    }

    // ---- trust ----------------------------------------------------------------

    private void trust(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return;
        if (a.length < 1) {
            Fx.msg(p, "Usage: /cursetrust <player>", NamedTextColor.RED);
            return;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || t.equals(p)) {
            Fx.msg(p, "Player not found (or that's you).", NamedTextColor.RED);
            return;
        }
        if (plugin.data().addTrust(p.getUniqueId(), t.getUniqueId())) {
            Fx.msg(p, "You now trust " + t.getName() + ". Their curses and mythics deal 0 damage to you.", NamedTextColor.GREEN);
            Fx.msg(t, p.getName() + " trusts you. Your curses and mythics can't hurt them. You can remove this with /cursetrustremove "
                    + p.getName() + ".", NamedTextColor.GREEN);
        } else {
            Fx.msg(p, "You already trust " + t.getName() + ".", NamedTextColor.GRAY);
        }
    }

    private void untrust(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return;
        if (a.length < 1) {
            Fx.msg(p, "Usage: /cursetrustremove <player>", NamedTextColor.RED);
            return;
        }
        OfflinePlayer t = Bukkit.getPlayerExact(a[0]);
        if (t == null) t = Bukkit.getOfflinePlayerIfCached(a[0]);
        if (t == null) {
            Fx.msg(p, "Unknown player.", NamedTextColor.RED);
            return;
        }
        UUID other = t.getUniqueId();
        int n = plugin.data().removeTrustBetween(p.getUniqueId(), other);
        if (n == 0) {
            Fx.msg(p, "There is no trust link between you and " + a[0] + ".", NamedTextColor.GRAY);
        } else {
            Fx.msg(p, "Trust link with " + a[0] + " removed.", NamedTextColor.GREEN);
            Player online = Bukkit.getPlayer(other);
            if (online != null) Fx.msg(online, p.getName() + " removed the trust link between you.", NamedTextColor.YELLOW);
        }
    }

    // ---- /curseinfo -----------------------------------------------------------

    private void info(CommandSender s) {
        if (!(s instanceof Player p)) return;
        Curse c = plugin.data().getCurse(p);
        if (c == null) {
            Fx.msg(p, "You have no curse.", NamedTextColor.GRAY);
            return;
        }
        int energy = plugin.data().getEnergy(p);
        p.sendMessage(Component.text(c.displayName, c.color).append(
                Component.text("  (Cursed Energy " + energy + "/" + PlayerData.MAX_ENERGY + ")", NamedTextColor.LIGHT_PURPLE)));
        p.sendMessage(Component.text("Passive: " + c.passive, NamedTextColor.GRAY));
        for (int i = 1; i <= c.abilityCount(); i++) {
            long rem = plugin.cooldowns().remaining(p.getUniqueId(), c.cooldownKey(i));
            String state = (!c.isMythic() && energy < i) ? "locked" : rem > 0 ? Fx.time(rem) : "ready";
            p.sendMessage(Component.text("/ability " + i + " - " + c.abilities[i - 1] + " [" + state + "]", NamedTextColor.GRAY));
        }
    }

    private void err(CommandSender s, String msg) {
        s.sendMessage(Component.text(msg, NamedTextColor.RED));
    }

    // ---- tab completion -------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "cursesmp" -> {
                if (a.length == 1) out.addAll(List.of("give", "start", "randomcurse", "energy", "reload"));
                else if (a.length == 2 && a[0].equalsIgnoreCase("give")) out.addAll(GIVE_ITEMS);
                else if (a.length == 2) players(out);
                else if (a.length == 3 && a[0].equalsIgnoreCase("give")) players(out);
            }
            case "cursetrust", "cursetrustremove" -> {
                if (a.length == 1) players(out);
            }
            case "ability" -> {
                if (a.length == 1) out.addAll(List.of("1", "2", "3", "4"));
            }
            case "mythiceventstart" -> {
                if (a.length == 1) out.addAll(MythicEvents.NAMES);
            }
            default -> {
            }
        }
        String prefix = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }

    private void players(List<String> out) {
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
    }
}
