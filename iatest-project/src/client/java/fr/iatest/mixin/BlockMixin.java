package fr.iatest.mixin;

import fr.iatest.Modules;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
    // Xray : on ne dessine que les faces des minerais
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void iatest$xray(BlockState state, BlockState neighbor, Direction dir,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (Modules.xray) cir.setReturnValue(Modules.isXrayBlock(state));
    }
}
