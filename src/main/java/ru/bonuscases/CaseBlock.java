package ru.bonuscases;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Блок в мире, превращённый в кейс. number - номер кейса (набор наград), price - цена открытия. */
public record CaseBlock(String world, int x, int y, int z, double price, int number) {

    public String key() {
        return key(world, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    /** Блок в мире или null, если мир не загружен. */
    public Block block() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : w.getBlockAt(x, y, z);
    }
}
