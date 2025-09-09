package it.samuconfaa.locateCities;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final LocateCities plugin;
    private Economy economy;
    private boolean economyEnabled;

    public EconomyManager(LocateCities plugin) {
        this.plugin = plugin;
        this.economyEnabled = false;
        setupEconomy();
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault non trovato! Economy disabilitata.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager()
                .getRegistration(Economy.class);

        if (rsp == null) {
            plugin.getLogger().warning("Nessun provider Economy trovato! Economy disabilitata.");
            return false;
        }

        economy = rsp.getProvider();
        economyEnabled = true;
        plugin.getLogger().info("Economy abilitata con " + economy.getName());
        return true;
    }

    public boolean isEconomyEnabled() {
        return economyEnabled && plugin.getConfigManager().isEconomyEnabled();
    }

    public boolean hasEnoughMoney(Player player, double amount) {
        if (!isEconomyEnabled()) return true;
        if (player.hasPermission("locatecities.free")) return true;

        return economy.getBalance(player) >= amount;
    }

    public boolean chargeMoney(Player player, double amount, String reason) {
        if (!isEconomyEnabled()) return true;
        if (player.hasPermission("locatecities.free")) return true;

        if (!hasEnoughMoney(player, amount)) {
            return false;
        }

        economy.withdrawPlayer(player, amount);
        return true;
    }

    public double getBalance(Player player) {
        if (!isEconomyEnabled()) return 0;
        return economy.getBalance(player);
    }

    public String formatMoney(double amount) {
        if (!isEconomyEnabled()) return String.valueOf(amount);
        return economy.format(amount);
    }

    public double getSearchCost() {
        return plugin.getConfigManager().getSearchCost();
    }

    public double getTeleportCost() {
        return plugin.getConfigManager().getTeleportCost();
    }

    public int getFreeDistance() {
        return plugin.getConfigManager().getFreeDistance();
    }
}