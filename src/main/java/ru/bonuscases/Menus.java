package ru.bonuscases;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class Menus {
    public static final int DEFAULT_WEIGHT = 10;
    public static final int CONFIRM_SLOT = 11;
    public static final int INFO_SLOT = 13;
    public static final int PREVIEW_SLOT = 15;
    public static final int LOOT_SLOTS = 45;
    public static final int BACK_SLOT = 49;
    public static final int HELP_SLOT = 49;
    public static final int TRASH_SLOT = 53;

    private static final int ADDED_LORE_LINES = 4;

    private final BonusCasesPlugin plugin;
    private final NamespacedKey weightKey;
    private final NamespacedKey addedKey;

    public Menus(BonusCasesPlugin plugin) {
        this.plugin = plugin;
        this.weightKey = new NamespacedKey(plugin, "weight");
        this.addedKey = new NamespacedKey(plugin, "added");
    }

    public ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String line : lore) {
                l.add(Text.c(line));
            }
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack filler(Material m) {
        return named(m, " ");
    }

    private void fill(Inventory inv, int from, int to, ItemStack item) {
        for (int i = from; i < to; i++) {
            inv.setItem(i, item);
        }
    }

    public ItemStack display(LootEntry e, double chance, boolean glint, String extraLore) {
        ItemStack it = e.item().clone();
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.c("&7Шанс: &f" + Text.pct(chance) + "%"));
            if (extraLore != null) {
                lore.add(Text.c(extraLore));
            }
            meta.lore(lore);
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    public void openConfirm(Player p, CaseBlock cb) {
        CaseMenu holder = new CaseMenu(CaseMenu.Type.CONFIRM, cb, cb.number());
        String name = plugin.cases().caseName(cb.number());
        Inventory inv = Bukkit.createInventory(holder, 27, Text.c(name));
        holder.setInventory(inv);

        fill(inv, 0, 27, filler(Material.GRAY_STAINED_GLASS_PANE));
        int count = plugin.cases().loot(cb.number()).size();
        inv.setItem(CONFIRM_SLOT, named(Material.LIME_CONCRETE, "&a&lОткрыть кейс",
                "&7Цена: &e" + plugin.economy().format(cb.price()),
                "",
                "&eНажмите, чтобы крутить колесо"));
        inv.setItem(INFO_SLOT, named(Material.ENDER_CHEST, name,
                "&7Кейс №" + cb.number(),
                "&7Наград внутри: &f" + count));
        inv.setItem(PREVIEW_SLOT, named(Material.BOOK, "&b&lЧто внутри?",
                "&7Список наград и шансы выпадения"));
        p.openInventory(inv);
    }

    public void openPreview(Player p, CaseBlock cb) {
        CaseMenu holder = new CaseMenu(CaseMenu.Type.PREVIEW, cb, cb.number());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Text.c("&8Награды: " + plugin.cases().caseName(cb.number())));
        holder.setInventory(inv);

        List<LootEntry> list = plugin.cases().loot(cb.number());
        for (int i = 0; i < list.size() && i < LOOT_SLOTS; i++) {
            LootEntry e = list.get(i);
            inv.setItem(i, display(e, CaseManager.chance(e, list), false, null));
        }
        fill(inv, LOOT_SLOTS, 54, filler(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(BACK_SLOT, named(Material.ARROW, "&eНазад"));
        p.openInventory(inv);
    }

    public void openEditor(Player p, int number) {
        CaseMenu holder = new CaseMenu(CaseMenu.Type.EDITOR, null, number);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Text.c("&8Награды: " + plugin.cases().caseName(number)));
        holder.setInventory(inv);

        List<LootEntry> list = plugin.cases().loot(number);
        for (int i = 0; i < list.size() && i < LOOT_SLOTS; i++) {
            inv.setItem(i, withWeight(list.get(i).item(), list.get(i).weight()));
        }
        fill(inv, LOOT_SLOTS, 54, filler(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(HELP_SLOT, named(Material.PAPER, "&e&lКак пользоваться",
                "&7Положите предметы в верхнюю часть -",
                "&7они станут наградами кейса №" + number + ".",
                "&7Количество в стаке = количество в награде.",
                "",
                "&7Shift+ЛКМ / Shift+ПКМ: вес +1 / -1",
                "&7Q / Ctrl+Q: вес +10 / -10",
                "&7Чем больше вес, тем чаще выпадает.",
                "",
                "&7Убрать награду: возьмите её и нажмите",
                "&7на корзину справа внизу.",
                "&aСохраняется при закрытии окна."));
        inv.setItem(TRASH_SLOT, named(Material.BARRIER, "&c&lКорзина",
                "&7Кликните сюда предметом на курсоре,",
                "&7чтобы убрать его из наград."));
        refreshEditor(inv);
        p.openInventory(inv);
    }

    public boolean isMarked(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) {
            return false;
        }
        return it.getItemMeta().getPersistentDataContainer().has(addedKey, PersistentDataType.INTEGER);
    }

    public int weightOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return DEFAULT_WEIGHT;
        }
        Integer w = it.getItemMeta().getPersistentDataContainer().get(weightKey, PersistentDataType.INTEGER);
        return w == null ? DEFAULT_WEIGHT : Math.max(1, w);
    }

    public ItemStack withWeight(ItemStack it, int weight) {
        ItemStack c = it.clone();
        ItemMeta meta = c.getItemMeta();
        if (meta == null) {
            return c;
        }
        meta.getPersistentDataContainer().set(weightKey, PersistentDataType.INTEGER, weight);
        c.setItemMeta(meta);
        return c;
    }

    private ItemStack decorate(ItemStack base, int weight, double chance) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) {
            return it;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Text.c("&7Вес: &e" + weight));
        lore.add(Text.c("&7Шанс: &f" + Text.pct(chance) + "%"));
        lore.add(Text.c("&8Shift+ЛКМ / Shift+ПКМ: вес +1 / -1"));
        lore.add(Text.c("&8Q / Ctrl+Q: вес +10 / -10"));
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(weightKey, PersistentDataType.INTEGER, weight);
        pdc.set(addedKey, PersistentDataType.INTEGER, ADDED_LORE_LINES);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack undecorate(ItemStack src) {
        ItemStack it = src.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) {
            return it;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer added = pdc.get(addedKey, PersistentDataType.INTEGER);
        if (added != null) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                int keep = Math.max(0, lore.size() - added);
                List<Component> nl = new ArrayList<>(lore.subList(0, keep));
                meta.lore(nl.isEmpty() ? null : nl);
            }
        }
        pdc.remove(weightKey);
        pdc.remove(addedKey);
        it.setItemMeta(meta);
        return it;
    }

    public void refreshEditor(Inventory inv) {
        List<Integer> slots = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < LOOT_SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) {
                continue;
            }
            slots.add(i);
            total += weightOf(it);
        }
        for (int i : slots) {
            ItemStack it = inv.getItem(i);
            int w = weightOf(it);
            double chance = total == 0 ? 0 : 100.0 * w / total;
            inv.setItem(i, decorate(undecorate(it), w, chance));
        }
    }

    public List<LootEntry> collectEditor(Inventory inv) {
        List<LootEntry> out = new ArrayList<>();
        for (int i = 0; i < LOOT_SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) {
                continue;
            }
            out.add(new LootEntry(undecorate(it), weightOf(it)));
        }
        return out;
    }
}
