package fr.iatest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Tous les modules au meme endroit. Commandes : /ia <module> */
public class Modules {
    public static boolean xray, reach, bridge, aura, totem, mace, crystal;
    private static int ticks = 0;

    public static boolean toggle(String n) {
        Minecraft mc = Minecraft.getInstance();
        switch (n) {
            case "xray" -> { xray = !xray; if (mc.levelRenderer != null) mc.levelRenderer.allChanged(); return xray; }
            case "reach" -> { reach = !reach; return reach; }
            case "bridge" -> { bridge = !bridge; if (!bridge) releaseKeys(mc); return bridge; }
            case "aura" -> { aura = !aura; return aura; }
            case "totem" -> { totem = !totem; return totem; }
            case "mace" -> { mace = !mace; return mace; }
            case "crystal" -> { crystal = !crystal; return crystal; }
        }
        return false;
    }

    public static void disableAll() {
        Minecraft mc = Minecraft.getInstance();
        boolean wasXray = xray;
        xray = reach = bridge = aura = totem = mace = crystal = false;
        releaseKeys(mc);
        if (wasXray && mc.levelRenderer != null) mc.levelRenderer.allChanged();
    }

    private static void releaseKeys(Minecraft mc) {
        if (mc.options == null) return;
        mc.options.keyDown.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    // ---------- XRAY (utilise par le mixin) ----------
    public static boolean isXrayBlock(BlockState s) {
        return s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.GOLD_ORES)
                || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.EMERALD_ORES)
                || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.REDSTONE_ORES)
                || s.is(Blocks.ANCIENT_DEBRIS) || s.is(Blocks.NETHER_QUARTZ_ORE) || s.is(Blocks.NETHER_GOLD_ORE)
                || s.is(Blocks.CHEST) || s.is(Blocks.SPAWNER) || s.is(Blocks.LAVA) || s.is(Blocks.WATER);
    }

    // ---------- BOUCLE PRINCIPALE ----------
    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel w = mc.level;
        if (p == null || w == null || mc.gameMode == null) return;
        ticks++;

        // REACH (cote client : un serveur vanilla re-verifie la portee)
        var range = p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (range != null) range.setBaseValue(reach ? 6.0 : 3.0);

        if (totem) autoTotem(mc, p);
        if (bridge) autoBridge(mc, p, w);
        if (crystal && ticks % 2 == 0) autoCrystal(mc, p, w);
        if (mace) autoMace(mc, p, w);
        if (aura) killAura(mc, p, w);
    }

    // ---------- helpers ----------
    private static Player nearestPlayer(LocalPlayer p, ClientLevel w, double max) {
        return w.players().stream()
                .filter(o -> o != p && o.isAlive() && !o.isSpectator())
                .filter(o -> p.distanceTo(o) <= max)
                .min(Comparator.comparingDouble(p::distanceTo)).orElse(null);
    }

    private static int findHotbar(LocalPlayer p, Predicate<ItemStack> f) {
        for (int i = 0; i < 9; i++) if (f.test(p.getInventory().getItem(i))) return i;
        return -1;
    }

    private static void attack(Minecraft mc, LocalPlayer p, Entity e) {
        mc.gameMode.attack(p, e);
        p.swing(InteractionHand.MAIN_HAND);
    }

    // ---------- 5. AUTO TOTEM ----------
    private static void autoTotem(Minecraft mc, LocalPlayer p) {
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        if (mc.screen != null) return;
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                int slot = i < 9 ? 36 + i : i; // index du slot dans le menu inventaire
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, slot, 40, ClickType.SWAP, p);
                return;
            }
        }
    }

    // ---------- 4. KILL AURA ----------
    private static void killAura(Minecraft mc, LocalPlayer p, ClientLevel w) {
        if (p.getAttackStrengthScale(0.5f) < 1.0f) return;
        Player t = nearestPlayer(p, w, reach ? 6.0 : 3.0);
        if (t != null) attack(mc, p, t);
    }

    // ---------- 6. AUTO MACE (attaque en chute) ----------
    private static void autoMace(Minecraft mc, LocalPlayer p, ClientLevel w) {
        if (p.fallDistance < 1.5 || p.onGround()) return;
        Player t = nearestPlayer(p, w, reach ? 6.0 : 3.5);
        if (t == null) return;
        int slot = findHotbar(p, s -> s.is(Items.MACE));
        if (slot < 0) return;
        p.getInventory().setSelectedSlot(slot);
        attack(mc, p, t);
    }

    // ---------- 7. AUTO CRYSTAL ----------
    private static void autoCrystal(Minecraft mc, LocalPlayer p, ClientLevel w) {
        Player t = nearestPlayer(p, w, 10);
        if (t == null) return;

        // 1) casser un cristal proche de la cible
        List<EndCrystal> crystals = w.getEntitiesOfClass(EndCrystal.class,
                p.getBoundingBox().inflate(5), c -> c.distanceTo(t) <= 6);
        if (!crystals.isEmpty()) {
            attack(mc, p, crystals.get(0));
            return;
        }
        // 2) sinon en poser un sur obsidienne/bedrock pres de la cible
        int slot = findHotbar(p, s -> s.is(Items.END_CRYSTAL));
        if (slot < 0) return;
        BlockPos best = null;
        double bestD = 1e9;
        BlockPos c = t.blockPosition();
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 0; y++) for (int z = -2; z <= 2; z++) {
            BlockPos b = c.offset(x, y, z);
            BlockState s = w.getBlockState(b);
            if (!(s.is(Blocks.OBSIDIAN) || s.is(Blocks.BEDROCK))) continue;
            if (!w.getBlockState(b.above()).isAir() || !w.getBlockState(b.above(2)).isAir()) continue;
            if (p.getEyePosition().distanceTo(Vec3.atCenterOf(b)) > 4.5) continue;
            double d = Vec3.atCenterOf(b.above()).distanceTo(t.position());
            if (d < bestD) { bestD = d; best = b; }
        }
        if (best == null) return;
        p.getInventory().setSelectedSlot(slot);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(best).add(0, 0.5, 0), Direction.UP, best, false);
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
        p.swing(InteractionHand.MAIN_HAND);
    }

    // ---------- 3. AUTO BRIDGE (version simplifiee du godbridge) ----------
    // Regarde dans la direction OPPOSEE au pont, pitch bas ; recule en sneak et pose les blocs.
    private static void autoBridge(Minecraft mc, LocalPlayer p, ClientLevel w) {
        int slot = findHotbar(p, s -> s.getItem() instanceof BlockItem);
        if (slot < 0) return;
        p.getInventory().setSelectedSlot(slot);
        p.setXRot(78f);
        mc.options.keyDown.setDown(true);
        mc.options.keyShift.setDown(true);

        Direction back = p.getDirection().getOpposite();
        BlockPos support = p.blockPosition().below();
        if (w.getBlockState(support).isAir()) return;
        BlockPos next = support.relative(back);
        if (!w.getBlockState(next).isAir()) return;
        Vec3 hitPos = Vec3.atCenterOf(support).add(back.getStepX() * 0.5, back.getStepY() * 0.5, back.getStepZ() * 0.5);
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hitPos, back, support, false));
        p.swing(InteractionHand.MAIN_HAND);
    }
}
