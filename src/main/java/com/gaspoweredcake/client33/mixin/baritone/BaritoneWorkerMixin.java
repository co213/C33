package com.gaspoweredcake.client33.mixin.baritone;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.ThreadPoolExecutor;

/** Cache workers may wait forever after world save; they must not keep the client JVM alive. */
@Pseudo
@Mixin(targets = "baritone.Baritone", remap = false)
public abstract class BaritoneWorkerMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "NEW", target = "java/util/concurrent/ThreadPoolExecutor"), require = 0)
    private static ThreadPoolExecutor daemonWorkers(ThreadPoolExecutor executor) {
        var factory = executor.getThreadFactory();
        executor.setThreadFactory(action -> {
            Thread thread = factory.newThread(action);
            thread.setDaemon(true);
            return thread;
        });
        return executor;
    }
}
