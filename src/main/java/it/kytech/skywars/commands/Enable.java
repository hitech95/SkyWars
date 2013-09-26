package it.kytech.skywars.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import it.kytech.skywars.Game;
import it.kytech.skywars.MessageManager;
import it.kytech.skywars.Game.GameMode;
import it.kytech.skywars.MessageManager.PrefixType;
import it.kytech.skywars.GameManager;
import it.kytech.skywars.SettingsManager;



public class Enable implements SubCommand{

	@Override
	public boolean onCommand(Player player, String[] args) {        
		if(!player.hasPermission(permission()) && !player.isOp()){
			MessageManager.getInstance().sendFMessage(PrefixType.ERROR, "error.nopermission", player);
			return true;
		}
		try{
			if(args.length == 0){
				for(Game g:GameManager.getInstance().getGames()){
					if(g.getMode() == GameMode.DISABLED)
						g.enable();
				}
				MessageManager.getInstance().sendFMessage(MessageManager.PrefixType.INFO, "game.all", player, "input-enabled");
			}
			else{
				GameManager.getInstance().enableGame(Integer.parseInt(args[0]));
				MessageManager.getInstance().sendFMessage(MessageManager.PrefixType.INFO, "game.state", player, "arena-" + args[0], "input-enabled");
			}
		} catch (NumberFormatException e) {
			MessageManager.getInstance().sendFMessage(MessageManager.PrefixType.ERROR, "error.notanumber", player, "input-Arena");
		} catch (NullPointerException e) {
			MessageManager.getInstance().sendFMessage(MessageManager.PrefixType.ERROR, "error.gamedoesntexist", player, "arena-" + args[0]);
		}
		return true;

	}


	@Override
	public String help(Player p) {
		return "/sg enable <id> - " + SettingsManager.getInstance().getMessageConfig().getString("messages.help.enable", "Enables arena <id>");
	}

	@Override
	public String permission() {
		return "sg.arena.enable";
	}
}