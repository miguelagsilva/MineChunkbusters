package me.vitruvis.pombachunks;

import com.massivecraft.factions.*;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PombaChunks extends JavaPlugin implements Listener {
    FileConfiguration config = this.getConfig();

    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this, this);

        // Detetar ItemsAdder
        if (getServer().getPluginManager().getPlugin("SaberFactions") != null) {
            getLogger().info("SaberFactions detetado com sucesso!");
        } else {
            getLogger().info(ChatColor.RED + "SaberFactions não detetado!");
        }

        // Config
        config.addDefault("place_commands", Arrays.asList(
                "dcwebhook direto-destruidor-chunks Um destruidor de chunks foi colocado em %block_x%/%block_y%/%block_z% por %player%"
        ));
        config.addDefault("deny_commands", Arrays.asList(
                "[MESSAGE] MALANDRAO"
        ));
        config.addDefault("block_blacklist", Arrays.asList(
                "BEDROCK"
        ));
        config.addDefault("locale.prefix", "Destruidor de chunks »");
        config.addDefault("locale.deny_placement.not_your_territory", "Not your territory!");
        config.addDefault("locale.deny_placement.not_a_faction_owner", "Not a faction owner!");
        config.addDefault("locale.deny_placement.players_in_chunk", "There are players in this chunk!");
        config.addDefault("locale.deny_placement.not_a_faction_territory", "There is no faction territory in this chunk!");
        config.addDefault("debug", false);
        config.options().copyDefaults(true);
        saveConfig();

        getLogger().info("PombaChunks ativou com sucesso!");
    }

    @EventHandler
    public void onBlockPlace (BlockPlaceEvent event) {
        if (event.getItemInHand().getItemMeta().getAsString().contains("PombaChunks")) {
            if (getConfig().getBoolean("debug")) {
                getLogger().info("Destuir de chunks colocado:\n" + event.getItemInHand().getItemMeta().getAsString());
            }

            List<String> place_commands = config.getStringList("place_commands");
            List<String> deny_commands = config.getStringList("deny_commands");
            String prefix = config.getString("locale.prefix");
            Block block = event.getBlock();
            Player player = event.getPlayer();
            Location location = block.getLocation();
            FLocation fLoc = new FLocation(location);
            FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
            Faction faction = Board.getInstance().getFactionAt(fLoc);
            Entity[] entitiesInChunk = block.getChunk().getEntities();
            List<String> allowedRoles = Arrays.asList("MODERATOR", "COLEADER", "LEADER");

            if (!player.hasPermission("pombachunks.admin.bypass")) {
                if (faction == null || faction.isWilderness()) {
                    player.sendMessage(translateHexColorCodes(config.getString("locale.deny_placement.not_a_faction_territory").replace("%prefix%", prefix)).replace("&", "§"));
                    executeCommands(deny_commands, player, block);
                    event.setCancelled(true);
                    return;
                }
                if (faction != fPlayer.getFaction()) {
                    player.sendMessage(translateHexColorCodes(config.getString("locale.deny_placement.not_your_territory").replace("%prefix%", prefix)).replace("&", "§"));
                    executeCommands(deny_commands, player, block);
                    event.setCancelled(true);
                    return;
                }
                if (!allowedRoles.contains(fPlayer.getRole().name())) {
                    player.sendMessage(translateHexColorCodes(config.getString("locale.deny_placement.not_a_faction_owner").replace("%prefix%", prefix)).replace("&", "§"));
                    executeCommands(deny_commands, player, block);
                    event.setCancelled(true);
                    return;
                }
                for (Entity entity : entitiesInChunk) {
                    if (entity instanceof Player) {
                        player.sendMessage(translateHexColorCodes(config.getString("locale.deny_placement.players_in_chunk").replace("%prefix%", prefix)).replace("&", "§"));
                        executeCommands(deny_commands, player, block);
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            executeCommands(place_commands, player, block);

            bustChunk(block, event.getPlayer());

        }
    }

    public void executeCommands(List<String> commands, Player player, Block block) {
        Location location = block.getLocation();

        for (String comando : commands) {
            comando = comando.replace("%player%", player.getName())
                    .replace("%prefix%", config.getString("locale.prefix"))
                    .replace("%block_x%", String.valueOf(location.getBlockX()))
                    .replace("%block_y%", String.valueOf(location.getBlockY()))
                    .replace("%block_z%", String.valueOf(location.getBlockZ()));
            if (comando.startsWith("[MESSAGE] ")) {
                player.sendMessage(translateHexColorCodes(comando.substring(10)).replace("&", "§"));
            } else if (comando.startsWith("[SOUND] ")) {
                Sound sound = Sound.valueOf(comando.substring(8));
                block.getWorld().playSound(location, sound, SoundCategory.BLOCKS, 1F, 1F);
            } else {
                getServer().dispatchCommand(getServer().getConsoleSender(), comando);
            }
        }
    }

    public void bustChunk(Block block, Player player) {
        if (getConfig().getBoolean("debug")) {
            getLogger().info("A destruir chunk nas coordenadas " + block.getLocation().getBlockX() + "/" + block.getLocation().getBlockY() + "/" + block.getLocation().getBlockZ() + " bloco colocado por " + player);
        }

        Chunk chunk = block.getChunk();
        World world = chunk.getWorld();
        List<String> blacklisted_blocks = config.getStringList("block_blacklist");

        for (int x = 0; x<16; x++) {
            for (int z = 0; z<16; z++) {
                for (int y = world.getMinHeight(); y<world.getMaxHeight(); y++) {
                    Block selected_block = world.getBlockAt(chunk.getBlock(x,y,z).getLocation());
                    if (!blacklisted_blocks.contains(selected_block.getType().toString())) {
                        selected_block.setType(Material.AIR);
                    }
                }
            }
        }

    }

    public String translateHexColorCodes(String message) {
        String COLOR_CHAR = "§";
        String startTag = "&#";
        String endTag = "";

        final Pattern hexPattern = Pattern.compile(startTag + "([A-Fa-f0-9]{6})" + endTag);
        Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
        while (matcher.find())
        {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, COLOR_CHAR + "x"
                    + COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
                    + COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
                    + COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5)
            );
        }
        return matcher.appendTail(buffer).toString();
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
