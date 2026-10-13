package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.world.RtpSearchCycle;
import java.util.concurrent.TimeUnit;
import com.gaspoweredcake.client33.utils.misc.ModuleLifecycle;

public final class RtpRegression {
    public static void run() {
        long second = TimeUnit.SECONDS.toNanos(1);
        RtpSearchCycle cycle = new RtpSearchCycle();
        cycle.reset(0, 10 * second);
        check(!cycle.ready(9 * second, true, false), "Activation must wait for chunk loading");
        check(!cycle.ready(10 * second, false, false), "An unfinished scan must block RTP");
        check(!cycle.ready(10 * second, true, true), "An open screen must block commands");
        check(cycle.ready(10 * second, true, false), "A settled, completely scanned area permits RTP");
        cycle.sent(10 * second, 30 * second, 10 * second);
        check(!cycle.ready(20 * second, true, false), "Loading wait cannot bypass server cooldown");
        cycle.locationChanged(25 * second, 10 * second);
        check(!cycle.ready(35 * second, true, false), "Teleport must retain the previous command cooldown");
        check(cycle.ready(40 * second, true, false), "Cooldown expires on the exact real-time boundary");
        cycle.locationChanged(39 * second, 10 * second);
        check(!cycle.ready(40 * second, true, false), "Late teleports must extend the chunk loading wait");
        check(cycle.ready(49 * second, true, false), "A delayed teleport can be scanned before retrying");
        cycle.reset(100 * second, second);
        check(cycle.ready(101 * second, true, false), "Reactivation discards the old session cooldown");
        ModuleLifecycle[] lifecycle = new ModuleLifecycle[1];
        int[] starts = {0};
        lifecycle[0] = new ModuleLifecycle(() -> starts[0]++, () -> {
            if (lifecycle[0].enabled()) lifecycle[0].request(false, false);
        }, enabled -> {}, failure -> { throw failure; });
        lifecycle[0].request(true, true);
        lifecycle[0].availability(false);
        lifecycle[0].availability(true);
        check(!lifecycle[0].enabled() && starts[0] == 1, "Disconnect must discard enabled intent instead of resuming RTP on reconnect");
        long nearWrap = Long.MAX_VALUE - second;
        cycle.reset(nearWrap, 2 * second);
        check(!cycle.ready(nearWrap + second, true, false), "Nano-time wrap must not expire waits early");
        check(cycle.ready(nearWrap + 2 * second, true, false), "Nano-time wrap must still permit expiry");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
