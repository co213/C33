package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.render.PathViewMath;
import com.gaspoweredcake.client33.utils.combat.TargetSelection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class WatchRegression {
    public static void run() {
        check(PathViewMath.start(0, 100) == 0 && PathViewMath.start(50, 100) == 49, "Path window includes one previous point without negative indexing");
        check(PathViewMath.start(Integer.MAX_VALUE, 10) == 9 && PathViewMath.start(Integer.MIN_VALUE, 10) == 0, "Stale/extreme executor positions cannot overflow slicing");
        check(PathViewMath.end(49, 1000, Integer.MAX_VALUE) == 305, "Snapshot allocation is capped at 256 nodes");
        check(PathViewMath.end(0, 0, 100) == 0 && PathViewMath.end(9, 10, 2) == 10, "Empty and terminal paths have valid slice bounds");
        check(PathViewMath.progress(0, 0) == 0 && PathViewMath.progress(50, 101) == 0.5 && PathViewMath.progress(999, 10) == 1, "Progress describes the current segment and remains bounded");
        double seam = PathViewMath.angle(179, -179, 0.05, 4);
        check(Math.abs(PathViewMath.wrap(seam - 179)) < 2, "Watch camera crosses the yaw seam along the shortest arc");
        double at60 = 0, at144 = 0;
        for (int i = 0; i < 60; i++) at60 = PathViewMath.angle(at60, 90, 1.0 / 60, 4);
        for (int i = 0; i < 144; i++) at144 = PathViewMath.angle(at144, 90, 1.0 / 144, 4);
        check(Math.abs(at60 - at144) < 1e-9, "Watch camera response is independent of display refresh rate");
        check(PathViewMath.angle(10, 90, 0, 4) == 10 && PathViewMath.angle(10, Double.NaN, 0.1, 4) == 10, "Paused/invalid samples cannot change the watch camera");
        for (int i = -100; i < 100; i++) {
            double pulse = PathViewMath.pulse(i, i / 3.0);
            check(pulse >= 0 && pulse <= 1, "Traveling light remains bounded");
        }
        double pitch = PathViewMath.pitch(-90, 90, 0.1, 14);
        check(pitch > -90 && pitch < 90, "Pitch easing never wraps across the vertical poles");
        check(PathViewMath.pitch(0, 1000, 0.1, 14) <= 90, "Rendered pitch is bounded");
        Random random = new Random(33);
        record Candidate(int id, int score) {}
        Comparator<Candidate> order = Comparator.comparingInt(Candidate::score);
        for (int trial = 0; trial < 100; trial++) {
            var candidates = new ArrayList<Candidate>();
            for (int i = 0; i < 100; i++) candidates.add(new Candidate(i, random.nextInt(8)));
            Candidate preferred = trial % 2 == 0 ? candidates.get(random.nextInt(candidates.size())) : null;
            for (int limit : new int[]{1, 2, 5, 20, 100, Integer.MAX_VALUE}) {
                var expected = new ArrayList<>(candidates); expected.sort(order);
                if (preferred != null) { expected.remove(preferred); expected.addFirst(preferred); }
                expected = new ArrayList<>(expected.subList(0, Math.min(limit, expected.size())));
                check(TargetSelection.select(candidates, limit, order, preferred).equals(expected), "Bounded selection preserves exact stable sort/sticky-target behavior");
            }
        }
        check(TargetSelection.select(List.of(1, 2), 0, Comparator.<Integer>naturalOrder(), null).isEmpty(), "Empty target budget is handled safely");
        check(TargetSelection.select(List.<Integer>of(), 1, Comparator.<Integer>naturalOrder(), null).isEmpty(), "Empty candidate lists remain empty");
        check(TargetSelection.select(List.of(3, 1, 2), 2, Comparator.<Integer>naturalOrder(), 99).equals(List.of(1, 2)), "Expired preferred target cannot displace valid candidates");
        System.out.println("Path snapshot bounds, cinematic/presentation angles and exact target selection regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
