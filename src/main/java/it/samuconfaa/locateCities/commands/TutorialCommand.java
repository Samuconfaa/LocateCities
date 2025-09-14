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

        // Controlla se il giocatore ha accesso VIP
        boolean hasVipAccess = false;
        if (plugin.getConfigManager().isVipTeleportSystemEnabled()) {
            hasVipAccess = player.hasPermission(plugin.getConfigManager().getVipTeleportPermission()) ||
                    player.hasPermission("locatecities.admin") ||
                    player.hasPermission("locatecities.free");
        } else {
            hasVipAccess = player.hasPermission("locatecities.teleport");
        }

        boolean finalHasVipAccess = hasVipAccess;
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
                        player.sendMessage(ChatColor.GREEN + "   ✅ Questo comando è disponibile per tutti!");
                        break;

                    case 2:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.BLUE + "✈️ " + ChatColor.WHITE + "TELETRASPORTO:");

                        if (plugin.getConfigManager().isVipTeleportSystemEnabled()) {
                            if (finalHasVipAccess) {
                                player.sendMessage(ChatColor.YELLOW + "   /citta tp <nome>" + ChatColor.GRAY + " - Teletrasportati alla città");
                                player.sendMessage(ChatColor.GRAY + "   Esempio: " + ChatColor.WHITE + "/citta tp roma");
                                player.sendMessage(ChatColor.GRAY + "   Esempio: " + ChatColor.WHITE + "/citta tp londra");
                                player.sendMessage(ChatColor.GREEN + "   ✅ Hai accesso VIP al teletrasporto!");
                                player.sendMessage(ChatColor.GOLD + "   🌟 Cooldown: ogni " +
                                        plugin.getConfigManager().getVipTeleportCooldownDays() + " giorni");
                            } else {
                                player.sendMessage(ChatColor.RED + "   🔒 TELETRASPORTO RISERVATO VIP");
                                player.sendMessage(ChatColor.GRAY + "   Il teletrasporto è riservato ai possessori del PASS MENSILE+");
                                player.sendMessage(ChatColor.GRAY + "   Puoi comunque cercare le coordinate delle città!");
                            }
                        } else {
                            player.sendMessage(ChatColor.YELLOW + "   /citta tp <nome>" + ChatColor.GRAY + " - Teletrasportati alla città");
                            player.sendMessage(ChatColor.GRAY + "   Esempio: " + ChatColor.WHITE + "/citta tp roma");
                            player.sendMessage(ChatColor.GRAY + "   Esempio: " + ChatColor.WHITE + "/citta tp londra");
                        }

                        player.sendMessage(ChatColor.GRAY + "   💡 Puoi anche usare " + ChatColor.WHITE + "/citta teleport <nome>");
                        break;

                    case 3:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "📜 " + ChatColor.WHITE + "CRONOLOGIA:");
                        player.sendMessage(ChatColor.YELLOW + "   /citta history" + ChatColor.GRAY + " - Vedi i tuoi teleport passati");

                        if (finalHasVipAccess) {
                            player.sendMessage(ChatColor.GRAY + "   Mostra tutte le città visitate e quando");
                        } else if (plugin.getConfigManager().isVipTeleportSystemEnabled()) {
                            player.sendMessage(ChatColor.GRAY + "   (Sarà vuota se non hai mai fatto teleport VIP)");
                        } else {
                            player.sendMessage(ChatColor.GRAY + "   Mostra tutte le città visitate e quando");
                        }
                        break;

                    case 4:
                        player.sendMessage("");
                        player.sendMessage(ChatColor.DARK_AQUA + "📚 " + ChatColor.WHITE + "AIUTO E TUTORIAL:");
                        player.sendMessage(ChatColor.YELLOW + "   /citta tutorial" + ChatColor.GRAY + " - Rivedi questo tutorial");
                        player.sendMessage(ChatColor.YELLOW + "   /citta" + ChatColor.GRAY + " - Mostra l'aiuto rapido");
                        break;

                    case 5:
                        player.sendMessage("");

                        if (plugin.getConfigManager().isVipTeleportSystemEnabled()) {
                            player.sendMessage(ChatColor.RED + "🔒 " + ChatColor.WHITE + "SISTEMA VIP TELEPORT:");

                            if (finalHasVipAccess) {
                                player.sendMessage(ChatColor.GREEN + "   ✅ Hai accesso VIP al teletrasporto!");
                                player.sendMessage(ChatColor.GRAY + "   • Puoi teletrasportarti ogni " +
                                        ChatColor.WHITE + plugin.getConfigManager().getVipTeleportCooldownDays() + " giorni");
                                player.sendMessage(ChatColor.GRAY + "   • Il cooldown è globale (vale per tutte le città)");
                                player.sendMessage(ChatColor.GRAY + "   • Il tuo permesso: " + ChatColor.GREEN +
                                        plugin.getConfigManager().getVipTeleportPermission());
                            } else {
                                player.sendMessage(ChatColor.RED + "   ❌ Non hai accesso VIP al teletrasporto");
                                player.sendMessage(ChatColor.GRAY + "   • Solo gli utenti con PASS MENSILE+ possono teletrasportarsi");
                                player.sendMessage(ChatColor.GRAY + "   • Puoi comunque cercare tutte le città!");

                                if (plugin.getConfigManager().allowOthersSearchOnly()) {
                                    player.sendMessage(ChatColor.GREEN + "   • La ricerca è completamente gratuita per te!");
                                }
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "⏰ " + ChatColor.WHITE + "SISTEMA COOLDOWN:");
                            player.sendMessage(ChatColor.GRAY + "   • Sistema VIP disabilitato - tutti possono teletrasportarsi");
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

                            if (finalHasVipAccess) {
                                player.sendMessage(ChatColor.GRAY + "   • Teleport: " + ChatColor.WHITE + "$" +
                                        plugin.getEconomyManager().getTeleportCost());
                                player.sendMessage(ChatColor.GRAY + "   • Gratuito entro " + ChatColor.WHITE +
                                        plugin.getEconomyManager().getFreeDistance() + " blocchi");
                            } else if (plugin.getConfigManager().isVipTeleportSystemEnabled()) {
                                player.sendMessage(ChatColor.RED + "   • Teleport: Riservato PASS MENSILE+");
                            } else {
                                player.sendMessage(ChatColor.GRAY + "   • Teleport: " + ChatColor.WHITE + "$" +
                                        plugin.getEconomyManager().getTeleportCost());
                                player.sendMessage(ChatColor.GRAY + "   • Gratuito entro " + ChatColor.WHITE +
                                        plugin.getEconomyManager().getFreeDistance() + " blocchi");
                            }
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

                        if (finalHasVipAccess) {
                            player.sendMessage(ChatColor.GRAY + "   2. Poi prova " + ChatColor.YELLOW + "/citta tp roma");
                            player.sendMessage(ChatColor.GRAY + "   3. Guarda la cronologia con " + ChatColor.YELLOW + "/citta history");
                        } else if (plugin.getConfigManager().isVipTeleportSystemEnabled()) {
                            player.sendMessage(ChatColor.GRAY + "   2. " + ChatColor.RED + "Teletrasporto non disponibile (solo con PASS MENSILE+)");
                            player.sendMessage(ChatColor.GRAY + "   3. Prova altre città con " + ChatColor.YELLOW + "/citta search <nome>");
                        } else {
                            player.sendMessage(ChatColor.GRAY + "   2. Poi prova " + ChatColor.YELLOW + "/citta tp roma");
                            player.sendMessage(ChatColor.GRAY + "   3. Guarda la cronologia con " + ChatColor.YELLOW + "/citta history");
                        }

                        player.sendMessage("");
                        player.sendMessage(ChatColor.GREEN + "✅ Tutorial completato! Buona esplorazione! 🗺️");
                        player.sendMessage(ChatColor.GRAY + "Ricorda: usa " + ChatColor.YELLOW + "/citta" + ChatColor.GRAY + " per vedere i comandi disponibili");

                        // Messaggio finale personalizzato
                        if (plugin.getConfigManager().isVipTeleportSystemEnabled() && !finalHasVipAccess) {
                            player.sendMessage("");
                            player.sendMessage(ChatColor.GOLD + "🌟 " + ChatColor.YELLOW + "Per ottenere l'accesso VIP al teletrasporto,");
                            player.sendMessage(ChatColor.YELLOW + "sostieni il server acquistando il PASS MENSILE+!");
                        }

                        cancel();
                        return;
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 60L); // 3 secondi tra ogni step
    }
}