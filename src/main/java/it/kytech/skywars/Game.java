package it.kytech.skywars;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import it.kytech.skywars.MessageManager.PrefixType;
import it.kytech.skywars.api.PlayerJoinArenaEvent;
import it.kytech.skywars.api.PlayerKilledEvent;
import it.kytech.skywars.api.PlayerLeaveArenaEvent;
import it.kytech.skywars.hooks.HookManager;
import it.kytech.skywars.logging.QueueManager;
import it.kytech.skywars.stats.StatsManager;
import it.kytech.skywars.util.ItemReader;
import java.util.Collection;
import java.util.List;
import org.bukkit.potion.PotionEffect;

//Data container for a game
public class Game {

    public static enum GameMode {

        DISABLED, LOADING, INACTIVE, WAITING,
        STARTING, INGAME, FINISHING, RESETING, ERROR
    }
    private GameMode mode = GameMode.DISABLED;
    private ArrayList< Player> activePlayers = new ArrayList< Player>();
    private ArrayList< Player> inactivePlayers = new ArrayList< Player>();
    private ArrayList< String> spectators = new ArrayList< String>();
    private ArrayList< Player> queue = new ArrayList< Player>();
    private HashMap< String, Object> flags = new HashMap< String, Object>();
    HashMap< Player, Integer> nextspec = new HashMap< Player, Integer>();
    private ArrayList<Integer> tasks = new ArrayList<Integer>();
    private Arena arena;
    private int gameID;
    private int gcount = 0;
    private FileConfiguration config;
    private FileConfiguration system;
    private HashMap< Integer, Player> spawns = new HashMap< Integer, Player>();
    private HashMap< Player, ItemStack[][]> inv_store = new HashMap< Player, ItemStack[][]>();
    private int spawnCount = 0;
    private int vote = 0;
    private boolean disabled = false;
    private int endgameTaskID = 0;
    private boolean endgameRunning = false;
    private double rbpercent = 0;
    private String rbstatus = "";
    private long startTime = 0;
    private boolean countdownRunning;
    private StatsManager sm = StatsManager.getInstance();
    private HashMap< String, String> hookvars = new HashMap< String, String>();
    private MessageManager msgmgr = MessageManager.getInstance();

    public Game(int gameid) {
        gameID = gameid;
        reloadConfig();
        setup();
    }

    public void reloadConfig() {
        config = SettingsManager.getInstance().getConfig();
        system = SettingsManager.getInstance().getSystemConfig();
    }

    public void $(String msg) {
        SkyWars.$(msg);
    }

    public void debug(String msg) {
        SkyWars.debug(msg);
    }

    public void setup() {
        System.out.println("called game setup");

        mode = GameMode.LOADING;
        int x = system.getInt("sw-system.arenas." + gameID + ".x1");
        int y = system.getInt("sw-system.arenas." + gameID + ".y1");
        int z = system.getInt("sw-system.arenas." + gameID + ".z1");
        $(x + " " + y + " " + z);
        int x1 = system.getInt("sw-system.arenas." + gameID + ".x2");
        int y1 = system.getInt("sw-system.arenas." + gameID + ".y2");
        int z1 = system.getInt("sw-system.arenas." + gameID + ".z2");
        $(x1 + " " + y1 + " " + z1);
        Location max = new Location(SettingsManager.getGameWorld(gameID), Math.max(x, x1), Math.max(y, y1), Math.max(z, z1));
        $(max.toString());
        Location min = new Location(SettingsManager.getGameWorld(gameID), Math.min(x, x1), Math.min(y, y1), Math.min(z, z1));
        $(min.toString());


        if (system.isSet("sw-system.arenas." + gameID + ".block.break")) {
            List<Integer> bBreak = system.getIntegerList("sw-system.arenas." + gameID + ".block.break");
            List<Integer> bPlace = system.getIntegerList("sw-system.arenas." + gameID + ".block.place");

            if (system.isSet("sw-system.arenas." + gameID + ".block.isWhitelist")) {
                boolean isWhitelist = system.getBoolean("sw-system.arenas." + gameID + ".block.isWhitelist");
                arena = new Arena(min, max, bBreak, bPlace, isWhitelist);
            } else {
                arena = new Arena(min, max, bBreak, bPlace);
            }
        } else {
            arena = new Arena(min, max);
        }

        loadspawns();

        hookvars.put("arena", gameID + "");
        hookvars.put("maxplayers", spawnCount + "");
        hookvars.put("activeplayers", "0");

        mode = GameMode.WAITING;
    }  

    public void loadspawns() {
        for (int a = 1; a <= SettingsManager.getInstance().getSpawnCount(gameID); a++) {
            spawns.put(a, null);
            spawnCount = a;
        }
    }

    public void addSpawn() {
        spawnCount++;
        spawns.put(spawnCount, null);
    }

    public void setMode(GameMode m) {
        mode = m;
    }

    public GameMode getGameMode() {
        return mode;
    }

    public Arena getArena() {
        return arena;
    }


    /*
     * 
     * ################################################
     * 
     * 				ENABLE
     * 
     * ################################################
     * 
     * 
     */
    public void enable() {
        mode = GameMode.WAITING;
        if (disabled) {
            MessageManager.getInstance().broadcastFMessage(PrefixType.INFO, "broadcast.gameenabled", "arena-" + gameID);
        }
        disabled = false;
        int b = (SettingsManager.getInstance().getSpawnCount(gameID) > queue.size()) ? queue.size() : SettingsManager.getInstance().getSpawnCount(gameID);
        for (int a = 0; a < b; a++) {
            addPlayer(queue.remove(0));
        }
        int c = 1;
        for (Player p : queue) {
            msgmgr.sendMessage(PrefixType.INFO, "You are now #" + c + " in line for arena " + gameID, p);
            c++;
        }

        LobbyManager.getInstance().updateWall(gameID);

        MessageManager.getInstance().broadcastFMessage(PrefixType.INFO, "broadcast.gamewaiting", "arena-" + gameID);

    }


    /*
     * 
     * ################################################
     * 
     * 				ADD PLAYER
     * 
     * ################################################
     * 
     * 
     */
    public boolean addPlayer(Player p) {
        if (SettingsManager.getInstance().getLobbySpawn() == null) {
            msgmgr.sendFMessage(PrefixType.WARNING, "error.nolobbyspawn", p);
            return false;
        }
        if (!p.hasPermission("sw.arena.join." + gameID)) {
            debug("permission needed to join arena: " + "sw.arena.join." + gameID);
            msgmgr.sendFMessage(PrefixType.WARNING, "game.nopermission", p, "arena-" + gameID);
            return false;
        }
        HookManager.getInstance().runHook("GAME_PRE_ADDPLAYER", "arena-" + gameID, "player-" + p.getName(), "maxplayers-" + spawns.size(), "players-" + activePlayers.size());

        GameManager.getInstance().removeFromOtherQueues(p, gameID);

        if (GameManager.getInstance().getPlayerGameId(p) != -1) {
            if (GameManager.getInstance().isPlayerActive(p)) {
                msgmgr.sendMessage(PrefixType.ERROR, "Cannot join multiple games!", p);
                return false;
            }
        }
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        if (spectators.contains(p)) {
            removeSpectator(p);
        }
        if (mode == GameMode.WAITING || mode == GameMode.STARTING) {
            if (activePlayers.size() < SettingsManager.getInstance().getSpawnCount(gameID)) {
                msgmgr.sendMessage(PrefixType.INFO, "Joining Arena " + gameID, p);
                PlayerJoinArenaEvent joinarena = new PlayerJoinArenaEvent(p, GameManager.getInstance().getGame(gameID));
                Bukkit.getServer().getPluginManager().callEvent(joinarena);
                if (joinarena.isCancelled()) {
                    return false;
                }
                boolean placed = false;
                int spawnCount = SettingsManager.getInstance().getSpawnCount(gameID);

                for (int a = 1; a <= spawnCount; a++) {
                    if (spawns.get(a) == null) {
                        placed = true;
                        spawns.put(a, p);
                        p.setGameMode(org.bukkit.GameMode.SURVIVAL);

                        p.teleport(SettingsManager.getInstance().getLobbySpawn());
                        saveInv(p);
                        clearInv(p);
                        p.teleport(SettingsManager.getInstance().getSpawnPoint(gameID, a));

                        p.setHealth(p.getMaxHealth());
                        p.setFoodLevel(20);
                        clearInv(p);

                        activePlayers.add(p);
                        sm.addPlayer(p, gameID);

                        hookvars.put("activeplayers", activePlayers.size() + "");
                        LobbyManager.getInstance().updateWall(gameID);
                        HookManager.getInstance().runHook("GAME_POST_ADDPLAYER", "activePlayers-" + activePlayers.size());

                        if (spawnCount == activePlayers.size()) {
                            countdown(5);
                        }
                        break;
                    }
                }
                if (!placed) {
                    msgmgr.sendFMessage(PrefixType.ERROR, "error.gamefull", p, "arena-" + gameID);
                    return false;
                }

            } else if (SettingsManager.getInstance().getSpawnCount(gameID) == 0) {
                msgmgr.sendMessage(PrefixType.WARNING, "No spawns set for Arena " + gameID + "!", p);
                return false;
            } else {
                msgmgr.sendFMessage(PrefixType.WARNING, "error.gamefull", p, "arena-" + gameID);
                return false;
            }
            msgFall(PrefixType.INFO, "game.playerjoingame", "player-" + p.getName(), "activeplayers-" + getActivePlayers(), "maxplayers-" + SettingsManager.getInstance().getSpawnCount(gameID));
            if (activePlayers.size() >= config.getInt("auto-start-players") && !countdownRunning) {
                countdown(config.getInt("auto-start-time"));
            }
            return true;
        } else {
            if (config.getBoolean("enable-player-queue")) {
                if (!queue.contains(p)) {
                    queue.add(p);
                    msgmgr.sendFMessage(PrefixType.INFO, "game.playerjoinqueue", p, "queuesize-" + queue.size());
                }
                int a = 1;
                for (Player qp : queue) {
                    if (qp == p) {
                        msgmgr.sendFMessage(PrefixType.INFO, "game.playercheckqueue", p, "queuepos-" + a);
                        break;
                    }
                    a++;
                }
            }
        }
        if (mode == GameMode.INGAME) {
            msgmgr.sendFMessage(PrefixType.WARNING, "error.alreadyingame", p);
        } else if (mode == GameMode.DISABLED) {
            msgmgr.sendFMessage(PrefixType.WARNING, "error.gamedisabled", p, "arena-" + gameID);
        } else if (mode == GameMode.RESETING) {
            msgmgr.sendFMessage(PrefixType.WARNING, "error.gamereseting", p);
        } else {
            msgmgr.sendMessage(PrefixType.INFO, "Cannot join game!", p);
        }
        LobbyManager.getInstance().updateWall(gameID);
        return false;
    }

    public void removeFromQueue(Player p) {
        queue.remove(p);
    }

    /*
     * 
     * ################################################
     * 
     * 				VOTE
     * 
     * ################################################
     * 
     * 
     */
    ArrayList< Player> voted = new ArrayList< Player>();

    public void vote(Player pl) {


        if (GameMode.STARTING == mode) {
            msgmgr.sendMessage(PrefixType.WARNING, "Game already starting!", pl);
            return;
        }
        if (GameMode.WAITING != mode) {
            msgmgr.sendMessage(PrefixType.WARNING, "Game already started!", pl);
            return;
        }
        if (voted.contains(pl)) {
            msgmgr.sendMessage(PrefixType.WARNING, "You already voted!", pl);
            return;
        }
        vote++;
        voted.add(pl);
        msgmgr.sendFMessage(PrefixType.INFO, "game.playervote", pl, "player-" + pl.getName());
        HookManager.getInstance().runHook("PLAYER_VOTE", "player-" + pl.getName());
        
        if ((((vote + 0.0) / (getActivePlayers() + 0.0)) >= (config.getInt("auto-start-vote") + 0.0) / 100) && getActivePlayers() > 1) {
            countdown(config.getInt("auto-start-time"));
            for (Player p : activePlayers) {                
                msgmgr.sendMessage(PrefixType.INFO, "Game starting in " + config.getInt("auto-start-time") + "!", p);
            }
        }
    }

    /*
     * 
     * ################################################
     * 
     * 				START GAME
     * 
     * ################################################
     * 
     * 
     */
    public void startGame() {
        if (mode == GameMode.INGAME) {
            return;
        }

        if (activePlayers.size() <= 1) {
            for (Player pl : activePlayers) {
                msgmgr.sendMessage(PrefixType.WARNING, "Not enough players!", pl);
                mode = GameMode.WAITING;
                LobbyManager.getInstance().updateWall(gameID);

            }
            return;
        } else {
            startTime = new Date().getTime();
            for (Player pl : activePlayers) {
                pl.setHealth(pl.getMaxHealth());
                msgmgr.sendFMessage(PrefixType.INFO, "game.goodluck", pl);
            }
        }

        mode = GameMode.INGAME;
        LobbyManager.getInstance().updateWall(gameID);
        MessageManager.getInstance().broadcastFMessage(PrefixType.INFO, "broadcast.gamestarted", "arena-" + gameID);

    }
    /*
     * 
     * ################################################
     * 
     * 				COUNTDOWN
     * 
     * ################################################
     * 
     * 
     */

    public int getCountdownTime() {
        return count;
    }
    int count = 20;
    int tid = 0;

    public void countdown(int time) {
        MessageManager.getInstance().broadcastFMessage(PrefixType.INFO, "broadcast.gamestarting", "arena-" + gameID, "t-" + time);
        countdownRunning = true;
        count = time;
        Bukkit.getScheduler().cancelTask(tid);

        if (mode == GameMode.WAITING || mode == GameMode.STARTING) {
            mode = GameMode.STARTING;
            tid = Bukkit.getScheduler().scheduleSyncRepeatingTask((Plugin) GameManager.getInstance().getPlugin(), new Runnable() {
                public void run() {
                    if (count > 0) {
                        if (count % 10 == 0) {
                            msgFall(PrefixType.INFO, "game.countdown", "t-" + count);
                        }
                        if (count < 6) {
                            msgFall(PrefixType.INFO, "game.countdown", "t-" + count);

                        }
                        count--;
                        LobbyManager.getInstance().updateWall(gameID);
                    } else {
                        startGame();
                        Bukkit.getScheduler().cancelTask(tid);
                        countdownRunning = false;
                    }
                }
            }, 0, 20);

        }
    }

    /*
     * 
     * ################################################
     * 
     * 				REMOVE PLAYER
     * 
     * ################################################
     * 
     * 
     */
    public void removePlayer(Player p, boolean b) {
        p.teleport(SettingsManager.getInstance().getLobbySpawn());
        if (mode == GameMode.INGAME) {
            killPlayer(p, b);
        } else {
            sm.removePlayer(p, gameID);
            restoreInv(p);
            activePlayers.remove(p);
            inactivePlayers.remove(p);
            for (Object in : spawns.keySet().toArray()) {
                if (spawns.get(in) == p) {
                    spawns.remove(in);
                }
            }
            LobbyManager.getInstance().clearSigns(gameID);
        }

        HookManager.getInstance().runHook("PLAYER_REMOVED", "player-" + p.getName());

        PlayerLeaveArenaEvent pl = new PlayerLeaveArenaEvent(p, this, b);

        LobbyManager.getInstance().updateWall(gameID);
    }

    public void playerLeave(Player p) {
    }

    /*
     * 
     * ################################################
     * 
     * 				KILL PLAYER
     * 
     * ################################################
     * 
     * 
     */
    public void killPlayer(Player p, boolean left) {
        clearInv(p);
        if (!left) {
            p.teleport(SettingsManager.getInstance().getLobbySpawn());
        }
        sm.playerDied(p, activePlayers.size(), gameID, new Date().getTime() - startTime);

        if (!activePlayers.contains(p)) {
            return;
        } else {
            restoreInv(p);
        }

        activePlayers.remove(p);
        inactivePlayers.add(p);
        PlayerKilledEvent pk = null;
        if (left) {
            msgFall(PrefixType.INFO, "game.playerleavegame", "player-" + p.getName());
        } else {
            if (mode != GameMode.WAITING && p.getLastDamageCause() != null && p.getLastDamageCause().getCause() != null) {
                switch (p.getLastDamageCause().getCause()) {
                    case ENTITY_ATTACK:
                        if (p.getLastDamageCause().getEntityType() == EntityType.PLAYER) {
                            Player killer = p.getKiller();
                            msgFall(PrefixType.INFO, "death." + p.getLastDamageCause().getEntityType(),
                                    "player-" + (SkyWars.auth.contains(p.getName()) ? ChatColor.DARK_RED + "" + ChatColor.BOLD : "") + p.getName(),
                                    "killer-" + ((killer != null) ? (SkyWars.auth.contains(killer.getName()) ? ChatColor.DARK_RED + "" + ChatColor.BOLD : "")
                                    + killer.getName() : "Unknown"),
                                    "item-" + ((killer != null) ? ItemReader.getFriendlyItemName(killer.getItemInHand().getType()) : "Unknown Item"));
                            if (killer != null && p != null) {
                                sm.addKill(killer, p, gameID);
                            }
                            pk = new PlayerKilledEvent(p, this, killer, p.getLastDamageCause().getCause());
                        } else {
                            msgFall(PrefixType.INFO, "death." + p.getLastDamageCause().getEntityType(), "player-"
                                    + (SkyWars.auth.contains(p.getName()) ? ChatColor.DARK_RED + "" + ChatColor.BOLD : "")
                                    + p.getName(), "killer-" + p.getLastDamageCause().getEntityType());
                            pk = new PlayerKilledEvent(p, this, null, p.getLastDamageCause().getCause());

                        }
                        break;
                    default:
                        msgFall(PrefixType.INFO, "death." + p.getLastDamageCause().getCause(),
                                "player-" + (SkyWars.auth.contains(p.getName()) ? ChatColor.DARK_RED + "" + ChatColor.BOLD : "") + p.getName(),
                                "killer-" + p.getLastDamageCause().getCause());
                        pk = new PlayerKilledEvent(p, this, null, p.getLastDamageCause().getCause());

                        break;
                }
                Bukkit.getServer().getPluginManager().callEvent(pk);

                if (getActivePlayers() > 1) {
                    for (Player pl : getAllPlayers()) {
                        msgmgr.sendMessage(PrefixType.INFO, ChatColor.DARK_AQUA + "There are " + ChatColor.YELLOW + ""
                                + getActivePlayers() + ChatColor.DARK_AQUA + " players remaining!", pl);
                    }
                }
            }
        }
        
        if (activePlayers.size() < 2 && mode != GameMode.WAITING) {
            playerWin(p);
            endGame();
        }
        LobbyManager.getInstance().updateWall(gameID);
    }

    /*
     * 
     * ################################################
     * 
     * 				PLAYER WIN
     * 
     * ################################################
     * 
     * 
     */
    public void playerWin(Player p) {
        if (GameMode.DISABLED == mode) {
            return;
        }
        Player win = activePlayers.get(0);
        win.teleport(SettingsManager.getInstance().getLobbySpawn());
        restoreInv(win);
        msgmgr.broadcastFMessage(PrefixType.INFO, "game.playerwin", "arena-" + gameID, "victim-" + p.getName(), "player-" + win.getName());
        LobbyManager.getInstance().display(new String[]{
            win.getName(), "", "Won the ", "Sky War!"
        }, gameID);

        mode = GameMode.FINISHING;

        clearSpecs();
        win.setHealth(win.getMaxHealth());
        win.setFoodLevel(20);
        win.setFireTicks(0);
        win.setFallDistance(0);

        Collection<PotionEffect> activePotionEffects = win.getActivePotionEffects();
        for (PotionEffect i : activePotionEffects) {
            win.removePotionEffect(i.getType());
        }

        sm.playerWin(win, gameID, new Date().getTime() - startTime);
        sm.saveGame(gameID, win, getActivePlayers() + getInactivePlayers(), new Date().getTime() - startTime);

        activePlayers.clear();
        inactivePlayers.clear();
        spawns.clear();

        loadspawns();
        LobbyManager.getInstance().updateWall(gameID);
        MessageManager.getInstance().broadcastFMessage(PrefixType.INFO, "broadcast.gameend", "arena-" + gameID);

    }

    public void endGame() {
        mode = GameMode.WAITING;
        resetArena();
        LobbyManager.getInstance().clearSigns(gameID);
        LobbyManager.getInstance().updateWall(gameID);

    }
    /*
     * 
     * ################################################
     * 
     * 				DISABLE
     * 
     * ################################################
     * 
     * 
     */

    public void disable() {
        disabled = true;
        spawns.clear();

        for (int a = 0; a < activePlayers.size(); a = 0) {
            try {

                Player p = activePlayers.get(a);
                msgmgr.sendMessage(PrefixType.WARNING, "Game disabled!", p);
                removePlayer(p, false);
            } catch (Exception e) {
            }

        }

        for (int a = 0; a < inactivePlayers.size(); a = 0) {
            try {

                Player p = inactivePlayers.remove(a);
                msgmgr.sendMessage(PrefixType.WARNING, "Game disabled!", p);
            } catch (Exception e) {
            }

        }

        clearSpecs();
        queue.clear();

        endGame();
        LobbyManager.getInstance().updateWall(gameID);
        MessageManager.getInstance().broadcastFMessage(PrefixType.INFO, "broadcast.gamedisabled", "arena-" + gameID);

    }
    /*
     * 
     * ################################################
     * 
     * 				RESET
     * 
     * ################################################
     * 
     * 
     */

    public void resetArena() {

        for (Integer i : tasks) {
            Bukkit.getScheduler().cancelTask(i);
        }

        tasks.clear();
        vote = 0;
        voted.clear();

        mode = GameMode.RESETING;
        endgameRunning = false;

        Bukkit.getScheduler().cancelTask(endgameTaskID);
        GameManager.getInstance().gameEndCallBack(gameID);
        QueueManager.getInstance().rollback(gameID, false);
        LobbyManager.getInstance().updateWall(gameID);

    }

    public void resetCallback() {
        if (!disabled) {
            enable();
        } else {
            mode = GameMode.DISABLED;
        }
        LobbyManager.getInstance().updateWall(gameID);
    }

    public void saveInv(Player p) {
        ItemStack[][] store = new ItemStack[2][1];

        store[0] = p.getInventory().getContents();
        store[1] = p.getInventory().getArmorContents();

        inv_store.put(p, store);

    }

    public void restoreInvOffline(String p) {
        restoreInv(Bukkit.getPlayer(p));
    }


    /*
     * 
     * ################################################
     * 
     * 				SPECTATOR
     * 
     * ################################################
     * 
     * 
     */
    public void addSpectator(Player p) {
        if (mode != GameMode.INGAME) {
            msgmgr.sendMessage(PrefixType.WARNING, "You can only spectate running games!", p);
            return;
        }

        saveInv(p);
        clearInv(p);
        p.teleport(SettingsManager.getInstance().getSpawnPoint(gameID, 1).add(0, 10, 0));

        HookManager.getInstance().runHook("PLAYER_SPECTATE", "player-" + p.getName());

        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.hidePlayer(p);
        }

        p.setAllowFlight(true);
        p.setFlying(true);
        spectators.add(p.getName());
        msgmgr.sendMessage(PrefixType.INFO, "You are now spectating! Use /sw spectate again to return to the lobby.", p);
        msgmgr.sendMessage(PrefixType.INFO, "Right click while holding shift to teleport to the next ingame player, left click to go back.", p);
        nextspec.put(p, 0);
    }

    public void removeSpectator(Player p) {
        ArrayList< Player> players = new ArrayList< Player>();
        players.addAll(activePlayers);
        players.addAll(inactivePlayers);

        if (p.isOnline()) {
            for (Player pl : Bukkit.getOnlinePlayers()) {
                pl.showPlayer(p);
            }
        }
        restoreInv(p);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setFallDistance(0);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.teleport(SettingsManager.getInstance().getLobbySpawn());
        // Bukkit.getServer().broadcastPrefixType("Removing Spec "+p.getName()+" "+spectators.size()+" left");
        spectators.remove(p.getName());
        // Bukkit.getServer().broadcastPrefixType("Removed");

        nextspec.remove(p);
    }

    public void clearSpecs() {

        for (int a = 0; a < spectators.size(); a = 0) {
            removeSpectator(Bukkit.getPlayerExact(spectators.get(0)));
        }
        spectators.clear();
        nextspec.clear();
    }

    public HashMap< Player, Integer> getNextSpec() {
        return nextspec;
    }

    @SuppressWarnings("deprecation")
    public void restoreInv(Player p) {
        try {
            clearInv(p);
            p.getInventory().setContents(inv_store.get(p)[0]);
            p.getInventory().setArmorContents(inv_store.get(p)[1]);
            inv_store.remove(p);
            p.updateInventory();
        } catch (Exception e) { /*p.sendMessage(ChatColor.RED+"Inentory failed to restore or nothing was in it.");*/

        }
    }

    @SuppressWarnings("deprecation")
    public void clearInv(Player p) {
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            inv[i] = null;
        }
        p.getInventory().setContents(inv);
        inv = p.getInventory().getArmorContents();
        for (int i = 0; i < inv.length; i++) {
            inv[i] = null;
        }
        p.getInventory().setArmorContents(inv);
        p.updateInventory();

    }

    public boolean isBlockInArena(Location v) {
        return arena.containsBlock(v);
    }

    public int getID() {
        return gameID;
    }

    public int getActivePlayers() {
        return activePlayers.size();
    }

    public int getInactivePlayers() {
        return inactivePlayers.size();
    }

    public Player[][] getPlayers() {
        return new Player[][]{
            activePlayers.toArray(new Player[0]), inactivePlayers.toArray(new Player[0])
        };
    }

    public ArrayList< Player> getAllPlayers() {
        ArrayList< Player> all = new ArrayList< Player>();
        all.addAll(activePlayers);
        all.addAll(inactivePlayers);
        return all;
    }

    public boolean isSpectator(Player p) {
        return spectators.contains(p.getName());
    }

    public boolean isInQueue(Player p) {
        return queue.contains(p);
    }

    public boolean isPlayerActive(Player player) {
        return activePlayers.contains(player);
    }

    public boolean isPlayerinactive(Player player) {
        return inactivePlayers.contains(player);
    }

    public boolean hasPlayer(Player p) {
        return activePlayers.contains(p) || inactivePlayers.contains(p);
    }

    public GameMode getMode() {
        return mode;
    }

    public synchronized void setRBPercent(double d) {
        rbpercent = d;
    }

    public double getRBPercent() {
        return rbpercent;
    }

    public void setRBStatus(String s) {
        rbstatus = s;
    }

    public String getRBStatus() {
        return rbstatus;
    }

    public String getName() {
        return "Arena " + gameID;
    }

    public void msgFall(PrefixType type, String msg, String... vars) {
        for (Player p : getAllPlayers()) {
            msgmgr.sendFMessage(type, msg, p, vars);
        }
    }
}