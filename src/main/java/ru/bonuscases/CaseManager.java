package ru.bonuscases;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

public final class CaseManager {
    private final BonusCasesPlugin plugin;
    private final File casesFile;
    private final File lootFile;
    private final Map<String, CaseBlock> cases = new LinkedHashMap<>();
    private final Map<Integer, List<LootEntry>> loot = new TreeMap<>();

    public CaseManager(BonusCasesPlugin plugin) {
        this.plugin = plugin;
        this.casesFile = new File(plugin.getDataFolder(), "cases.yml");
        this.lootFile = new File(plugin.getDataFolder(), "loot.yml");
    }

    public void load() {
        cases.clear();
        loot.clear();

        YamlConfiguration cy = YamlConfiguration.loadConfiguration(casesFile);
        ConfigurationSection cs = cy.getConfigurationSection("cases");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                String world = s.getString("world");
                if (world == null) {
                    continue;
                }
                CaseBlock cb = new CaseBlock(world, s.getInt("x"), s.getInt("y"), s.getInt("z"),
                        s.getDouble("price"), s.getInt("number", 1));
                cases.put(cb.key(), cb);
            }
        }

        YamlConfiguration ly = YamlConfiguration.loadConfiguration(lootFile);
        ConfigurationSection ls = ly.getConfigurationSection("loot");
        if (ls != null) {
            for (String num : ls.getKeys(false)) {
                int n;
                try {
                    n = Integer.parseInt(num);
                } catch (NumberFormatException ex) {
                    continue;
                }
                ConfigurationSection ns = ls.getConfigurationSection(num);
                if (ns == null) {
                    continue;
                }
                List<LootEntry> list = new ArrayList<>();
                for (String idx : ns.getKeys(false)) {
                    ConfigurationSection es = ns.getConfigurationSection(idx);
                    if (es == null) {
                        continue;
                    }
                    ItemStack item = es.getItemStack("item");
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }
                    list.add(new LootEntry(item, Math.max(1, es.getInt("weight", Menus.DEFAULT_WEIGHT))));
                }
                loot.put(n, list);
            }
        }
    }

    private void saveCases() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (CaseBlock cb : cases.values()) {
            String p = "cases." + (i++) + ".";
            y.set(p + "world", cb.world());
            y.set(p + "x", cb.x());
            y.set(p + "y", cb.y());
            y.set(p + "z", cb.z());
            y.set(p + "price", cb.price());
            y.set(p + "number", cb.number());
        }
        write(y, casesFile);
    }

    private void saveLoot() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<Integer, List<LootEntry>> e : loot.entrySet()) {
            int i = 0;
            for (LootEntry le : e.getValue()) {
                String p = "loot." + e.getKey() + "." + (i++) + ".";
                y.set(p + "item", le.item());
                y.set(p + "weight", le.weight());
            }
        }
        write(y, lootFile);
    }

    private void write(YamlConfiguration y, File file) {
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось сохранить " + file.getName() + ": " + ex.getMessage());
        }
    }

    public CaseBlock at(Block b) {
        return cases.get(CaseBlock.key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
    }

    public void add(CaseBlock cb) {
        cases.put(cb.key(), cb);
        saveCases();
    }

    public boolean remove(Block b) {
        CaseBlock removed = cases.remove(CaseBlock.key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
        if (removed != null) {
            saveCases();
            return true;
        }
        return false;
    }

    public Collection<CaseBlock> all() {
        return new ArrayList<>(cases.values());
    }

    public List<LootEntry> loot(int number) {
        List<LootEntry> l = loot.get(number);
        return l == null ? List.of() : List.copyOf(l);
    }

    public Collection<Integer> lootNumbers() {
        return new ArrayList<>(loot.keySet());
    }

    public void setLoot(int number, List<LootEntry> entries) {
        if (entries.isEmpty()) {
            loot.remove(number);
        } else {
            loot.put(number, new ArrayList<>(entries));
        }
        saveLoot();
    }

    public static long totalWeight(List<LootEntry> list) {
        long total = 0;
        for (LootEntry e : list) {
            total += e.weight();
        }
        return total;
    }

    public static double chance(LootEntry e, List<LootEntry> list) {
        long total = totalWeight(list);
        return total == 0 ? 0 : 100.0 * e.weight() / total;
    }

    public LootEntry pick(int number) {
        List<LootEntry> list = loot(number);
        if (list.isEmpty()) {
            return null;
        }
        long r = ThreadLocalRandom.current().nextLong(totalWeight(list));
        for (LootEntry e : list) {
            r -= e.weight();
            if (r < 0) {
                return e;
            }
        }
        return list.get(list.size() - 1);
    }

    public String caseName(int number) {
        String n = plugin.getConfig().getString("case-names." + number);
        return n != null ? n : "&fКейс №" + number;
    }
}
