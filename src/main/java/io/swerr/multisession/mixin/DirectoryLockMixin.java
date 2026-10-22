package io.swerr.multisession.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import net.minecraft.util.DirectoryLock;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DirectoryLock.class)
public abstract class DirectoryLockMixin {

    @WrapMethod(method = "isLocked")
    private static boolean multisession$lockedByThisGame(Path dir, Operation<Boolean> original) {
        try {
            return original.call(dir);
        } catch (OverlappingFileLockException e) {
            return true;
        }
    }
}
