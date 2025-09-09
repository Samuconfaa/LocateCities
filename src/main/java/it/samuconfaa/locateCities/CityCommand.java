package it.samuconfaa.locateCities;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CityCommand implements CommandExecutor {

    private final CityManager cityManager;

    public CityCommand(CityManager cityManager) {
        this.cityManager = cityManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Uso: /citta <nome> [tp]");
            sender.sendMessage(ChatColor.GRAY + "Esempio: /citta Roma");
            sender.sendMessage(ChatColor.GRAY + "Esempio: /citta Milano tp");
            return true;
        }

        String cityName = args[0];
        boolean teleport = args.length > 1 && args[1].equalsIgnoreCase("tp");

        // Controlla se il teleport è richiesto ma il sender non è un player
        if (teleport && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo i giocatori possono teletrasportarsi!");
            return true;
        }

        // Controlla se il teleport è abilitato
        if (teleport && !cityManager.configManager.isTeleportEnabled()) {
            sender.sendMessage(ChatColor.RED + "Il teletrasporto è disabilitato!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "🔍 Ricerca di " + cityName + " in corso...");

        cityManager.findCity(cityName).whenComplete((cityData, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(ChatColor.RED + "❌ Errore: " + throwable.getMessage());
                return;
            }

            CityData.MinecraftCoordinates coords = cityData.toMinecraftCoordinates(cityManager.configManager);

            // Messaggio con le coordinate
            String message = String.format("📍 %s%s%s si trova alle coordinate %sX:%d Z:%d%s",
                    ChatColor.GREEN, cityData.getName(), ChatColor.WHITE,
                    ChatColor.AQUA, coords.getX(), coords.getZ(), ChatColor.WHITE);

            sender.sendMessage(message);

            // Se è richiesto il teleport
            if (teleport && sender instanceof Player) {
                Player player = (Player) sender;
                Location location = cityManager.getMinecraftLocation(cityData, player.getWorld());

                player.teleport(location);
                player.sendMessage(ChatColor.GREEN + "✈ Teletrasportato a " + cityData.getName() + "!");
            } else if (!teleport) {
                // Suggerisci il teleport se disponibile
                if (sender instanceof Player && cityManager.configManager.isTeleportEnabled()) {
                    sender.sendMessage(ChatColor.GRAY + "💡 Usa '/citta " + cityName + " tp' per teletrasportarti!");
                }
            }
        });

        return true;
    }
}