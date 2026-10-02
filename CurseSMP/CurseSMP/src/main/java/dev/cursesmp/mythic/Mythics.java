package dev.cursesmp.mythic;

import dev.cursesmp.Items;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class Mythics {
    private Mythics() {}

    private static final Map<String, Mythic> BY_ID = new HashMap<>();

    public static void init() {
        BY_ID.clear();
        BY_ID.put(Items.SHADOW_SLAYER, new ShadowSlayer());
        BY_ID.put(Items.SOUL_CRUSHER, new SoulCrusher());
    }

    /** The mythic weapon this item is, or null. */
    public static Mythic fromItem(ItemStack s) {
        String id = Items.idOf(s);
        return id == null ? null : BY_ID.get(id);
    }
}
