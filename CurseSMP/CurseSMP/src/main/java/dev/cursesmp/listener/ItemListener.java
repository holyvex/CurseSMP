package dev.cursesmp.listener;

import dev.cursesmp.CurseSMP;
import dev.cursesmp.Fx;
import dev.cursesmp.Items;
import dev.cursesmp.PlayerData;
import dev.cursesmp.curse.Curse;
import dev.cursesmp.curse.Curses;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Keyed;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Locale;

/** Right-click behaviour of custom items, crafting protection and pickups. */
public final class ItemListener implements Listener {
    private final CurseSMP plugin;
    private final PlayerData data;

    public ItemListener(CurseSMP plugin) {
        this.plugin = plugin;
        this.data = plugin.data();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = Items.idOf(e.getItem());
        if (id == null) return;
        Player p = e.getPlayer();

        if (id.startsWith(Items.CURSE_PREFIX)) {
            e.setCancelled(true);
            Curse c = Curses.byId(id.substring(Items.CURSE_PREFIX.length()));
            if (c == null || c.isMythic()) return;
            if (data.getCurse(p) == c) {
                Fx.msg(p, "You already wield the " + c.displayName + ".", NamedTextColor.GRAY);
                return;
            }
            data.equip(p, c);
            Items.consumeOne(p);
            Fx.sound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.8f);
            Fx.particle(p.getLocation().add(0, 1, 0), Particle.ENCHANT, 60, 0.6, 0.8);
            Fx.dust(p.getLocation().add(0, 1, 0), c.dust, 1.5f, 40, 0.6);
            Fx.msg(p, "You equipped the " + c.displayName + "!", c.color);
        } else if (id.equals(Items.ENERGY)) {
            e.setCancelled(true);
            int en = data.getEnergy(p);
            if (en >= PlayerData.MAX_ENERGY) {
                Fx.msg(p, "You already hold the maximum Cursed Energy.", NamedTextColor.GRAY);
                return;
            }
            data.setEnergy(p, en + 1);
            Items.consumeOne(p);
            Fx.sound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2f, 0.7f);
            Fx.particle(p.getLocation().add(0, 1, 0), Particle.ENCHANT, 40, 0.5, 0.6);
            Fx.msg(p, "Cursed Energy " + (en + 1) + "/" + PlayerData.MAX_ENERGY + " - ability " + (en + 1) + " unlocked!",
                    NamedTextColor.LIGHT_PURPLE);
        } else if (id.equals(Items.REROLL)) {
            e.setCancelled(true);
            plugin.rituals().start(p);
        }
    }

    /** Stop dyes / bones etc. from being used on animals when right-clicking with a custom item. */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        String id = Items.idOf(hand);
        if (id == null) return;
        if (id.startsWith(Items.CURSE_PREFIX) || id.equals(Items.ENERGY) || id.equals(Items.REROLL)) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack st = e.getItem().getItemStack();
        if (Items.is(st, Items.SHARD)) plugin.events().onShardPickup(p, st.getAmount());
    }

    /** Custom items can only be used as crafting ingredients in the Shadow Slayer recipe. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        boolean hasCustom = false;
        for (ItemStack s : e.getInventory().getMatrix()) {
            if (s != null && Items.idOf(s) != null) {
                hasCustom = true;
                break;
            }
        }
        if (!hasCustom) return;
        Recipe r = e.getRecipe();
        boolean slayer = r instanceof Keyed k
                && k.getKey().getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))
                && k.getKey().getKey().equals("shadow_slayer");
        if (!slayer) e.getInventory().setResult(null);
    }

    // ---- the Angel's temporary mace must not leave the inventory ---------------------

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (Items.is(e.getItemDrop().getItemStack(), Items.TEMP_MACE)) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        boolean temp = Items.is(e.getCurrentItem(), Items.TEMP_MACE) || Items.is(e.getCursor(), Items.TEMP_MACE);
        if (temp && e.getView().getTopInventory().getType() != InventoryType.CRAFTING) e.setCancelled(true);
    }
}
