package me.joeleoli.hcfactions.faction.argument;

import com.doctordark.util.JavaUtils;
import com.doctordark.util.command.CommandArgument;
import me.joeleoli.hcfactions.ConfigurationService;
import me.joeleoli.hcfactions.FactionsPlugin;
import me.joeleoli.hcfactions.faction.struct.Role;
import me.joeleoli.hcfactions.faction.type.PlayerFaction;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class FactionRenameArgument extends CommandArgument {

	private static final long FACTION_RENAME_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(15L);
	private static final String FACTION_RENAME_DELAY_WORDS = DurationFormatUtils.formatDurationWords(FACTION_RENAME_DELAY_MILLIS, true, true);

	private final FactionsPlugin plugin;

	public FactionRenameArgument(FactionsPlugin plugin) {
		super("rename", "Change the name of your faction.");
		this.plugin = plugin;
		this.aliases = new String[]{"changename", "setname"};
	}

	@Override
	public String getUsage(String label) {
		return '/' + label + ' ' + getName() + " <newFactionName>";
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "Only players can create faction.");
			return true;
		}

		if (args.length < 2) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "Usage: " + getUsage(label));
			return true;
		}

		Player player = (Player) sender;
		PlayerFaction playerFaction = plugin.getFactionManager().getPlayerFaction(player);

		if (playerFaction == null) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "You are not in a faction.");
			return true;
		}

		if (playerFaction.getMember(player.getUniqueId()).getRole() != Role.LEADER) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "You must be a faction leader to edit the name.");
			return true;
		}

		String newName = args[1];

		if (ConfigurationService.DISALLOWED_FACTION_NAMES.contains(newName.toLowerCase())) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "'" + newName + "' is a blocked faction name.");
			return true;
		}

		if (newName.length() < ConfigurationService.FACTION_NAME_CHARACTERS_MIN) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "Faction names must have at least " + ConfigurationService.FACTION_NAME_CHARACTERS_MIN + " characters.");
			return true;
		}

		if (newName.length() > ConfigurationService.FACTION_NAME_CHARACTERS_MAX) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "Faction names cannot be longer than " + ConfigurationService.FACTION_NAME_CHARACTERS_MAX + " characters.");
			return true;
		}

		if (!JavaUtils.isAlphanumeric(newName)) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "Faction names may only be alphanumeric.");
			return true;
		}

		if (plugin.getFactionManager().getFaction(newName) != null) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "Faction " + newName + ChatColor.RED + " already exists.");
			return true;
		}

		long difference = (playerFaction.lastRenameMillis - System.currentTimeMillis()) + FACTION_RENAME_DELAY_MILLIS;

		if (!player.isOp() && difference > 0L) {
			player.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "There is a faction rename delay of " + FACTION_RENAME_DELAY_WORDS + ". Therefore you need to wait another " + DurationFormatUtils.formatDurationWords(difference, true, true) + " to rename your faction.");

			return true;
		}

		playerFaction.setName(args[1], sender);
		return true;
	}
}
