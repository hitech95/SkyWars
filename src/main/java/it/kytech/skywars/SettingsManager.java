package it.kytech.skywars;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public class SettingsManager {

    //makes the config easily accessible
    private static SettingsManager instance = new SettingsManager();
    private static Plugin p;
    private FileConfiguration spawns;
    private FileConfiguration system;
    private FileConfiguration messages;
    private FileConfiguration chest;
    private File fSpawns;
    private File fSystem;
    private File fMessages;
    private File fChest;
    private static final int MESSAGE_VERSION = 0;
    private static final int CHEST_VERSION = 0;
    private static final int SPAWN_VERSION = 0;
    private static final int SYSTEM_VERSION = 0;

    private SettingsManager() {
    }

    public static SettingsManager getInstance() {
        return instance;
    }

    public void setup(Plugin p) {
        SettingsManager.p = p;
        if (p.getConfig().getInt("config-version") == SkyWars.config_version) {
            SkyWars.config_todate = true;
        } else {
            File config = new File(p.getDataFolder(), "config.yml");
            config.delete();
        }

        p.getConfig().options().copyDefaults(true);
        p.saveDefaultConfig();

        fSpawns = new File(p.getDataFolder(), "spawns.yml");
        fSystem = new File(p.getDataFolder(), "system.yml");
        fMessages = new File(p.getDataFolder(), "messages.yml");
        fChest = new File(p.getDataFolder(), "chest.yml");

        try {
            if (!fSpawns.exists()) {
                fSpawns.createNewFile();
            }
            if (!fSystem.exists()) {
                fSystem.createNewFile();
            }
            if (!fMessages.exists()) {
                loadFile("messages.yml");
            }
            if (!fChest.exists()) {
                loadFile("chest.yml");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        reloadSystem();
        saveSystemConfig();

        reloadSpawns();
        saveSpawns();

        reloadChest();

        reloadMessages();
        saveMessages();


    }

    public void set(String arg0, Object arg1) {
        p.getConfig().set(arg0, arg1);
    }

    public FileConfiguration getConfig() {
        return p.getConfig();
    }

    public FileConfiguration getSystemConfig() {
        return system;
    }

    public FileConfiguration getSpawns() {
        return spawns;
    }

    public FileConfiguration getChest() {
        return chest;
    }

    public FileConfiguration getMessageConfig() {
        return messages;
    }

    public void saveConfig() {
        // p.saveConfig();
    }

    public static World getGameWorld(int game) {
        if (SettingsManager.getInstance().getSystemConfig().getString("sw-system.arenas." + game + ".world") == null) {
            return null;
        }
        return p.getServer().getWorld(SettingsManager.getInstance().getSystemConfig().getString("sw-system.arenas." + game + ".world"));
    }

    public void reloadConfig() {
        p.reloadConfig();
    }

    public boolean moveFile(File ff) {
        SkyWars.$("Moving outdated config file. " + fSpawns.getName());
        String name = ff.getName();
        File ff2 = new File(SkyWars.getPluginDataFolder(), getNextName(name, 0));
        return ff.renameTo(ff2);
    }

    public String getNextName(String name, int n) {
        File ff = new File(SkyWars.getPluginDataFolder(), name + ".old" + n);
        if (!ff.exists()) {
            return ff.getName();
        } else {
            return getNextName(name, n + 1);
        }
    }

    public void reloadSpawns() {
        spawns = YamlConfiguration.loadConfiguration(fSpawns);
        if (spawns.getInt("version", 0) != SPAWN_VERSION) {
            moveFile(fSpawns);
            reloadSpawns();
        }
        spawns.set("version", SPAWN_VERSION);
        saveSpawns();
    }

    public void reloadSystem() {
        system = YamlConfiguration.loadConfiguration(fSystem);
        if (system.getInt("version", 0) != SYSTEM_VERSION) {
            moveFile(fSystem);
            reloadSystem();
        }
        system.set("version", SYSTEM_VERSION);
        saveSystemConfig();
    }   

    public void reloadMessages() {
        messages = YamlConfiguration.loadConfiguration(fMessages);
        if (messages.getInt("version", 0) != MESSAGE_VERSION) {
            moveFile(fMessages);
            loadFile("messages.yml");
        }
        messages.set("version", MESSAGE_VERSION);
        saveMessages();
    }

    public void reloadChest() {
        chest = YamlConfiguration.loadConfiguration(fChest);
        if (chest.getInt("version", 0) != CHEST_VERSION) {
            moveFile(fChest);
            loadFile("chest.yml");
        }
    }

    public void saveSystemConfig() {
        try {
            system.save(fSystem);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void saveSpawns() {
        try {
            spawns.save(fSpawns);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }    

    public void saveMessages() {
        try {
            messages.save(fMessages);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void saveChest() {
        try {
            chest.save(fChest);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public int getSpawnCount(int gameid) {
        return spawns.getInt("spawns." + gameid + ".count");
    } 

    public Location getLobbySpawn() {
        try {
            return new Location(Bukkit.getWorld(system.getString("sw-system.lobby.spawn.world")),
                    system.getInt("sw-system.lobby.spawn.x"),
                    system.getInt("sw-system.lobby.spawn.y"),
                    system.getInt("sw-system.lobby.spawn.z"));
        } catch (Exception e) {
            return null;
        }
    }

    public Location getSpawnPoint(int gameid, int spawnid) {
        return new Location(getGameWorld(gameid),
                spawns.getInt("spawns." + gameid + "." + spawnid + ".x"),
                spawns.getInt("spawns." + gameid + "." + spawnid + ".y"),
                spawns.getInt("spawns." + gameid + "." + spawnid + ".z"));
    }

    public void setLobbySpawn(Location l) {
        system.set("sw-system.lobby.spawn.world", l.getWorld().getName());
        system.set("sw-system.lobby.spawn.x", l.getBlockX());
        system.set("sw-system.lobby.spawn.y", l.getBlockY());
        system.set("sw-system.lobby.spawn.z", l.getBlockZ());
    }

    public void setSpawn(int gameid, int spawnid, Vector v) {
        spawns.set("spawns." + gameid + "." + spawnid + ".x", v.getBlockX());
        spawns.set("spawns." + gameid + "." + spawnid + ".y", v.getBlockY());
        spawns.set("spawns." + gameid + "." + spawnid + ".z", v.getBlockZ());
        if (spawnid > spawns.getInt("spawns." + gameid + ".count")) {
            spawns.set("spawns." + gameid + ".count", spawnid);
        }
        try {
            spawns.save(fSpawns);
        } catch (IOException e) {
        }
        GameManager.getInstance().getGame(gameid).addSpawn();

    }

    public static String getSqlPrefix() {

        return getInstance().getConfig().getString("sql.prefix");
    }

    public void loadFile(String file) {
        File t = new File(p.getDataFolder(), file);
        System.out.println("Writing new file: " + t.getAbsolutePath());

        try {
            t.createNewFile();
            FileWriter out = new FileWriter(t);
            InputStream is = getClass().getResourceAsStream("/" + file);
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                out.write(line + "\n");
                SkyWars.debug(line);
            }
            out.flush();
            is.close();
            isr.close();
            br.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}