package ru.bonuscases;

import org.bukkit.inventory.ItemStack;

/** Одна награда в кейсе: предмет (с количеством) и вес. Чем больше вес, тем чаще выпадает. */
public record LootEntry(ItemStack item, int weight) {
}
