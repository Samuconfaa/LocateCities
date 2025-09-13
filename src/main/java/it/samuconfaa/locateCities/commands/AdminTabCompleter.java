package it.samuconfaa.locateCities.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AdminTabCompleter implements TabCompleter {

    // Sottcomandi admin disponibili
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
            "reload", "clearcache", "info", "setorigin", "setscale",
            "stats", "near", "playerhistory", "cleandb", "dbstats", "bypass"
    );

    // Città popolari per il comando near
    private static final List<String> POPULAR_CITIES = Arrays.asList(
            "Roma", "Milano", "Napoli", "Torino", "Palermo", "Genova", "Bologna",
            "Firenze", "Bari", "Catania", "Venezia", "Verona", "Messina", "Padova",
            "Londra", "Parigi", "Madrid", "Berlino", "Amsterdam", "New York", "Tokyo"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Suggerimenti per i sottcomandi
            String partial = args[0].toLowerCase();
            completions = ADMIN_SUBCOMMANDS.stream()
                    .filter(subcommand -> subcommand.startsWith(partial))
                    .collect(Collectors.toList());

        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "setorigin":
                    // Suggerimento per latitudine
                    completions.add("41.9028"); // Roma esempio
                    completions.add("45.4642"); // Milano esempio
                    break;

                case "setscale":
                    // Suggerimenti per scale comuni
                    completions.addAll(Arrays.asList("100", "500", "1000", "5000", "10000"));
                    break;

                case "near":
                    // Suggerimenti città per comando near
                    String partial = args[1].toLowerCase();
                    completions = POPULAR_CITIES.stream()
                            .filter(city -> city.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());
                    break;

                case "playerhistory":
                    // Nomi dei giocatori online
                    completions = Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                    break;

                case "cleandb":
                    // Suggerimenti per giorni comuni
                    completions.addAll(Arrays.asList("30", "60", "90", "180", "365"));
                    break;

                case "bypass":
                    // Nomi dei giocatori per bypass
                    completions = Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                    break;
            }

        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "setorigin":
                    // Suggerimento per longitudine
                    completions.add("12.4964"); // Roma esempio
                    completions.add("9.1900");  // Milano esempio
                    break;

                case "bypass":
                    // Suggerimenti città per bypass
                    String partial = args[2].toLowerCase();
                    completions = POPULAR_CITIES.stream()
                            .filter(city -> city.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());
                    break;
            }
        }

        return completions;
    }
}