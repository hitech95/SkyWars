package it.kytech.skywars.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import it.kytech.skywars.GameManager;
import it.kytech.skywars.MessageManager;
import it.kytech.skywars.SettingsManager;



public class Leave implements SubCommand {
	
    public boolean onCommand(Player player, String[] args) {
        if (GameManager.getInstance().getPlayerGameId(player) == -1) {
            MessageManager.getInstance().sendFMessage(MessageManager.PrefixType.ERROR, "error.notinarena", player);
        }
        else{
            GameManager.getInstance().removePlayer(player, false);
        }
        return true;
    }

    @Override
    public String help(Player p) {
        return "/sg leave - " + SettingsManager.getInstance().getMessageConfig().getString("messages.help.leave", "Leaves the game");
    }

	@Override
	public String permission() {
		return null;
	}
}
