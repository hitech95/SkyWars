package it.kytech.skywars.util;

import org.bukkit.ChatColor;
import it.kytech.skywars.SkyWars;



public class NameUtil {

	
	public static String stylize(String name, boolean s, boolean r){

		if(SkyWars.auth.contains(name) && r){
			name = ChatColor.DARK_RED+name;
		}
		if(SkyWars.auth.contains(name) && !r){
			name = ChatColor.DARK_BLUE+name;
		}
		if(s && name.equalsIgnoreCase("Double0negative")){
			name = name.replace("Double0negative", "Double0");
		}
		return name;
	}
}
