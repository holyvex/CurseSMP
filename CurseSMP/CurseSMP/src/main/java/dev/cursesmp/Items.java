package dev.cursesmp;

import dev.cursesmp.curse.Curse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Factory + recipes for every custom item. Items are identified by a PDC string id. */
public final class Items {
    private Items() {}

    public static final String CURSE_PREFIX = "curse_";
    public static final String ENERGY = "cursed_energy";
    public static final String REROLL = "reroll_system";
    public static final String SHARD = "shadow_shard";
    public static final String HANDLE = "shadow_handle";
    public static final String SHADOW_SLAYER = "shadow_slayer";
    public static final String SOUL_CRUSHER = "soul_crusher";
    public static final String SHIELD = "cursed_shield";
    public static final String COORDS = "shield_coords";
    public static final String TEMP_MACE = "temp_mace";

    private static CurseSMP plugin;
    private static NamespacedKey idKey;
    private static NamespacedKey heartKey;

    public static void init(CurseSMP p) {
        plugin = p;
        idKey = new NamespacedKey(p, "item");
        heartKey = new NamespacedKey(p, "shield_hearts");
    }

    public static String idOf(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return null;
        return s.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public static boolean is(ItemStack s, String id) {
        return id.equals(idOf(s));
    }

    public static void consumeOne(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else hand.setAmount(hand.getAmount() - 1);
    }

    private static ItemStack make(Material mat, String id, String name, NamedTextColor color, boolean glint,
                                  String model, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) {
            lines.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        if (glint) meta.setEnchantmentGlintOverride(true);
        if (model != null && plugin.getConfig().getBoolean("custom-models", false)) {
            NamespacedKey k = NamespacedKey.fromString("cursesmp:" + model);
            if (k != null) meta.setItemModel(k);
        }
        it.setItemMeta(meta);
        return it;
    }

    // ---- items ----------------------------------------------------------------

    public static ItemStack curse(Curse c) {
        List<String> lore = new ArrayList<>();
        lore.add("Right-click to equip this curse.");
        lore.add("");
        lore.add("Passive: " + c.passive);
        for (int i = 0; i < c.abilities.length; i++) {
            lore.add("Ability " + (i + 1) + ": " + c.abilities[i]);
        }
        return make(c.icon, CURSE_PREFIX + c.id, c.displayName, c.color, true, "curse_" + c.id,
                lore.toArray(new String[0]));
    }

    public static ItemStack cursedEnergy(int amount) {
        ItemStack it = make(Material.AMETHYST_SHARD, ENERGY, "Cursed Energy", NamedTextColor.DARK_PURPLE, true,
                "cursed_energy", "Right-click to absorb.", "Each point unlocks one curse ability (max 3).");
        it.setAmount(amount);
        return it;
    }

    public static ItemStack rerollSystem() {
        return make(Material.NETHER_STAR, REROLL, "Reroll System", NamedTextColor.LIGHT_PURPLE, true,
                "reroll_system", "Right-click to begin a Reroll Ritual.",
                "Survive 5 minutes to reroll your curse.", "Die during the ritual and your curse is lost!");
    }

    public static ItemStack shadowShard(int amount) {
        ItemStack it = make(Material.ECHO_SHARD, SHARD, "Shadow Shard", NamedTextColor.DARK_PURPLE, true,
                "shadow_shard", "Fallen from a shadow meteor.");
        it.setAmount(amount);
        return it;
    }

    public static ItemStack shadowHandle() {
        return make(Material.BLAZE_ROD, HANDLE, "Shadow Handle", NamedTextColor.DARK_GRAY, true,
                "shadow_handle", "A handle forged from darkness.", "Craft with 4 Shadow Shards + a Netherite Sword.");
    }

    public static ItemStack shadowSlayer() {
        return make(Material.NETHERITE_SWORD, SHADOW_SLAYER, "Shadow Slayer", NamedTextColor.DARK_PURPLE, true,
                "shadow_slayer", "Mythic Weapon",
                "F: Wither Heads (2 hearts each)", "Shift+F: Shadow Beam (4 hearts)");
    }

    public static ItemStack soulCrusher() {
        ItemStack it = make(Material.MACE, SOUL_CRUSHER, "Soul Crusher", NamedTextColor.AQUA, true,
                "soul_crusher", "Mythic Weapon", "F: Skyfall Smash (3 hearts)", "Shift+F: Repulse");
        ItemMeta m = it.getItemMeta();
        m.addEnchant(Enchantment.WIND_BURST, 2, true);
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack cursedShield() {
        ItemStack it = make(Material.SHIELD, SHIELD, "Cursed Shield", NamedTextColor.DARK_RED, true,
                "cursed_shield", "Mythic Shield",
                "Blocking: 25% chance to block a cursed ability", "Holding: +3 extra hearts", "Unbreakable");
        ItemMeta m = it.getItemMeta();
        m.setUnbreakable(true);
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack shieldCoords(Location l) {
        return make(Material.PAPER, COORDS, "Shield Coordinates", NamedTextColor.GOLD, true, "shield_coords",
                "Dropped by the Cursed Assassin.",
                "X: " + l.getBlockX(), "Y: " + l.getBlockY(), "Z: " + l.getBlockZ(),
                "World: " + l.getWorld().getName(), "A chest holds the Cursed Shield there.");
    }

    public static ItemStack tempMace() {
        return make(Material.MACE, TEMP_MACE, "Angel's Mace", NamedTextColor.GOLD, true, null,
                "Summoned for 10 seconds.", "Every hit deals 1 heart.");
    }

    // ---- Cursed Shield passive: +3 hearts while it is in the inventory --------

    public static void applyShieldHearts(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        boolean has = false;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && is(s, SHIELD)) {
                has = true;
                break;
            }
        }
        AttributeModifier existing = null;
        for (AttributeModifier m : attr.getModifiers()) {
            if (heartKey.equals(m.getKey())) existing = m;
        }
        if (has && existing == null) {
            attr.addModifier(new AttributeModifier(heartKey, 6.0, AttributeModifier.Operation.ADD_NUMBER));
        } else if (!has && existing != null) {
            attr.removeModifier(existing);
        }
    }

    // ---- recipes --------------------------------------------------------------

    public static void registerRecipes() {
        NamespacedKey kEnergy = new NamespacedKey(plugin, "cursed_energy");
        NamespacedKey kReroll = new NamespacedKey(plugin, "reroll_system");
        NamespacedKey kSlayer = new NamespacedKey(plugin, "shadow_slayer");
        Bukkit.removeRecipe(kEnergy);
        Bukkit.removeRecipe(kReroll);
        Bukkit.removeRecipe(kSlayer);

        ShapelessRecipe energy = new ShapelessRecipe(kEnergy, cursedEnergy(1));
        energy.addIngredient(Material.DIAMOND_BLOCK);
        energy.addIngredient(Material.ECHO_SHARD);
        energy.addIngredient(Material.NETHERITE_SCRAP);
        energy.addIngredient(Material.AMETHYST_SHARD);
        Bukkit.addRecipe(energy);

        ShapedRecipe reroll = new ShapedRecipe(kReroll, rerollSystem());
        reroll.shape("DED", "ENE", "DED");
        reroll.setIngredient('D', Material.DIAMOND);
        reroll.setIngredient('E', Material.ECHO_SHARD);
        reroll.setIngredient('N', Material.NETHER_STAR);
        Bukkit.addRecipe(reroll);

        ShapedRecipe slayer = new ShapedRecipe(kSlayer, shadowSlayer());
        slayer.shape(" S ", "SWS", " H ");
        slayer.setIngredient('S', new RecipeChoice.ExactChoice(shadowShard(1)));
        slayer.setIngredient('W', Material.NETHERITE_SWORD);
        slayer.setIngredient('H', new RecipeChoice.ExactChoice(shadowHandle()));
        Bukkit.addRecipe(slayer);
    }
}
