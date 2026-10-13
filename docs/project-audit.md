# Project audit and feature delivery — 2026-10-12

## Scope

Minecraft 26.2, Fabric, Java 25. Starting revision: `598520ff6cb79fc5cc34b44bfa4eb16c8975127d`. Current inventory: 989 Java files / 105,434 lines; 175 registered modules. All 174 module registrations from the starting revision remain, with Base Finder added.

The entire source set was compiled and included in strict compiler diagnostics. Resource and registration consistency was checked across the repository. Manual inspection and fixes concentrated on the boundaries below. This is not a claim that every source line, module, server plugin, or optional integration has been exhaustively verified.

## Implemented repairs and extensions

| Area | Changes | Evidence / limits |
| --- | --- | --- |
| Profiles | Safe name/path checks, symlink rejection, non-destructive imports, staged component exports, refresh, rename, isolated editing | Filesystem failure and conflict regressions; full build |
| Settings and persistence | Defensive mutable-value copies, finite vector validation, malformed position/enum/list guards, synchronous account replacement, safe waypoint removal | Settings/NBT and existing persistence regressions |
| Collections and I/O | Logical list bounds, exception-safe removal, correct sorted map views, positioned channel reads, no-progress detection | Collection/channel regressions |
| Proxies and Swarm | Bounded concurrent retries, worker queues, connection cleanup and limits, client-thread command dispatch | Concurrent retry regression; Swarm compile/manual inspection, no live multi-client fixture |
| Input and macros | Release cancelled keys, preserve overlapping holds, cancel on disconnect, safe reentrant schedules, ignore key repeats | Full build; existing lifecycle regression does not individually test every command |
| Rendering and assets | Direct texture-buffer validation, decoder/stream cleanup, safe font replacement, uniform bounds, malformed head data handling | Full build and native client/UI/overlay rendering; not every asset failure was injected |
| Combat and rotations | Correct Surround yaw/pitch order; isolated rotation batches, stale-world guards, one-shot callbacks, angle restoration | Compilation and existing combat regressions; multiplayer combat behavior not newly verified |
| Misc/world modules | Packet log retention order and file rotation, AutoSmelter throttle, Notebot future/world checks and NBS timing/decoding, explicit encodings/locales | Log retention regression; compile/manual inspection for song playback and smelting |
| Logout Spots | Identity-based membership changes, recent immutable sightings, bounded expiring spots, rejoin/world cleanup, corrected flat rendering, detailed labels, waypoint/navigation actions | Equal-count replacement, unload, rejoin, expiry/cap/reset regressions; native settings UI |
| Navigation / Freecam | Mark and select waypoints, walk/mine controls, backend requirement errors, route ownership, cancellation, temporary settings restoration, waypoint commands | Ownership/exploration regression; actual Freecam click-to-walk and mining through a blocked corridor |
| Base Finder | Incremental loaded-storage scanning, configurable clusters, expanding exploration, saved candidate, approach/stop, stuck/cancel/world lifecycle | Real dispenser detection and arrival; continued travel across multiple empty-terrain segments |
| Ghost Hand | Reach-bounded wall interaction, target preview, explicit unconfirmed probe depth, cooldown, empty-main-hand check | Chest opened behind a stone wall in both modes on integrated server; arbitrary obfuscation plugins unverified |
| Storage ESP | Maximum-loaded-range preset, fresh chunk/block refresh, wider/unlimited label range, separate bounded last-seen position memory | Native 80-block through-wall label; removed chest becomes amber unconfirmed history; memory expiry/cap regression |
| Block ESP and mixins | Incremental client-thread palette scans replace unsafe world reads on worker threads; guarded no-op block events; stale command suggestions discarded; Baritone cache workers no longer block JVM exit | Native selection, block updates, startup and clean exit after reproducing/fixing the shutdown watchdog crash |

## Validation

- Documented full Gradle build includes `moduleLogicRegression`, access-widener validation, compilation and JAR assembly. New coverage includes profiles, settings, collections/channels/concurrency/log retention, navigation/logout/exploration, and location memory.
- Resource audit passed: 13 JSON files, 6 mixin configurations, 210 registered mixins, package/path consistency, no duplicate module registrations or missing baseline modules.
- Native tests used a synthetic superflat world under Xvfb/Mesa with compatible Baritone 26.2-SNAPSHOT. The initial navigation run exposed a shutdown-watchdog failure; the optional Baritone worker fix was then verified by daemon thread inspection and a successful normal quit.
- Native warnings: missing narrator/audio device, unavailable X11 cursor shape, and rejected development authentication for Realms/certificates. Local gameplay remained usable. No real server credentials were used.
- Exact commands, input hashes, intermediate failures and reused checks are recorded outside the repository in `workspace-context/validation-record.md`.

## Remaining work and practical limits

1. **Strict Error Prone/NullAway is not clean.** The expanded baseline emitted 2,358 NullAway errors and 2,533 warnings, followed by Gradle diagnostic-transport heap exhaustion. Those are baseline counts, not a count of remaining defects after this patch. The named diagnostic inventory is provided separately as `compiler-audit-baseline.csv`. Many warnings involve reflective callbacks or Minecraft registry identity; nullable contracts still require a deliberate repository-wide migration. No detector was disabled to claim a pass.
2. This patch does not certify all 175 modules on external multiplayer servers, anti-cheat configurations, optional renderer mods, account providers, proxies, or Swarm peers. Most inherited module implementations remain. No full-client performance benchmark was completed.
3. The server controls loaded chunks, block identities, inventories, reach acceptance, and teleportation. Never-revealed chests sent as stone cannot be reliably identified by a client. Probe mode attempts an ordinary nearby interaction; rejection remains possible. Location memory is explicitly unconfirmed and may refer to a destroyed container. A universal pearl-phasing bypass is not implemented.
4. Navigation requires a compatible optional Baritone installation. It approaches within three blocks; it may fail due to terrain, equipment, hunger, unloaded information, or server restrictions. It stops on cancellation, world/death changes, or no movement for 30 seconds. Base Finder also stops after eight consecutive blocked routes or at the world border.
5. Collection search ordering is retained, but `ValueComparableMap` now implements the general Map contract through AbstractMap rather than inheriting TreeMap. In-repository callers only use Map operations; external addons depending on TreeMap-specific methods would need adjustment.

Usage instructions are in `docs/gui-and-modules.md`.

## Auto Farm follow-up

Added Auto Farm to World (176 registered modules total). This ports the older farming workflow to Minecraft 26.2, with Cycle / Harvest / Plant / Bonemeal / Hoe modes, original-crop replanting, hotbar/offhand supplies, reach and visibility checks, tool protection, bounded retry/replant state, and cancellation on mode/world/lifecycle changes. The settings menu groups common controls and collapses advanced controls, with irrelevant settings hidden by mode.

The full build and module logic regressions passed after these additions. Local integrated-server assertions verified mixed-crop Cycle behavior, immature crop protection, separate harvest/plant/hoe/bonemeal modes, nether wart exclusion from bonemeal, missing-seed protection, offhand planting, live mode changes, disabling, and clearing remembered crop identity across dimension transfers. Menu readability was inspected at 640×480. Registration/resource checks passed. These are local checks, not multiplayer-server compatibility certification; the strict compiler baseline and other broader limitations above remain outstanding.

## Speed Mine follow-up

Fixed the hardcoded instant-mining threshold and aligned Highway Builder with the same eligibility rule. Damage completion now happens in the actual vanilla mining update rather than writing stale progress in an earlier tick. Instant breaking retains vanilla permission, target-switching and prediction bookkeeping, then submits STOP after a successful predicted destruction. Cancelled starts return false. Normal acceleration uses the actual block and local player, with guarded arithmetic and the normal non-instant packet path preserved. Haste is now a mining-only calculation that preserves potion effects and accounts for stronger Haste/Conduit Power. The optional legacy extra-abort packet is scoped to accelerated completions and follows STOP.

Full build/regressions passed. Local gameplay confirmed low/high instant thresholds, Instamine off, Damage/Normal/Haste mining, Break Delay protection, blacklist/whitelist behavior, cancelled starts, adventure restrictions, unbreakable bedrock, and zero-hardness crop mining. The integrated server retained its real hidden-icon Haste effect; effect preservation on the client is also supported by removing all potion mutations from the implementation. Existing-effect multiplier arithmetic has focused regression coverage. Current external anti-cheat versions and remote-server acceptance remain unverified.

## Baritone usability and rounded GUI follow-up

- Replaced the backend-only setting list with task forms, live task status, pause/resume/stop, player-coordinate capture, common movement settings, installed command help/search and advanced search that preserves dashboard controls.
- Added pure command validation before task replacement: bounds/overflow, mine block IDs and deduplication, player names, tunnel order, schematic filenames, prefix stripping and multiline/length rejection.
- Added optional capability defaults to IPathManager, so the GUI does not resolve Baritone classes when the optional backend is absent. The Baritone tab remains available with installation guidance.
- Paused navigation no longer consumes the stuck timeout. Stopping also clears the wrapper's pause/direction state.
- Backend numeric wrappers no longer clamp ordinary double settings to 10, floats remain representable, large Long values are not truncated and rewritten on activation, and color activation reads current backend values.
- Added rounded GUI mesh geometry through the existing rendering/scissor path. Reused cached circle samples; corner radius clamps to control dimensions. Fixed triangle winding for the existing culling pipeline during visual verification.
- Initial UI check found that long advanced labels enlarged the whole window beyond 640x480; wrapped backend labels locally before the next native check.

Command format regressions cover all dashboard forms, but not every backend operation in gameplay. Schematic formats, multiplayer rules, remote servers and all optional mod combinations are not exhaustively verified. Existing strict NullAway baseline remains outstanding; this work does not establish a warning-free repository.

### Player and logout destinations

Connected Baritone's dashboard to loaded-player coordinates and Logout Spots' bounded session records. Added Walk to/Mine to routes, live Follow selection, search/refresh, age/source labels and a count of online players without supplied positions. Targets are revalidated by world/player identity or logout timestamp before navigation; expiry/rejoin prevents using stale dashboard records. No automatic attack or server position discovery is implemented. LogoutTracker regression exercises logout detection, rejoin, expiry, bounds and world clearing; a real multiplayer player/logout scene is still pending native verification.

### Final export sweep

Added searchable saved waypoint routes to Baritone, using existing dimension checks and coordinate scaling, with deleted-waypoint/world guards. README now states Java25, Fabric Loader0.19.5+, Minecraft26.2 and the optional Baritone requirement. Source export retains the complete source/resources, Gradle wrapper, build configuration, licenses and current documentation; old checked-in prebuilt JARs are excluded to avoid presenting them as the new build. Release artifacts are supplied separately. Build/regressions, resource registration, diff whitespace, GUI native checks and export content/hash checks are the selected final gates; strict NullAway and remote multiplayer/provider checks retain their documented limits.

Final build/regressions and resource audit pass with 176 modules and 210 registered mixins. Native Minecraft26.2 checks verified readable rounded menus at640x480, backend-present/absent loading, Go To arrival, pause/resume/stop, scrolling, command-library search/selection, advanced search retaining task controls, and saved-waypoint arrival. A supplied diamond pickaxe/allowBreak fixture verified the mining target became air; automatic collection/quantity completion was not asserted. Initial mining attempt correctly reported that breaking was disabled. Earlier SpeedMine and AutoFarm gameplay/regression passes remain valid for unchanged implementations. Archive CRC, source/JAR hashes and wrapper executable mode are checked during export.
