package dev.cursesmp;

import dev.cursesmp.command.Commands;
import dev.cursesmp.curse.Curse;
import dev.cursesmp.curse.Curses;
import dev.cursesmp.event.MythicEvents;
import dev.cursesmp.listener.ItemListener;
import dev.cursesmp.listener.PowerListener;
import dev.cursesmp.mythic.Mythics;
import dev.cursesmp.ritual.RitualManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CurseSMP extends JavaPlugin {
    private static CurseSMP instance;
    private PlayerData data;
    private Cooldowns cooldowns;
    private RitualManager rituals;
    private MythicEvents events;
    private int ticks;

    public static CurseSMP get() {
        return instance;
    }

    public PlayerData data() {
        return data;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public RitualManager rituals() {
        return rituals;
    }

    public MythicEvents events() {
        return events;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        data = new PlayerData(this);
        cooldowns = new Cooldowns(this);
        Combat.init(this);
        Powers.init(this);
        Items.init(this);
        Curses.init();
        Mythics.init();
        rituals = new RitualManager(this);
        events = new MythicEvents(this);
        Items.registerRecipes();

        getServer().getPluginManager().registerEvents(new PowerListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemListener(this), this);

        Commands cmds = new Commands(this);
        for (String name : new String[]{"cursesmp", "cursetrust", "cursetrustremove", "ability", "curseinfo", "mythiceventstart"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(cmds);
                c.setTabCompleter(cmds);
            }
        }
        Bukkit.getScheduler().runTaskTimer(this, this::mainTick, 20L, 5L);
        getLogger().info("CurseSMP enabled.");
    }

    @Override
    public void onDisable() {
        if (rituals != null) rituals.shutdown();
        Powers.shutdown();
        if (data != null) data.saveTrust();
    }

    /** Every 5 ticks: passives, Dragon Egg check, Cursed Shield hearts, action-bar cooldowns. */
    private void mainTick() {
        ticks++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) continue;
            if (ticks % 4 == 0) {
                data.syncDragon(p);
                Items.applyShieldHearts(p);
            }
            Curse c = data.getCurse(p);
            if (c != null) c.passiveTick(p);
            Hud.show(p);
        }
    }
}
