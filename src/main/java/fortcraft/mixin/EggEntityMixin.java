package fortcraft.mixin;

import fortcraft.Fortcraft;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProjectileEntity.class)
public abstract class EggEntityMixin {

    // Egg trail
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if ((Object) this instanceof EggEntity egg && !egg.getWorld().isClient()) {
            ((ServerWorld) egg.getWorld()).spawnParticles(ParticleTypes.END_ROD, egg.getX(), egg.getY(), egg.getZ(), 1, 0, 0, 0, 0);
        }
    }

    // Place port a fort on impact
    @Inject(method = "onCollision", at = @At("HEAD"))
    private void onCollision(HitResult hitResult, CallbackInfo ci) {
        if ((Object) this instanceof EggEntity egg && !egg.getWorld().isClient() && Fortcraft.INSTANCE != null) {
            BlockPos hitPos = BlockPos.ofFloored(hitResult.getPos());
            Fortcraft.INSTANCE.spawnPortaFortAt((ServerWorld) egg.getWorld(), hitPos);
        }
    }
}