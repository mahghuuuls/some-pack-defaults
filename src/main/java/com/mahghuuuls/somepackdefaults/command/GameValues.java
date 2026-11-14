package com.mahghuuuls.somepackdefaults.command;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.report.InspectionLines;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Current values read without side effects (ARC-007). File values come from reading
 * {@code server.properties} directly. Running values come only from getters that return a field:
 * several of the dedicated server's getters read the settings file through a method that writes a
 * missing line back, so those lines show only their file value.
 */
final class GameValues implements InspectionLines.CurrentValues {

    private final MinecraftServer server;
    private final Path serverProperties;
    private Properties fileValues;
    private boolean fileRead;

    GameValues(MinecraftServer server) {
        this(server, Paths.get("server.properties"));
    }

    GameValues(MinecraftServer server, Path serverProperties) {
        this.server = server;
        this.serverProperties = serverProperties;
    }

    @Override
    public boolean singleplayer() {
        return !server.isDedicatedServer();
    }

    @Override
    public String fileValue(Entry serverLine) {
        if (!fileRead) {
            fileRead = true;
            fileValues = readOnly(serverProperties);
        }
        return fileValues == null ? null : fileValues.getProperty(serverLine.name());
    }

    /** The file's lines, or null when it is missing or cannot be read; never written. */
    static Properties readOnly(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(Files.readAllBytes(path)));
            return properties;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String runningValue(Entry entry) {
        WorldServer overworld = server.getWorld(0);
        if (entry.category() == SettingCategory.GAMERULE) {
            GameRules rules = overworld.getGameRules();
            return rules.hasRule(entry.name()) ? rules.getString(entry.name()) : null;
        }
        if (singleplayer()) {
            return null;
        }
        switch (entry.name()) {
            case "motd":
                return server.getMOTD();
            case "pvp":
                return String.valueOf(server.isPVPEnabled());
            case "allow-flight":
                return String.valueOf(server.isFlightAllowed());
            case "online-mode":
                return String.valueOf(server.isServerInOnlineMode());
            case "prevent-proxy-connections":
                return String.valueOf(server.getPreventProxyConnections());
            case "spawn-animals":
                return String.valueOf(server.getCanSpawnAnimals());
            case "spawn-npcs":
                return String.valueOf(server.getCanSpawnNPCs());
            case "max-build-height":
                return String.valueOf(server.getBuildLimit());
            case "force-gamemode":
                return String.valueOf(server.getForceGamemode());
            case "player-idle-timeout":
                return String.valueOf(server.getMaxPlayerIdleMinutes());
            case "resource-pack":
                return server.getResourcePackUrl();
            case "max-players":
                return String.valueOf(server.getPlayerList().getMaxPlayers());
            case "view-distance":
                return String.valueOf(server.getPlayerList().getViewDistance());
            case "white-list":
                return String.valueOf(server.getPlayerList().isWhiteListEnabled());
            case "generate-structures":
                return String.valueOf(server.canStructuresSpawn());
            case "gamemode":
                return String.valueOf(server.getGameType().getID());
            case "difficulty":
                return String.valueOf(overworld.getDifficulty().getId());
            case "hardcore":
                return String.valueOf(overworld.getWorldInfo().isHardcoreModeEnabled());
            case "level-name":
                return server.getFolderName();
            case "level-type":
                // The game matches world type names ignoring case; show the file's spelling when it is the same type.
                String type = overworld.getWorldInfo().getTerrainType().getName();
                String file = fileValue(entry);
                return file != null && file.equalsIgnoreCase(type) ? file : type;
            default:
                return null;
        }
    }
}
