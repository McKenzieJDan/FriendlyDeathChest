package io.mckenz.friendlydeathchest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.Effect;

import java.util.List;
import java.util.ArrayList;

public class FriendlyDeathChest extends JavaPlugin implements Listener {
    private int searchRadius = 1;
    private String messageNoChest;
    private String messageChestCreated;
    private String messageChestRemoved;
    private String signLine1;
    private String signLine2;
    private String signLine3;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Load configuration
        loadConfig();
        
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("FriendlyDeathChest has been enabled!");
    }

    private void loadConfig() {
        // Load search radius
        searchRadius = getConfig().getInt("search-radius", 1);
        
        // Load messages
        messageNoChest = getConfig().getString("messages.no-chest-location", 
            "§c[FriendlyDeathChest] Could not create a chest. Items dropped normally.");
        messageChestCreated = getConfig().getString("messages.chest-created", 
            "§6[FriendlyDeathChest] Your items are safe in a chest at: §e%d, %d, %d");
        messageChestRemoved = getConfig().getString("messages.chest-removed", 
            "§6[FriendlyDeathChest] Chest removed as it is now empty.");
            
        // Load sign text
        signLine1 = getConfig().getString("sign.line1", "Death Chest");
        signLine2 = getConfig().getString("sign.line2", "%s");
        signLine3 = getConfig().getString("sign.line3", "Rest in peace");
    }

    @Override
    public void onDisable() {
        getLogger().info("FriendlyDeathChest has been disabled!");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        List<ItemStack> drops = new ArrayList<>(event.getDrops()); // Create a copy of the drops

        // Don't do anything if there are no items to store
        if (drops.isEmpty()) {
            return;
        }

        // Find a suitable location for the chest
        Block chestBlock = findSuitableLocation(deathLocation);
        if (chestBlock == null) {
            // If no suitable location found, let items drop normally
            player.sendMessage(messageNoChest);
            return;
        }

        // Place and fill the chest
        chestBlock.setType(Material.CHEST);
        Chest chest = (Chest) chestBlock.getState();
        
        // Add sign on top of chest with custom text
        Block signBlock = chestBlock.getRelative(0, 1, 0);
        signBlock.setType(Material.OAK_SIGN);
        if (signBlock.getState() instanceof org.bukkit.block.Sign sign) {
            sign.setLine(0, signLine1);
            sign.setLine(1, String.format(signLine2, player.getName()));
            sign.setLine(2, signLine3);
            sign.update();
        }
        
        // Clear the drops and add them to chest
        event.getDrops().clear();
        for (ItemStack item : drops) {
            if (item != null) {
                chest.getInventory().addItem(item);
            }
        }

        // Play creation effects
        Location chestLoc = chestBlock.getLocation().add(0.5, 0.5, 0.5);
        chestLoc.getWorld().spawnParticle(Particle.FLAME, chestLoc, 50, 0.5, 0.5, 0.5, 0.1);
        chestLoc.getWorld().playSound(chestLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        chestLoc.getWorld().playSound(chestLoc, Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.5f);

        // Send custom coordinates message to player
        String message = String.format(messageChestCreated,
                chestBlock.getX(), chestBlock.getY(), chestBlock.getZ());
        player.sendMessage(message);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest)) {
            return;
        }

        Chest chest = (Chest) event.getInventory().getHolder();
        Location chestLoc = chest.getLocation().add(0.5, 0.5, 0.5);

        // Play discovery effects
        chestLoc.getWorld().spawnParticle(Particle.END_ROD, chestLoc, 20, 0.2, 0.2, 0.2, 0.05);
        chestLoc.getWorld().playSound(chestLoc, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        chestLoc.getWorld().playSound(chestLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        
        if (!(inventory.getHolder() instanceof Chest)) {
            return;
        }
        
        Chest chest = (Chest) inventory.getHolder();
        
        if (inventory.isEmpty()) {
            Location chestLoc = chest.getLocation().add(0.5, 0.5, 0.5);

            // Play disappearing effects
            chestLoc.getWorld().spawnParticle(Particle.SMOKE, chestLoc, 30, 0.2, 0.2, 0.2, 0.05);
            chestLoc.getWorld().spawnParticle(Particle.PORTAL, chestLoc, 20, 0.2, 0.2, 0.2, 0.5);
            chestLoc.getWorld().playSound(chestLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);

            // Schedule the chest and sign removal
            getServer().getScheduler().runTask(this, () -> {
                // Remove sign first without dropping it
                Block signBlock = chest.getBlock().getRelative(0, 1, 0);
                if (signBlock.getType() == Material.OAK_SIGN) {
                    signBlock.setType(Material.AIR, false);
                }
                // Then remove chest without dropping it
                chest.getBlock().setType(Material.AIR, false);
                if (event.getPlayer() instanceof Player) {
                    ((Player) event.getPlayer()).sendMessage(messageChestRemoved);
                }
            });
        }
    }

    private Block findSuitableLocation(Location location) {
        Block block = location.getBlock();
        
        // Check the death location first
        if (isValidChestLocation(block)) {
            return block;
        }

        // Check nearby blocks in configurable radius
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Block relative = block.getRelative(x, y, z);
                    if (isValidChestLocation(relative)) {
                        return relative;
                    }
                }
            }
        }

        return null;
    }

    private boolean isValidChestLocation(Block block) {
        return block.getType() == Material.AIR &&
               block.getRelative(0, -1, 0).getType().isSolid() &&
               block.getRelative(0, 1, 0).getType() == Material.AIR; // Make sure there's space for the sign
    }
} 