package it.kytech.skywars.commands;

import org.bukkit.entity.Player;

public interface SubCommand {

    public boolean onCommand(Player player, String[] args);

    public String help(Player p);
    
    public String permission();
    
}