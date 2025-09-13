package it.samuconfaa.locateCities;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TutorialCommand implements CommandExecutor {

    private final LocateCities plugin;

    public TutorialCommand(LocateCities plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Il tutorial è disponibile solo per i giocatori!");
            return true;
        }

        Player player = (Player) sender;
        startTutorial(player);
        return true;
    }

    private void startTutorial(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║" + ChatColor.YELLOW + "    🎓 TUTORIAL LOCATECITIES 🎓     " + ChatColor.GOLD + "║");
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
        player.sendMessage("");

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                switch (step) {
                    case 0:
                        player.sendMessage(ChatColor.AQUA + "📖 " + ChatColor.WHITE + "BENVENUTO IN LOCATECITIES!");
                        player.sendMessage(ChatColor.GRAY + "Questo plugin ti permette di trovare e raggiunger città del mondo reale!");
                        break;
                    case 1:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.GREEN + "🔍 " + ChatColor.WHITE + "RICERCA CITTÀ:");
                        player.sendMessage(ChatColor.YELLOW + "   /citta <nome>" + ChatColor.GRAY + " - Trova le coordinate di una città");
                        player.sendMessage(ChatColor.GRAY + "   Esempio: " + ChatColor.WHITE + "/citta roma");
                        break;
                    case 2:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.BLUE + "✈️ " + ChatColor.WHITE + "TELETRASPORTO:");
                        player.sendMessage(ChatColor.YELLOW + "   /citta <nome> tp" + ChatColor.GRAY + " - Teletrasportati alla città");
                        player.sendMessage(ChatColor.GRAY + "   Esempio: " + ChatColor.WHITE + "/citta roma tp");
                        break;
                    case 3:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "📜 " + ChatColor.WHITE + "CRONOLOGIA:");
                        player.sendMessage(ChatColor.YELLOW + "   /citta history" + ChatColor.GRAY + " - Vedi i tuoi teleport passati");
                        break;
                    case 4:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.RED + "⏰ " + ChatColor.WHITE + "SISTEMA COOLDOWN:");
                        if (plugin.getConfigManager().isTeleportDayCooldownEnabled()) {
                            player.sendMessage(ChatColor.GRAY + "   • Puoi teletrasportarti alla stessa città ogni " +
                                    ChatColor.WHITE + plugin.getConfigManager().getTeleportCooldownDays() + " giorni");
                        } else {
                            player.sendMessage(ChatColor.GRAY + "   • Sistema cooldown giorni disabilitato");
                        }
                        break;
                    case 5:
                        player.sendMessage("");
                        if (plugin.getEconomyManager().isEconomyEnabled()) {
                            player.sendMessage(ChatColor.GOLD + "💰 " + ChatColor.WHITE + "COSTI:");
                            player.sendMessage(ChatColor.GRAY + "   • Ricerca: " + ChatColor.WHITE + "$" +
                                    plugin.getEconomyManager().getSearchCost());
                            player.sendMessage(ChatColor.GRAY + "   • Teleport: " + ChatColor.WHITE + "$" +
                                    plugin.getEconomyManager().getTeleportCost());
                            player.sendMessage(ChatColor.GRAY + "   • Gratuito entro " + ChatColor.WHITE +
                                    plugin.getEconomyManager().getFreeDistance() + " blocchi");
                        } else {
                            player.sendMessage(ChatColor.GOLD + "💰 " + ChatColor.WHITE + "ECONOMIA:");
                            player.sendMessage(ChatColor.GRAY + "   • Sistema economico disabilitato");
                        }
                        break;
                    case 6:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.DARK_GREEN + "🎯 " + ChatColor.WHITE + "PROVA SUBITO:");
                        player.sendMessage(ChatColor.GRAY + "   Scrivi " + ChatColor.YELLOW + "/citta " +
                                ChatColor.WHITE + "seguita dal nome di una città!");
                        player.sendMessage("");
                        player.sendMessage(ChatColor.GREEN + "✅ Tutorial completato! Buona esplorazione! 🗺️");
                        cancel();
                        return;
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 60L); // 3 secondi tra ogni step
    }
}