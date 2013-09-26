package it.kytech.skywars.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import it.kytech.skywars.GameManager;
import it.kytech.skywars.MessageManager;
import it.kytech.skywars.SettingsManager;



public class CreateArena implements SubCommand{

    public boolean onCommand(Player player, String[] args) {
        if(!player.hasPermission(permission()) && !player.isOp()){
            MessageManager.getInstance().sendFMessage(MessageManager.PrefixType.ERROR, "error.nopermission", player);
            return true;
        }
        GameManager.getInstance().createArenaFromSelection(player);
        return true;
    }

    @Override
    public String help(Player p) {
        return "/sg createarena - " + SettingsManager.getInstance().getMessageConfig().getString("messages.help.createarena", "Create a new arena with the current WorldEdit selection");
    }

	@Override
	public String permission() {
		return "sg.admin.createarena";
	}
}