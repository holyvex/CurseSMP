package dev.cursesmp.curse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Curses {
    private Curses() {}

    public static Curse BLEEDING;
    public static Curse REAPER;
    public static CurseOfFreezing FREEZING;
    public static Curse EARTH;
    public static Curse ANGEL;
    public static Curse DRAGON;

    private static final Map<String, Curse> BY_ID = new LinkedHashMap<>();

    public static void init() {
        BY_ID.clear();
        BLEEDING = reg(new CurseOfBleeding());
        REAPER = reg(new CurseOfTheReaper());
        FREEZING = (CurseOfFreezing) reg(new CurseOfFreezing());
        EARTH = reg(new CurseOfEarth());
        ANGEL = reg(new CurseOfAngel());
        DRAGON = reg(new CurseOfTheDragon());
    }

    private static Curse reg(Curse c) {
        BY_ID.put(c.id, c);
        return c;
    }

    public static Curse byId(String id) {
        return BY_ID.get(id);
    }

    public static Collection<Curse> all() {
        return BY_ID.values();
    }

    /** Curses that can be rolled (the Dragon curse is egg-only). */
    public static List<Curse> rollable() {
        return List.of(BLEEDING, REAPER, FREEZING, EARTH, ANGEL);
    }

    /**
     * Every rollable curse gets a 25% roll (in random order); the first one to succeed wins.
     * If none succeed the roll repeats. {@code exclude} (may be null) is skipped so rerolls change your curse.
     */
    public static Curse roll(Curse exclude) {
        List<Curse> pool = new ArrayList<>(rollable());
        if (exclude != null && pool.size() > 1) pool.remove(exclude);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        while (true) {
            Collections.shuffle(pool);
            for (Curse c : pool) {
                if (r.nextDouble() < 0.25) return c;
            }
        }
    }
}
