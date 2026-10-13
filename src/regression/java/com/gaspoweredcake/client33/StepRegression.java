package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.movement.StepAssist;
import com.gaspoweredcake.client33.utils.movement.StepHeightLease;
import com.gaspoweredcake.client33.utils.movement.StepRecovery;

public final class StepRegression {
    public static void run() {
        double[] base = {0.6};
        StepHeightLease lease = new StepHeightLease(() -> base[0], value -> base[0] = value);
        lease.apply(1.25); lease.apply(1.25); lease.restore();
        check(base[0] == 0.6, "Repeated native ticks restore the base value rather than an effective/modifier value");
        lease.apply(1.25); base[0] = 0.8; lease.restore();
        check(base[0] == 0.8, "Disable preserves a newer external/server attribute update");
        lease.apply(1.25); base[0] = 0.7; lease.apply(1.25); lease.restore();
        check(base[0] == 0.7, "Updates observed while stepping become the new restoration baseline");
        lease.restore(); check(base[0] == 0.7, "Repeated restoration is harmless");
        StepRecovery recovery = new StepRecovery(); recovery.reset(0);
        check(recovery.ready(0) && !recovery.jumpFallback(), "Activation starts a fresh movement session");
        recovery.corrected(1, true);
        check(recovery.jumpFallback() && !recovery.ready(2_000_000_000L), "Native correction falls back and pauses assistance");
        check(recovery.ready(2_000_000_001L) && recovery.jumpFallback(), "Cooldown expiry does not resume rejected native movement");
        recovery.corrected(3_000_000_000L, false);
        check(!recovery.ready(4_000_000_000L) && recovery.jumpFallback(), "Repeated corrections extend the pause without clearing fallback");
        recovery.reset(10_000_000_000L); recovery.corrected(10_000_000_000L, false);
        check(!recovery.jumpFallback(), "Ordinary server teleports while jumping do not mark native movement rejected");
        long wrap = Long.MAX_VALUE - 1_000_000_000L; recovery.reset(wrap); recovery.corrected(wrap, true);
        check(!recovery.ready(wrap + 1_000_000_000L) && recovery.ready(wrap + 2_000_000_000L), "Correction timing handles nano-time wrap");
        check(StepAssist.jumpRise(1.25, 0.6, rise -> rise >= 1) == 1, "Full blocks permit a normal jump");
        check(StepAssist.jumpRise(4, 0.6, rise -> rise >= 2) == 0, "Normal jumps never try a two-block wall");
        check(StepAssist.jumpRise(1.25, 0.6, rise -> rise >= 1 && rise < 1.25) == 0, "Blocked jump-arc headroom prevents auto-jumping");
        check(StepAssist.jumpRise(1.25, 0.6, rise -> true) == 0, "Unobstructed walking never triggers a jump");
        check(StepAssist.jumpRise(0.75, 0.6, rise -> rise >= 1) == 0, "Configured height limits are honored");
        check(StepAssist.jumpRise(1.25, 0.6, rise -> rise >= 0.5) == 0, "Slabs within ordinary native step height do not trigger an unnecessary jump");
        check(StepAssist.jumpRise(1.25, 1.25, rise -> rise >= 1) == 0, "A server-provided native step does not need a jump");
        StepAssist.Direction forward = StepAssist.direction(90, 0, 1), left = StepAssist.direction(90, 1, 0);
        check(Math.abs(forward.x() + 1) < 1e-12 && Math.abs(forward.z()) < 1e-12, "Yaw rotation follows forward input");
        check(Math.abs(left.x()) < 1e-12 && Math.abs(left.z() - 1) < 1e-12, "Strafe input has the correct sign at rotated yaw");
        StepAssist.Direction diagonal = StepAssist.direction(0, 1, 1);
        check(Math.abs(Math.hypot(diagonal.x(), diagonal.z()) - 1) < 1e-12, "Diagonal probes are normalized");
        check(!StepAssist.direction(0, 0, 0).moving() && !StepAssist.direction(Double.NaN, 1, 0).moving(), "No/invalid input cannot move probes");
        check(StepAssist.height(Double.NaN) == 0 && StepAssist.height(1e12) == 4, "Unsafe native heights are bounded");
        System.out.println("Step restoration, correction recovery and jump obstacle regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
