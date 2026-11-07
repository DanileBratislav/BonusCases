package ru.bonuscases;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Списание нартов. Vault и PlaceholderAPI вызываются через reflection,
 * поэтому плагин собирается только с paper-api.
 */
public final class EconomyBridge {
    private final BonusCasesPlugin plugin;

    private Object provider;
    private Method mHas;
    private Method mWithdraw;
    private Method mDeposit;
    private Method mSuccess;

    public EconomyBridge(BonusCasesPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean commandMode() {
        return "command".equalsIgnoreCase(plugin.getConfig().getString("economy.mode", "vault"));
    }

    public boolean available() {
        return commandMode() ? papiAvailable() : resolveVault();
    }

    public boolean has(Player p, double amount) {
        try {
            if (commandMode()) {
                double bal = balanceViaPapi(p);
                return !Double.isNaN(bal) && bal + 1e-9 >= amount;
            }
            if (!resolveVault()) {
                return false;
            }
            return (Boolean) mHas.invoke(provider, p, amount);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Ошибка проверки баланса: " + ex);
            return false;
        }
    }

    /** Списывает сумму. true - успешно. */
    public boolean withdraw(Player p, double amount) {
        try {
            if (commandMode()) {
                return runCommand("economy.command.take", p, amount);
            }
            if (!resolveVault()) {
                return false;
            }
            Object response = mWithdraw.invoke(provider, p, amount);
            return (Boolean) mSuccess.invoke(response);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Ошибка списания: " + ex);
            return false;
        }
    }

    /** Возврат денег (если после списания что-то пошло не так). */
    public void deposit(Player p, double amount) {
        try {
            if (commandMode()) {
                runCommand("economy.command.give", p, amount);
            } else if (resolveVault()) {
                mDeposit.invoke(provider, p, amount);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().severe("Не удалось вернуть " + amount + " игроку " + p.getName() + ": " + ex);
        }
    }

    /** "15 нартов" */
    public String format(double amount) {
        List<String> forms = plugin.getConfig().getStringList("currency.forms");
        String one = forms.size() > 0 ? forms.get(0) : "нарт";
        String few = forms.size() > 1 ? forms.get(1) : "нарта";
        String many = forms.size() > 2 ? forms.get(2) : "нартов";
        boolean whole = amount == Math.rint(amount);
        String word = whole ? Text.plural((long) amount, one, few, many) : many;
        return Text.num(amount) + " " + word;
    }

    private boolean resolveVault() {
        if (provider != null) {
            return true;
        }
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) {
                return false;
            }
            Class<?> resp = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            mHas = eco.getMethod("has", OfflinePlayer.class, double.class);
            mWithdraw = eco.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            mDeposit = eco.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            mSuccess = resp.getMethod("transactionSuccess");
            provider = rsp.getProvider();
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private boolean papiAvailable() {
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private double balanceViaPapi(Player p) throws ReflectiveOperationException {
        Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
        Method m = papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        String ph = plugin.getConfig().getString("economy.command.balance-placeholder", "%vault_eco_balance%");
        String raw = (String) m.invoke(null, p, ph);
        return parseNumber(raw);
    }

    private static double parseNumber(String raw) {
        if (raw == null) {
            return Double.NaN;
        }
        String s = raw.replaceAll("[^0-9.,-]", "");
        if (s.contains(",") && s.contains(".")) {
            s = s.replace(",", "");
        } else {
            s = s.replace(',', '.');
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private boolean runCommand(String configPath, Player p, double amount) {
        String cmd = plugin.getConfig().getString(configPath, "");
        if (cmd.isBlank()) {
            return false;
        }
        cmd = cmd.replace("{player}", p.getName()).replace("{amount}", Text.num(amount));
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}
