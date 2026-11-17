package de.poweritems;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PowerItems extends JavaPlugin implements Listener, TabExecutor {

    private NamespacedKey drillKey;
    private NamespacedKey modelKey;
    /** Verhindert Endlosschleife, weil player.breakBlock() selbst ein BlockBreakEvent ausloest. */
    private final Set<UUID> drilling = new HashSet<>();

    @Override
    public void onEnable() {
        drillKey = new NamespacedKey(this, "power_drill");
        modelKey = new NamespacedKey("poweritems", "power_drill");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("poweritems").setExecutor(this);
        getCommand("poweritems").setTabCompleter(this);
        getLogger().info("PowerItems aktiviert.");
    }

    // ------------------------------------------------------------------ Item

    public ItemStack createDrill() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "\u26A1 Power Drill");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Baut einen " + ChatColor.YELLOW + "3x3" + ChatColor.GRAY + " Bereich ab.");
        lore.add(ChatColor.DARK_GRAY + "Schleichen = nur 1 Block");
        lore.add("");
        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "POWER ITEM");
        meta.setLore(lore);

        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Gelbe Akzente: Textur kommt aus dem mitgelieferten Resource Pack
        meta.setItemModel(modelKey);

        meta.getPersistentDataContainer().set(drillKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isDrill(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_PICKAXE || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(drillKey, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- Mining

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (drilling.contains(player.getUniqueId())) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isDrill(tool)) return;
        if (player.isSneaking()) return; // Schleichen = normaler Abbau

        Block center = event.getBlock();
        BlockFace face = getMinedFace(player, center);

        drilling.add(player.getUniqueId());
        try {
            int broken = 0;
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;

                    Block target = switch (face) {
                        case UP, DOWN -> center.getRelative(a, 0, b);
                        case NORTH, SOUTH -> center.getRelative(a, b, 0);
                        default -> center.getRelative(0, a, b); // EAST / WEST
                    };

                    if (!canDrill(target, tool)) continue;
                    if (player.breakBlock(target)) broken++;

                    // Spitzhacke kaputtgegangen?
                    if (!isDrill(player.getInventory().getItemInMainHand())) {
                        effects(center, broken);
                        return;
                    }
                }
            }
            effects(center, broken);
        } finally {
            drilling.remove(player.getUniqueId());
        }
    }

    private boolean canDrill(Block block, ItemStack tool) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) return false;
        if (type.getHardness() < 0) return false;            // Bedrock, Barrier ...
        if (block.getState() instanceof TileState) return false; // Kisten, Spawner ...
        return block.isPreferredTool(tool);                   // nur Bloecke, die eine Spitzhacke abbaut
    }

    private BlockFace getMinedFace(Player player, Block center) {
        RayTraceResult result = player.rayTraceBlocks(7.0);
        if (result != null && result.getHitBlock() != null
                && result.getHitBlock().equals(center) && result.getHitBlockFace() != null) {
            return result.getHitBlockFace();
        }
        // Fallback nach Blickrichtung
        float pitch = player.getLocation().getPitch();
        if (pitch > 60) return BlockFace.UP;
        if (pitch < -60) return BlockFace.DOWN;
        return player.getFacing().getOppositeFace();
    }

    // ---------------------------------------------------------------- Effekte

    private void effects(Block center, int extra) {
        Location loc = center.getLocation().add(0.5, 0.5, 0.5);

        // Leise Blitz-Sounds: Lautstaerke bewusst sehr niedrig, hohe Tonlage
        float pitchVar = 1.7f + (float) (Math.random() * 0.3);
        center.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.06f, pitchVar);
        center.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.10f, 2.0f);

        center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 8 + extra * 2, 0.7, 0.7, 0.7, 0.05);
    }

    // ---------------------------------------------------------------- Command

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("poweritems.give")) {
            sender.sendMessage(ChatColor.RED + "Dazu hast du keine Rechte.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give") || !args[1].equalsIgnoreCase("drill")) {
            sender.sendMessage(ChatColor.YELLOW + "Benutzung: /" + label + " give drill [spieler]");
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ChatColor.RED + "Gib einen Spieler an.");
            return true;
        }

        target.getInventory().addItem(createDrill()).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        sender.sendMessage(ChatColor.YELLOW + "\u26A1 Power Drill an " + target.getName() + " gegeben.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) out.add("give");
        else if (args.length == 2) out.add("drill");
        else if (args.length == 3) getServer().getOnlinePlayers().forEach(p -> out.add(p.getName()));
        String last = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(last));
        return out;
    }
}
