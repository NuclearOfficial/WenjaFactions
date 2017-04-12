package me.joeleoli.hcfactions.listener;

import com.doctordark.util.BukkitUtils;
import com.doctordark.util.cuboid.Cuboid;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import me.joeleoli.hcfactions.ConfigurationService;
import me.joeleoli.hcfactions.FactionsPlugin;
import me.joeleoli.hcfactions.event.CaptureZone;
import me.joeleoli.hcfactions.faction.claim.Claim;
import me.joeleoli.hcfactions.faction.event.CaptureZoneEnterEvent;
import me.joeleoli.hcfactions.faction.event.CaptureZoneLeaveEvent;
import me.joeleoli.hcfactions.faction.struct.Raidable;
import me.joeleoli.hcfactions.faction.type.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.material.Cauldron;
import org.bukkit.material.MaterialData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import me.joeleoli.hcfactions.event.faction.CapturableFaction;
import me.joeleoli.hcfactions.faction.event.PlayerClaimEnterEvent;
import me.joeleoli.hcfactions.faction.struct.Role;

import javax.annotation.Nullable;

/**
 * Listener that manages protection for {@link Claim}s.
 */
public class ProtectionListener implements Listener {

	public static final String PROTECTION_BYPASS_PERMISSION = "hcf.faction.protection.bypass";

	// No such ImmutableEnumMultimap :/
	private static final ImmutableMultimap<Material, Material> ITEM_BLOCK_INTERACTABLES = ImmutableMultimap.<Material, Material>builder().put(Material.DIAMOND_HOE, Material.GRASS).put(Material.GOLD_HOE, Material.GRASS).put(Material.IRON_HOE, Material.GRASS).put(Material.STONE_HOE, Material.GRASS).put(Material.WOOD_HOE, Material.GRASS).build();

	private static final ImmutableSet<Material> BLOCK_INTERACTABLES = Sets.immutableEnumSet(Material.BED, Material.BED_BLOCK, Material.BEACON, Material.FENCE_GATE, Material.IRON_DOOR, Material.TRAP_DOOR, Material.WOOD_DOOR, Material.WOODEN_DOOR, Material.IRON_DOOR_BLOCK, Material.CHEST, Material.TRAPPED_CHEST, Material.FURNACE, Material.BURNING_FURNACE, Material.BREWING_STAND, Material.HOPPER, Material.DROPPER, Material.DISPENSER, Material.STONE_BUTTON, Material.WOOD_BUTTON, Material.ENCHANTMENT_TABLE, Material.ANVIL, Material.LEVER, Material.FIRE);

	private final FactionsPlugin plugin;

	public ProtectionListener(FactionsPlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Checks if a entity is eligible to build at a given location, if not it will send the deny
	 * message passed in the constructor.
	 * <p>
	 * <p>The deny message will be formatted using {@link String#format(String, Object...)}
	 * <p>
	 * <p>The first formatted argument is the display name of the enemy faction to the player
	 *
	 * @param entity      the entity to attempt for
	 * @param location    the location to attempt at
	 * @param denyMessage the deny message to send
	 * @return true if the player can build at location
	 */
	public static boolean attemptBuild(Entity entity, Location location, @Nullable String denyMessage) {
		return attemptBuild(entity, location, denyMessage, false);
	}

	/**
	 * Checks if a entity is eligible to build at a given location, if not it will send the deny
	 * message passed in the constructor.
	 * <p>
	 * <p>The deny message will be formatted using {@link String#format(String, Object...)}
	 * <p>
	 * <p>The first formatted argument is the display name of the enemy faction to the player
	 *
	 * @param entity        the entity to attempt for
	 * @param location      the location to attempt at
	 * @param denyMessage   the deny message to send
	 * @param isInteraction if the entity is trying to interact
	 * @return true if the player can build at location
	 */
	public static boolean attemptBuild(Entity entity, Location location, @Nullable String denyMessage, boolean isInteraction) {
		Player player = entity instanceof Player ? (Player) entity : null;

		// Allow CREATIVE players with specified permission to bypass this protection.
		if (player != null && player.getGameMode() == GameMode.CREATIVE && player.hasPermission(PROTECTION_BYPASS_PERMISSION)) {
			return true;
		}

    /*
     * if (ConfigurationService.KIT_MAP) { if (player != null) player.sendMessage(ChatColor.RED + "You cannot build during a kit map."); return false; }
     */

		if (player != null && player.getWorld().getEnvironment() == World.Environment.THE_END) {
			player.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "You may not build in the end.");
			return false;
		}

		boolean result = false;
		Faction factionAt = FactionsPlugin.getInstance().getFactionManager().getFactionAt(location);

		if (!(factionAt instanceof ClaimableFaction)) {
			result = true;
		} else if (factionAt instanceof Raidable && ((Raidable) factionAt).isRaidable()) {
			result = true;
		}

		if (player != null && factionAt instanceof PlayerFaction) {
			if (FactionsPlugin.getInstance().getFactionManager().getPlayerFaction(player) == factionAt) {
				result = true;
			}
		}

		if (result) {
			// Show this message last as the other messages look cleaner.
			if (!isInteraction && factionAt instanceof WarzoneFaction) {
				if(location.getX() > 300 || location.getZ() > 300 || location.getX() < -300 || location.getZ() < -300) {
					return true;
				}
				else {
					if (denyMessage != null && player != null) {
						player.sendMessage(FactionsPlugin.PREFIX + ChatColor.YELLOW + "You may not build in the " + factionAt.getDisplayName(player) + ChatColor.YELLOW + ".");
					}
				}

				return false;
			}
		} else if (denyMessage != null && player != null) {
			player.sendMessage(String.format(denyMessage, factionAt.getDisplayName(player)));
		}

		return result;
	}

	/**
	 * Checks if a {@link Location} is eligible to build into another {@link Location}.
	 *
	 * @param from the from {@link Location} to test
	 * @param to   the to {@link Location} to test
	 * @return true if the to {@link Faction} is the same or is not claimable
	 */
	public static boolean canBuildAt(Location from, Location to) {
		Faction toFactionAt = FactionsPlugin.getInstance().getFactionManager().getFactionAt(to);
		return !(toFactionAt instanceof Raidable && !((Raidable) toFactionAt).isRaidable() && toFactionAt != FactionsPlugin.getInstance().getFactionManager().getFactionAt(from));
	}

	private void handleMove(PlayerMoveEvent event, PlayerClaimEnterEvent.EnterCause enterCause) {
		Location from = event.getFrom();
		Location to = event.getTo();
		if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
			return;
		}

		Player player = event.getPlayer();
		boolean cancelled = false;

		Faction fromFaction = plugin.getFactionManager().getFactionAt(from);
		Faction toFaction = plugin.getFactionManager().getFactionAt(to);
		if (fromFaction != toFaction) {
			PlayerClaimEnterEvent calledEvent = new PlayerClaimEnterEvent(player, from, to, fromFaction, toFaction, enterCause);
			Bukkit.getPluginManager().callEvent(calledEvent);
			cancelled = calledEvent.isCancelled();
		} else if (toFaction instanceof CapturableFaction) {
			CapturableFaction capturableFaction = (CapturableFaction) toFaction;
			for (CaptureZone captureZone : capturableFaction.getCaptureZones()) {
				Cuboid cuboid = captureZone.getCuboid();
				if (cuboid != null) {
					boolean containsFrom = cuboid.contains(from);
					boolean containsTo = cuboid.contains(to);
					if (containsFrom && !containsTo) {
						CaptureZoneLeaveEvent calledEvent = new CaptureZoneLeaveEvent(player, capturableFaction, captureZone);
						Bukkit.getPluginManager().callEvent(calledEvent);
						cancelled = calledEvent.isCancelled();
						break;
					} else if (!containsFrom && containsTo) {
						CaptureZoneEnterEvent calledEvent = new CaptureZoneEnterEvent(player, capturableFaction, captureZone);
						Bukkit.getPluginManager().callEvent(calledEvent);
						cancelled = calledEvent.isCancelled();
						break;
					}
				}
			}
		}

		if (cancelled) {
			if (enterCause == PlayerClaimEnterEvent.EnterCause.TELEPORT) {
				event.setCancelled(true);
			} else {
				from.setX(from.getBlockX() + 0.5);
				from.setZ(from.getBlockZ() + 0.5);
				event.setTo(from);
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onPlayerMove(PlayerMoveEvent event) {
		this.handleMove(event, PlayerClaimEnterEvent.EnterCause.MOVEMENT);
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onPlayerMove(PlayerTeleportEvent event) {
		this.handleMove(event, PlayerClaimEnterEvent.EnterCause.TELEPORT);
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBlockIgnite(BlockIgniteEvent event) {
		switch (event.getCause()) {
			case FLINT_AND_STEEL:
			case ENDER_CRYSTAL:
				return;
			default:
				break;
		}

		Faction factionAt = plugin.getFactionManager().getFactionAt(event.getBlock().getLocation());
		if (factionAt instanceof ClaimableFaction && !(factionAt instanceof PlayerFaction)) {
			event.setCancelled(true);
		}
	}

	// Original source by mFactions: https://github.com/MassiveCraft/Factions/blob/dab81ede383aeb76606daf5a3c859775e1b3778/src/com/massivecraft/factions/engine/EngineExploit.java
	@EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
	public void onStickyPistonExtend(BlockPistonExtendEvent event) {
		Block block = event.getBlock();

		// Targets end-of-the-line empty (AIR) block which is being pushed into, including if piston itself would extend into air.
		Block targetBlock = block.getRelative(event.getDirection(), event.getLength() + 1);
		if (targetBlock.isEmpty() || targetBlock.isLiquid()) { // If potentially pushing into AIR/WATER/LAVA in another territory, check it out.
			Faction targetFaction = plugin.getFactionManager().getFactionAt(targetBlock.getLocation());
			if (targetFaction instanceof Raidable && !((Raidable) targetFaction).isRaidable() && targetFaction != plugin.getFactionManager().getFactionAt(block)) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
	public void onStickyPistonRetract(BlockPistonRetractEvent event) {
		if (!event.isSticky()) return; // If not a sticky piston, retraction should be fine.

		// If potentially retracted block is just AIR/WATER/LAVA, no worries
		Location retractLocation = event.getRetractLocation();
		Block retractBlock = retractLocation.getBlock();
		if (!retractBlock.isEmpty() && !retractBlock.isLiquid()) {
			Block block = event.getBlock();
			Faction targetFaction = plugin.getFactionManager().getFactionAt(retractLocation);
			if (targetFaction instanceof Raidable && !((Raidable) targetFaction).isRaidable() && targetFaction != plugin.getFactionManager().getFactionAt(block)) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBlockFromTo(BlockFromToEvent event) {
		Block toBlock = event.getToBlock();
		Block fromBlock = event.getBlock();
		if (ConfigurationService.DISABLE_OBSIDIAN_GENERATORS) {
			Material fromType = fromBlock.getType();
			Material toType = toBlock.getType();
			if ((toType == Material.REDSTONE_WIRE || toType == Material.TRIPWIRE) && (fromType == Material.AIR || fromType == Material.STATIONARY_LAVA || fromType == Material.LAVA)) {
				toBlock.setType(Material.AIR);
			}
		}

		Material fromType = fromBlock.getType();
		if (fromType == Material.WATER || fromType == Material.STATIONARY_WATER || fromType == Material.LAVA || fromType == Material.STATIONARY_LAVA) {
			if (!ProtectionListener.canBuildAt(fromBlock.getLocation(), toBlock.getLocation())) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onPlayerTeleport(PlayerTeleportEvent event) {
		if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
			Faction toFactionAt = plugin.getFactionManager().getFactionAt(event.getTo());
			if (toFactionAt.isSafezone() && !plugin.getFactionManager().getFactionAt(event.getFrom()).isSafezone()) {
				Player player = event.getPlayer();
				player.sendMessage(FactionsPlugin.PREFIX + ChatColor.RED + "You may not Enderpearl into safe-zones, used Enderpearl has been refunded.");
				plugin.getTimerManager().getEnderPearlTimer().refund(player);
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onPlayerPortal(PlayerPortalEvent event) {
		if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
			Location from = event.getFrom();
			Location to = event.getTo();
			Player player = event.getPlayer();

			Faction fromFac = plugin.getFactionManager().getFactionAt(from);
			if (fromFac.isSafezone()) { // teleport player to spawn point of target if came from safe-zone.
				event.setTo(to.getWorld().getSpawnLocation().add(0.5, 0, 0.5));
				event.useTravelAgent(false);
				player.sendMessage(FactionsPlugin.PREFIX + ChatColor.AQUA + "Teleported to spawn.");
				return;
			}

			if (event.useTravelAgent() && to.getWorld().getEnvironment() == World.Environment.NORMAL) {
				TravelAgent travelAgent = event.getPortalTravelAgent();
				if (!travelAgent.getCanCreatePortal()) return;

				Location foundPortal = travelAgent.findPortal(to);
				if (foundPortal != null) return; // there is already an exit portal, so ignore

				Faction factionAt = plugin.getFactionManager().getFactionAt(to);
				if (factionAt instanceof ClaimableFaction) {
					Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player);
					if (playerFaction != factionAt) {
						player.sendMessage(ChatColor.YELLOW + "Portal would have created portal in territory of " + factionAt.getDisplayName(player) + ChatColor.YELLOW + '.');
						event.setCancelled(true);
					}
				}
			}
		}
	}

	// Prevent mobs from spawning in the Warzone, safe-zones or claims.
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
		if (reason == CreatureSpawnEvent.SpawnReason.SLIME_SPLIT) { // allow slimes to always split
			return;
		}

		Location location = event.getLocation();
		Faction factionAt = plugin.getFactionManager().getFactionAt(location);
		if (factionAt.isSafezone() && reason == CreatureSpawnEvent.SpawnReason.SPAWNER) { // allow creatures to spawn in safe-zones by Spawner
			return;
		}

		if (factionAt instanceof ClaimableFaction && ((!(factionAt instanceof Raidable) || !((Raidable) factionAt).isRaidable())) && event.getEntity() instanceof Monster) {
			switch (reason) {
				case SPAWNER:
				case EGG:
				case CUSTOM:
				case BUILD_WITHER:
				case BUILD_IRONGOLEM:
				case BUILD_SNOWMAN:
					return;
				default:
					event.setCancelled(true);
			}
		}
	}

	// Prevents players attacking or taking damage when in safe-zone protected areas.
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onEntityDamage(EntityDamageEvent event) {
		Entity entity = event.getEntity();

		if (entity instanceof Player) {
			Player player = (Player) entity;
			Faction playerFactionAt = plugin.getFactionManager().getFactionAt(player.getLocation());
			EntityDamageEvent.DamageCause cause = event.getCause();

			if (playerFactionAt.isSafezone() && cause != EntityDamageEvent.DamageCause.SUICIDE && cause != EntityDamageEvent.DamageCause.VOID) {
				event.setCancelled(true);
			}

			Player attacker = BukkitUtils.getFinalAttacker(event, true);
			if (attacker != null) {
				Faction attackerFactionAt = plugin.getFactionManager().getFactionAt(attacker.getLocation());

				if (attackerFactionAt.isSafezone()) {
					event.setCancelled(true);
					attacker.sendMessage(ChatColor.RED + "You cannot attack players when in safe-zones.");
					return;
				} else if (playerFactionAt.isSafezone()) {
					// it's already cancelled above.
					attacker.sendMessage(ChatColor.RED + "You cannot attack players that are in safe-zones.");
					return;
				}

				PlayerFaction attackerFaction;
				PlayerFaction playerFaction = plugin.getFactionManager().getPlayerFaction(player);

				if (playerFaction != null && ((attackerFaction = plugin.getFactionManager().getPlayerFaction(attacker)) != null)) {
					Role role = playerFaction.getMember(player).getRole();
					String hiddenAstrixedName = role.getAstrix() + (player.hasPotionEffect(PotionEffectType.INVISIBILITY) ? "???" : player.getName());

					if (attackerFaction == playerFaction) {
						event.setCancelled(true);
					} else if (attackerFaction.getAllied().contains(playerFaction.getUniqueID())) {
						event.setCancelled(true);
						attacker.sendMessage(ConfigurationService.ALLY_COLOUR + hiddenAstrixedName + ChatColor.YELLOW + " is an ally.");
					}
				}
			}
		}
	}

	// Prevent players using horses that don't belong to them.
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onVehicleEnter(VehicleEnterEvent event) {
		Entity entered = event.getEntered();

		if (entered instanceof Player) {
			Vehicle vehicle = event.getVehicle();

			if (vehicle instanceof Horse) {
				Horse horse = (Horse) event.getVehicle();
				AnimalTamer owner = horse.getOwner();

				if (owner != null && !owner.equals(entered)) {
					((Player) entered).sendMessage(FactionsPlugin.PREFIX + ChatColor.YELLOW + "You cannot ride a horse that belongs to " + ChatColor.RED + owner.getName() + ChatColor.YELLOW + '.');
					event.setCancelled(true);
				}
			}
		}
	}

	// Prevents losing hunger in safe-zones.
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onFoodLevelChange(FoodLevelChangeEvent event) {
		Entity entity = event.getEntity();
		if (entity instanceof Player && ((Player) entity).getFoodLevel() > event.getFoodLevel() && plugin.getFactionManager().getFactionAt(entity.getLocation()).isSafezone()) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onPotionSplash(PotionSplashEvent event) {
		ThrownPotion potion = event.getEntity();
		if (!BukkitUtils.isDebuff(potion)) {
			return;
		}

		// Prevents potion effecting players that are in safe-zones.
		Faction factionAt = plugin.getFactionManager().getFactionAt(potion.getLocation());
		if (factionAt.isSafezone()) {
			event.setCancelled(true);
			return;
		}

		ProjectileSource source = potion.getShooter();
		if (source instanceof Player) {
			Player player = (Player) source;
			// Allow faction members to splash damage their own, PlayerFaction playerFaction = plugin.getFactionManager().getPlayerFaction(player);
			for (LivingEntity affected : event.getAffectedEntities()) {
				if (affected instanceof Player && !player.equals(affected)) {
					Player target = (Player) affected;
					if (target.equals(source)) continue; // allow the source to be affected regardless
					if (plugin.getFactionManager().getFactionAt(target.getLocation()).isSafezone() /* Nope || playerFaction.getMembers().containsKey(other.getUniqueId()) */) {
						event.setIntensity(affected, 0);
					}
				}
			}
		}
	}

	// Prevent monsters targeting players in safe-zones or their own claims.
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onEntityTarget(EntityTargetEvent event) {
		switch (event.getReason()) {
			case CLOSEST_PLAYER:
			case RANDOM_TARGET:
				Entity target = event.getTarget();
				if (event.getEntity() instanceof LivingEntity && target instanceof Player) {
					// Check LivingEntity instance, things like experience orbs might lag spam ;/
					Faction playerFaction; // lazy-load
					Faction factionAt = plugin.getFactionManager().getFactionAt(target.getLocation());
					if (factionAt.isSafezone() || ((playerFaction = plugin.getFactionManager().getPlayerFaction((Player) target)) != null && factionAt == playerFaction)) {
						event.setCancelled(true);
					}
				}
				break;
			default:
				break;
		}
	}

  /*
   * @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH) public void onBlockSpread(BlockSpreadEvent event) { Faction factionAt =
   * plugin.getFactionManager().getFactionAt(event.getBlock().getLocation()); if (factionAt instanceof ClaimableFaction && !(factionAt instanceof PlayerFaction)) { event.setCancelled(true); } }
   */

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (!event.hasBlock()) {
			return;
		}

		Block block = event.getClickedBlock();
		Action action = event.getAction();
		if (action == Action.PHYSICAL) { // Prevent players from trampling on crops or pressure plates, etc.
			if (!attemptBuild(event.getPlayer(), block.getLocation(), null)) {
				event.setCancelled(true);
			}
		} else if (action == Action.RIGHT_CLICK_BLOCK) {
			boolean canBuild = !BLOCK_INTERACTABLES.contains(block.getType());

			// Special case for cauldron
			if (canBuild) {
				Material itemType = event.hasItem() ? event.getItem().getType() : null;
				if (itemType != null && ITEM_BLOCK_INTERACTABLES.containsKey(itemType) && ITEM_BLOCK_INTERACTABLES.get(itemType).contains(event.getClickedBlock().getType())) {
					if (block.getType() != Material.WORKBENCH || !plugin.getFactionManager().getFactionAt(block).isSafezone()) {
						canBuild = false;
					}
				} else {
					MaterialData materialData = block.getState().getData();
					if (materialData instanceof Cauldron) {
						Cauldron cauldron = (Cauldron) materialData;
						if (!cauldron.isEmpty() && event.hasItem() && event.getItem().getType() == Material.GLASS_BOTTLE) {
							canBuild = false;
						}
					}
				}
			}

			if (!canBuild && !attemptBuild(event.getPlayer(), block.getLocation(), FactionsPlugin.PREFIX + ChatColor.YELLOW + "You cannot do this in the territory of %1$s" + ChatColor.YELLOW + '.', true)) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBlockBurn(BlockBurnEvent event) {
		Faction factionAt = plugin.getFactionManager().getFactionAt(event.getBlock().getLocation());
		if (factionAt instanceof WarzoneFaction || (factionAt instanceof Raidable && !((Raidable) factionAt).isRaidable())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBlockFade(BlockFadeEvent event) {
		Faction factionAt = plugin.getFactionManager().getFactionAt(event.getBlock().getLocation());
		if (factionAt instanceof ClaimableFaction && !(factionAt instanceof PlayerFaction)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onLeavesDelay(LeavesDecayEvent event) {
		Faction factionAt = plugin.getFactionManager().getFactionAt(event.getBlock().getLocation());
		if (factionAt instanceof ClaimableFaction && !(factionAt instanceof PlayerFaction)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBlockForm(BlockFormEvent event) {
		Faction factionAt = plugin.getFactionManager().getFactionAt(event.getBlock().getLocation());
		if (factionAt instanceof ClaimableFaction && !(factionAt instanceof PlayerFaction)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onEntityChangeBlock(EntityChangeBlockEvent event) {
		Entity entity = event.getEntity();
		if (entity instanceof LivingEntity && !attemptBuild(entity, event.getBlock().getLocation(), null)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockBreak(BlockBreakEvent event) {
		if (FactionsPlugin.getInstance().getFactionManager().getFactionAt(event.getBlock().getLocation()) instanceof GlowstoneMountainFaction && event.getBlock().getType().equals(Material.GLOWSTONE)) {
			event.setCancelled(false);
		} else {
			if (!attemptBuild(event.getPlayer(), event.getBlock().getLocation(), FactionsPlugin.PREFIX + ChatColor.YELLOW + "You may not build in the territory of %1$s" + ChatColor.YELLOW + '.')) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (!attemptBuild(event.getPlayer(), event.getBlockPlaced().getLocation(), FactionsPlugin.PREFIX + ChatColor.YELLOW + "You may not build in the territory of %1$s" + ChatColor.YELLOW + '.')) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBucketFill(PlayerBucketFillEvent event) {
		if (!attemptBuild(event.getPlayer(), event.getBlockClicked().getLocation(), FactionsPlugin.PREFIX + ChatColor.YELLOW + "You may not build in the territory of %1$s" + ChatColor.YELLOW + '.')) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBucketEmpty(PlayerBucketEmptyEvent event) {
		if (!attemptBuild(event.getPlayer(), event.getBlockClicked().getLocation(), FactionsPlugin.PREFIX + ChatColor.YELLOW + "You may not build in the territory of %1$s" + ChatColor.YELLOW + '.')) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
		Entity remover = event.getRemover();
		if (remover instanceof Player) {
			if (!attemptBuild(remover, event.getEntity().getLocation(), FactionsPlugin.PREFIX + ChatColor.YELLOW + "You may not build in the territory of %1$s" + ChatColor.YELLOW + '.')) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onHangingPlace(HangingPlaceEvent event) {
		if (!attemptBuild(event.getPlayer(), event.getEntity().getLocation(), FactionsPlugin.PREFIX + ChatColor.YELLOW + "You may not build in the territory of %1$s" + ChatColor.YELLOW + '.')) {
			event.setCancelled(true);
		}
	}

	// Prevents items that are in Item Frames OR hanging entities (PAINTINGS, etc) being removed.
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onHangingDamageByEntity(EntityDamageByEntityEvent event) {
		Entity entity = event.getEntity();
		if (entity instanceof Hanging) {
			Player attacker = BukkitUtils.getFinalAttacker(event, false);
			if (!attemptBuild(attacker, entity.getLocation(), FactionsPlugin.PREFIX + ChatColor.YELLOW + "You may not build in the territory of %1$s" + ChatColor.YELLOW + '.')) {
				event.setCancelled(true);
			}
		}
	}

	// Prevents items that are in Item Frames being rotated.
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void onHangingInteractByPlayer(PlayerInteractEntityEvent event) {
		Entity entity = event.getRightClicked();
		if (entity instanceof Hanging) {
			if (!attemptBuild(event.getPlayer(), entity.getLocation(), FactionsPlugin.PREFIX + ChatColor.YELLOW + "You may not build in the territory of %1$s" + ChatColor.YELLOW + '.')) {
				event.setCancelled(true);
			}
		}
	}
}
