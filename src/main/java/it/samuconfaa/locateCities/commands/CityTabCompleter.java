package it.samuconfaa.locateCities.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CityTabCompleter implements TabCompleter {

    // Città più popolari per suggerimenti
    private static final List<String> POPULAR_CITIES = Arrays.asList(
            // Italia
            "Roma", "Milano", "Napoli", "Torino", "Palermo", "Genova", "Bologna",
            "Firenze", "Bari", "Catania", "Venezia", "Verona", "Messina", "Padova",
            "Trieste", "Brescia", "Taranto", "Prato", "Parma", "Reggio Calabria",

            // Mondo
            "Londra", "Parigi", "Madrid", "Berlino", "Amsterdam", "Bruxelles",
            "Vienna", "Praga", "Budapest", "Varsavia", "Stoccolma", "Oslo",
            "New York", "Los Angeles", "Chicago", "Miami", "Toronto", "Vancouver",
            "Tokyo", "Osaka", "Seoul", "Shanghai", "Pechino", "Mumbai", "Delhi",
            "Sydney", "Melbourne", "Auckland", "San Paolo", "Rio de Janeiro",
            "Buenos Aires", "Città del Messico", "Il Cairo", "Dubai", "Istanbul"
    );

    // Sottcomandi disponibili
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "search", "tp", "teleport", "history", "tutorial"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Suggerimenti per i sottcomandi
            String partial = args[0].toLowerCase();
            completions = SUBCOMMANDS.stream()
                    .filter(subcommand -> subcommand.startsWith(partial))
                    .collect(Collectors.toList());

        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            // Solo per search, tp e teleport mostriamo suggerimenti città
            if (subCommand.equals("search") || subCommand.equals("tp") || subCommand.equals("teleport")) {
                String partial = args[1].toLowerCase();
                completions = POPULAR_CITIES.stream()
                        .filter(city -> city.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}