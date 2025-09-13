package it.samuconfaa.locateCities.commands;

import it.samuconfaa.locateCities.LocateCities;
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
                        player.sendMessage(ChatColor.GRAY + "Questo plugin ti permette di trovare e raggiungere città del mondo reale!");
                        player.sendMessage(ChatColor.GRAY + "I comandi sono stati organizzati per essere più chiari e facili da usare.");
                        break;

                    case 1:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.GREEN + "🔍 " + ChatColor.WHITE + "RICERCA CITTÀ:");
                        player.sendMessage(ChatColor.YELLOW + "   /citta search <nome>" + ChatColor.GRAY + " - Trova le coordinate di una città");
                        player.sendMessage(ChatColor.GRAY + "   Esempio: " + ChatColor.WHITE + "/citta search roma");
                        player.sendMessage(ChatColor.GRAY + "   Esempio: " + ChatColor.WHITE + "/citta search new york");
                        break;

                    case 2:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.BLUE + "✈️ " + ChatColor.WHITE + "TELETRASPORTO:");
                        player.sendMessage(ChatColor.YELLOW + "   /citta tp <nome>" + ChatColor.GRAY + " - Teletrasportati alla città");
                        player.sendMessage(ChatColor.GRAY + "   Esempio: " + ChatColor.WHITE + "/citta tp roma");
                        player.sendMessage(ChatColor.GRAY + "   Esempio: " + ChatColor.WHITE + "/citta tp londra");
                        player.sendMessage(ChatColor.GRAY + "   💡 Puoi anche usare " + ChatColor.WHITE + "/citta teleport <nome>");
                        break;

                    case 3:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "📜 " + ChatColor.WHITE + "CRONOLOGIA:");
                        player.sendMessage(ChatColor.YELLOW + "   /citta history" + ChatColor.GRAY + " - Vedi i tuoi teleport passati");
                        player.sendMessage(ChatColor.GRAY + "   Mostra tutte le città visitate e quando");
                        break;

                    case 4:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.DARK_AQUA + "📚 " + ChatColor.WHITE + "AIUTO E TUTORIAL:");
                        player.sendMessage(ChatColor.YELLOW + "   /citta tutorial" + ChatColor.GRAY + " - Rivedi questo tutorial");
                        player.sendMessage(ChatColor.YELLOW + "   /citta" + ChatColor.GRAY + " - Mostra l'aiuto rapido");
                        break;

                    case 5:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.RED + "⏰ " + ChatColor.WHITE + "SISTEMA COOLDOWN:");
                        if (plugin.getConfigManager().isTeleportDayCooldownEnabled()) {
                            player.sendMessage(ChatColor.GRAY + "   • Puoi teletrasportarti ogni " +
                                    ChatColor.WHITE + plugin.getConfigManager().getTeleportCooldownDays() + " giorni");
                            player.sendMessage(ChatColor.GRAY + "   • Il cooldown è globale (vale per tutte le città)");
                        } else {
                            player.sendMessage(ChatColor.GRAY + "   • Sistema cooldown giorni disabilitato");
                        }
                        if (plugin.getConfigManager().isRateLimitEnabled()) {
                            player.sendMessage(ChatColor.GRAY + "   • Ricerca: " + ChatColor.WHITE +
                                    plugin.getConfigManager().getSearchCooldown() + " secondi tra i comandi");
                            player.sendMessage(ChatColor.GRAY + "   • Teleport: " + ChatColor.WHITE +
                                    plugin.getConfigManager().getTeleportCooldown() + " secondi tra i teleport");
                        }
                        break;

                    case 6:
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

                    case 7:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.YELLOW + "⌨️ " + ChatColor.WHITE + "SUGGERIMENTO TAB COMPLETION:");
                        player.sendMessage(ChatColor.GRAY + "   • Premi " + ChatColor.WHITE + "TAB" + ChatColor.GRAY + " dopo i comandi per suggerimenti");
                        player.sendMessage(ChatColor.GRAY + "   • Funziona con nomi di città e sottcomandi");
                        player.sendMessage(ChatColor.GRAY + "   • Esempio: " + ChatColor.WHITE + "/citta search r" + ChatColor.GRAY + " + TAB = " + ChatColor.WHITE + "Roma");
                        break;

                    case 8:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.DARK_GREEN + "🎯 " + ChatColor.WHITE + "PROVA SUBITO:");
                        player.sendMessage(ChatColor.GRAY + "   1. Scrivi " + ChatColor.YELLOW + "/citta search roma");
                        player.sendMessage(ChatColor.GRAY + "   2. Poi prova " + ChatColor.YELLOW + "/citta tp roma");
                        player.sendMessage(ChatColor.GRAY + "   3. Guarda la cronologia con " + ChatColor.YELLOW + "/citta history");
                        player.sendMessage("");
                        player.sendMessage(ChatColor.GREEN + "✅ Tutorial completato! Buona esplorazione! 🗺️");
                        player.sendMessage(ChatColor.GRAY + "Ricorda: usa " + ChatColor.YELLOW + "/citta" + ChatColor.GRAY + " per vedere i comandi disponibili");
                        cancel();
                        return;
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 60L); // 3 secondi tra ogni step
    }
}