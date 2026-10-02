package dev.cursesmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

/** Small helpers for particles, sounds and messages. */
public final class Fx {
    private Fx() {}

    public static void msg(Player p, String text, NamedTextColor color) {
        p.sendMessage(Component.text(text, color));
    }

    public static void sound(Location l, Sound s, float volume, float pitch) {
        if (l.getWorld() != null) l.getWorld().playSound(l, s, SoundCategory.PLAYERS, volume, pitch);
    }

    public static void dust(Location l, Color c, float size, int count, double spread) {
        l.getWorld().spawnParticle(Particle.DUST, l, count, spread, spread, spread, 0, new Particle.DustOptions(c, size));
    }

    public static void particle(Location l, Particle p, int count, double spread, double speed) {
        l.getWorld().spawnParticle(p, l, count, spread, spread, spread, speed);
    }

    public static void line(Location a, Location b, double step, Consumer<Location> each) {
        Vector dir = b.toVector().subtract(a.toVector());
        double len = dir.length();
        if (len < 0.01) return;
        dir.normalize();
        for (double t = 0; t <= len; t += step) {
            each.accept(a.clone().add(dir.clone().multiply(t)));
        }
    }

    public static void ring(Location c, double radius, int points, Consumer<Location> each) {
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            each.accept(c.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
    }

    public static String time(long ms) {
        long s = (ms + 999) / 1000;
        return s >= 60 ? (s / 60) + "m" + (s % 60) + "s" : s + "s";
    }
}
