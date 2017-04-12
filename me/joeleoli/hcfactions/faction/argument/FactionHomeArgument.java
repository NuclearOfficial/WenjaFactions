package me.joeleoli.hcfactions.faction.argument;

import com.doctordark.util.DurationFormatter;
import com.doctordark.util.command.CommandArgument;
import me.joeleoli.hcfactions.FactionsPlugin;
import me.joeleoli.hcfactions.faction.FactionExecutor;
import me.joeleoli.hcfactions.faction.type.Faction;
import me.joeleoli.hcfactions.faction.type.PlayerFaction;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import me.joeleoli.hcfactions.event.faction.EventFaction;
import me.joeleoli.hcfactions.timer.PlayerTimer;

import java.util.UUID;

/**
 * Faction argument used to teleport to {@link Faction} home {@link Location}s.
 */
public class FactionHomeArgument extends CommandArgument {

	private final FactionExecutor factionExecutor;
	private final FactionsPlugin plugin;

	public FactionHomeArgument(FactionExecutor factionExecutor, FactionsPlugin plugin) {
		super("home", "Teleport to the faction home.");
		this.factionExecutor = factionExecutor;
		this.plugin = plugin;
	}

	@Override
	public String getUsage(String label) {
		return '/' + label + ' ' + getName();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "This command is only executable by players.");
			return true;
		}

		Player player = (Player) sender;

		if (args.length >= 2 && args[1].equalsIgnoreCase("set")) {
			factionExecutor.getArgument("sethome").onCommand(sender, command, label, args);
			return true;
		}

		UUID uuid = player.getUniqueId();

		PlayerTimer timer = plugin.getTimerManager().getEnderPearlTimer();
		long remaining = timer.getRemaining(player);

		if (remaining > 0L) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "You cannot warp whilst your " + timer.getDisplayName() + ChatColor.RED + " timer is active [" + ChatColor.BOLD + DurationFormatter.getRemaining(remaining, true, false) + ChatColor.RED + " remaining]");

			return true;
		}

		if ((remaining = (timer = plugin.getTimerManager().getCombatTimer()).getRemaining(player)) > 0L) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "You cannot warp whilst your " + timer.getDisplayName() + ChatColor.RED + " timer is active [" + ChatColor.BOLD + DurationFormatter.getRemaining(remaining, true, false) + ChatColor.RED + " remaining]");

			return true;
		}

		PlayerFaction playerFaction = plugin.getFactionManager().getPlayerFaction(uuid);

		if (playerFaction == null) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "You are not in a faction.");
			return true;
		}

		Location home = playerFaction.getHome();

		if (home == null) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "Your faction does not have a home set.");
			return true;
		}

		Faction factionAt = plugin.getFactionManager().getFactionAt(player.getLocation());

		if (factionAt instanceof EventFaction) {
			sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "You cannot warp whilst in event zones.");
			return true;
		}

		if (factionAt != playerFaction && factionAt instanceof PlayerFaction) {
			player.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "You may not warp in enemy claims. Use " + ChatColor.YELLOW + '/' + label + " stuck" + ChatColor.RED + " if trapped.");
			return true;
		}

		long millis;
		if (factionAt.isSafezone()) {
			millis = 0L;
		} else {
			switch (player.getWorld().getEnvironment()) {
				case THE_END:
					sender.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "You cannot teleport to your faction home whilst in The End.");
					return true;
				case NETHER:
					millis = 30000L;
					break;
				default:
					millis = 10000L;
					break;
			}
		}

		if (factionAt != playerFaction && factionAt instanceof PlayerFaction) {
			millis *= 2L;
		}

		plugin.getTimerManager().getTeleportTimer().teleport(player, home, millis, FactionsPlugin.PREFIX + ChatColor.AQUA + "Teleporting to your faction home in " + ChatColor.LIGHT_PURPLE + DurationFormatter.getRemaining(millis, true, false) + ChatColor.AQUA + ". Do not move or take damage.", PlayerTeleportEvent.TeleportCause.COMMAND);

		return true;
	}
}
