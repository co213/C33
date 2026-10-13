# Module workspace and automation

Press **Right Shift** to open 33. The startup credit is simply **33**.

## Finding and configuring modules

- Open Visual Studio for visual controls and previews. Category tabs sit across the top; the sidebar has All, Favorites, Enabled, and quick access.
- Search names, descriptions, and setting titles. Exact name matches appear first.
- Click a module to switch it on or off. Click **Configure** (or right-click the module) to configure it.
- Star a module to keep it in Favorites. Hidden modules stay out of library search results.
- Press **Ctrl+F** (**Command+F** on macOS) to focus the browser search.
- Inside a module, use **Find a setting** to filter controls. The enable switch and Back button are at the top; keybind and feedback controls are in a collapsible section.

The default palette is designed in HSV/HSB: fresh green accents, warm yellow highlights, deep natural blue surfaces, and bright neutral text. The color editor uses Hue (degrees), Saturation, Brightness and Opacity (percent). Existing saved theme colors remain customizable in the GUI tab; reset individual GUI color settings to apply the new defaults. RGB/hex clipboard imports and existing saved colors remain compatible.

## ESP

The 2D renderer interpolates entity bounds, validates projection depth and finite coordinates, and clips boxes to the viewport. Entities crossing the camera plane are omitted to prevent oversized boxes. Health bars and distance labels can be switched off independently. Max Distance applies to every mode. Box, Wireframe, Shader, and Glow modes remain available with existing saved settings.

## KillAura

Targets come from a nearby spatial query. Keep Target retains a valid primary target; turn it off to continuously re-sort by priority. Field of View limits the horizontal targeting cone. Attack and wall ranges both measure from the player's eyes to the closest point on the target hitbox.

Vanilla attack cooldown is used by default. Custom delays use a tick clock that advances while paused; TPS Sync scales custom delays. Weapon switch delay is independent. Deferred rotation attacks are discarded when their targeting tick becomes stale. Friend, entity type, age, named/tamed entity, and shield filters still apply.

## Automation

- **AutoEat** locks its food choice for the current eating session, honors food components and the blacklist, respects manual item use, and pauses while a screen is open. Full inventory search can borrow an occupied hotbar slot and restore its contents when the inventory still matches the session. It never moves items through an open container or occupied cursor. Manual inventory changes are preserved. AutoGap takes priority and releases AutoEat's resources before starting.
- **AutoTotem** evaluates danger every tick, independently of its inventory movement delay. Strict mode avoids unnecessary damage prediction. Inventory moves wait for the player inventory and an empty cursor; its lock and timers reset on disconnect/deactivation.

## Verification

Use Java 25 and `./gradlew build`. The build's `check` task runs `moduleLogicRegression`, a Java regression harness covering combat/recon boundaries, lifecycle transitions, shared scans, persistence failures, loopback HTTP and visual geometry. `./gradlew runClient` launches a development client for in-game verification.

## Visual Studio and combat effects

The menu uses the existing custom Minecraft GPU pipelines (tested with OpenGL), not ImGui or DX11. Rounded surfaces use finer curves and a one-pixel coverage fringe to soften jagged edges at framebuffer resolution. Custom text selects a font atlas that covers the requested GUI scale, with larger atlases created on demand through the maximum GUI/title scale. It stays integrated with Minecraft input, scaling and resource lifecycle without another native dependency. The animated menu figures are schematic previews, not live player models; use a world to see actual effects. Headers use an elevated blue surface, inset groups use deeper blue, and window shadows separate layers. Saved custom theme colors remain in place; reset GUI color settings to use the new green/yellow/blue defaults.

- **Hologram ESP**: segmented ground orbit, vertical rails and a rising scan band.
- **Constellation ESP**: twin animated helices with bright nodes around each entity.
- **Telemetry ESP**: screen-space brackets, scan line, ruler ticks, entity name/distance and optional health bar.
- Signature modes have independent animation, ground markers, color, target tether, detail and entity budget controls. Terrain occlusion is configurable for Hologram and Constellation. The entity budget bounds geometry per frame; entity selection and maximum distance still apply. KillAura's selected target can use the target color.
- **Combat Visuals**: broken target orbits, scanning band, crown markers, expanding attack rings, radial sparks and a fading movement ribbon. Primary/secondary colors, animation speed, lifetime, size, depth visibility, ring detail and simultaneous burst limit are configurable.
- **Auto third person** is opt-in in Combat Visuals. With both Combat Visuals and KillAura enabled it switches to the rear camera and restores the prior perspective when KillAura/Combat Visuals is disabled. Manual perspective changes yield camera control until the next activation. FreeLook/Freecam take priority. The movement ribbon is visible in third person; a teleport clears it.
- Attack bursts visualize local attack attempts, including manual attacks; they do not claim server-confirmed damage. Effects are bounded and cleared on disconnect/world change/deactivation. Pausing the game pauses effect lifetimes.
- Crimson Hologram, Arctic Constellation and Minimal Telemetry presets change appearance settings only. Enable ESP and Combat Visuals explicitly. Existing entity selections are preserved; ESP defaults to players.
- Scene shortcuts expose the existing Ambience, Fullbright, Camera Tweaks, FreeLook and Hand View controls.

### Quick start

1. Right Shift → Studio → choose a look preset.
2. Click the ESP module row to enable it; choose entities under **All ESP settings**.
3. Enable Combat Visuals and select **Third-person camera** if desired.
4. Configure/enable KillAura independently. The visual module never starts combat for you.
5. Use **All combat visuals** for effect colors, intensity and performance controls.

## Reconnaissance and PvP additions (26.2)

Open **Studio → World Intelligence / PvP Control**, or search these module names.

### Container ESP (`storage-esp`)

Discovery now walks the loaded 26.2 chunk block-entity positions incrementally instead of enumerating every chunk on every rendered frame. It validates entries against the current client world, clears on world changes, deduplicates double chests and includes container entities (chest boats and chest/hopper minecarts). Discovery, cache and render budgets bound work. Block Shader mode is preserved; vehicle storage uses a box overlay. No container contents or unloaded chunks are inferred.

### Faction Intel (`faction-intel`)

Combines claim boundaries and a power tracker. Inputs are received chat (including `/f who` responses), tab header/footer and custom tab names, and the displayed sidebar. It is passive except for the explicitly clicked **Request /f who** button. Records clear on disconnect or dimension change. Claim labels show owner, source and age; power readings retain each source, display the newest reading and flag disagreement rather than average conflicting values. Expiry is configurable. Claim boundaries are drawn at your current feet height; adjacent equal-owner chunks share an outer perimeter. Unknown chunks are not treated as wilderness.

Servers have different text layouts. The default grammar deliberately requires explicit coordinates/names and accepts these **synthetic example formats**:

```text
Claim [-2, 3]: Knights
Knights Power: 20/40
```

It also accepts a `Faction: Knights` line followed within five seconds by `Power: 20/40` from the same source. Claim Pattern takes exactly three capture groups (chunk X, chunk Z, owner); Power Pattern takes exactly three (faction, current power, maximum power). Invalid patterns disable parsing and show a HUD diagnostic. Patterns match whole lines, so normal player chat prefixes do not match.

Text maps accept the following bounded format, with north at the top (decreasing Z), east at the right (increasing X), and `+` identifying the header's chunk coordinate:

```text
Faction Map [-2, 3]
|AA.|
|A+B|
Legend: A=Knights,B=Rivals
```

`+` and unlisted symbols have unknown ownership. A map needs exactly one origin, rectangular rows, a legend, and must arrive within five seconds. This supports explicit textual map data; map-item pixels alone do not establish claim ownership. The map header, row and legend patterns are configurable: header captures chunk X/Z, row captures the grid cells, and repeated legend matches capture a single symbol and faction name. Actual plugin samples are still needed to tune these patterns. Layouts without explicit coordinates, a single `+` origin, or a symbol legend need a server-specific adapter. Text observations are not authoritative server claim APIs.

### Movement Heatmap (`movement-heatmap`)

Aggregates received player movement into bounded horizontal cells. Repeated movement raises intensity; stationary positions, large teleports and expired tracks do not. Half-life, expiry, minimum samples, cell size, render distance and friend/self inclusion are configurable. Cells are drawn at their most recently observed height. Self and friends are excluded by default. Only loaded players can contribute. The map remains in memory for this world session; it never uploads data or writes identifiable movement logs. A hotspot suggests activity, not proof of a base.

### Structure ESP (`structure-esp`)

Uses 26.2's POI registry and section palettes to discover POI blocks in loaded chunks, plus configurable landmark blocks (trial spawners, vaults, spawners and shriekers by default). The incremental scan has a block budget and skips palettes with no matching states. Block removal/unload clears local markers.

It also passively reads `ClientboundDebugChunkValuePacket` / `DebugSubscriptions.STRUCTURES` and `ClientboundDebugBlockValuePacket` / `DebugSubscriptions.POIS`. Structure bounding boxes and debug POIs appear only if the server actually sends these values. Empty updates remove observations; received data expires. It does not request privileged debug subscriptions, infer a seed, or equate a placed marker block with a generated structure. Ordinary multiplayer servers normally provide no structure debug bounds.

### Server-aware melee and shield estimates

KillAura's **Server Cooldown** uses monotonic time, server game-time deltas, sent attack packets and the current weapon's attack-speed delay. It uses a conservative jitter/configured margin and also requires the client cooldown to be ready. Duplicate/reordered time packets cannot refresh the model; stale timing pauses normal attacks. Until timing is sampled it uses a 20 TPS fallback. Constant ping is not subtracted from every cooldown: both successive attack packets experience that latency. Custom Delay remains the explicit override for plugin combat rules. Deferred attacks recheck readiness, so normal cooldown mode attacks one target per cooldown rather than spending the same ready state on several targets.

**Shield Predict** reads the target's received active-hand/item-use state and the item's 26.2 `BLOCKS_ATTACKS` delay component. It estimates use duration in server ticks, adds capped half-ping lead, and checks whether the target faces the attacker. KillAura's Ignore/Break shield rules can use that estimate. Merely holding a shield does not count as blocking. The HUD shows observed main/offhand items and estimated state. Server plugins and custom blocking arcs/disable rules can differ; no hidden shield cooldown or future offhand swap is claimed as known.

### Crystal Aura improvements

- Server Pacing scales configured delays with estimated server tick duration and waits for spawn/removal observations before retrying pending actions, with a bounded latency-aware timeout.
- Deferred actions carry a generation guard and recheck active state, world, pause/range/placement conditions and explosion damage at execution time.
- Health Reserve preserves a configurable amount of health/absorption when Anti Suicide is enabled.
- Damage Advantage optionally requires target damage to exceed self damage by a configured amount.
- Protect Friends rejects predicted explosions above the friend damage cap or predicted lethal friend damage.
- Fast Break now honors the break enable switch, pause state, switch delay and attack budget.

Damage, shield and cooldown decisions are estimates from received state. These changes provide explicit freshness/risk controls and reduce stale actions; outperforming another client would require a controlled multiplayer benchmark.

## Shared infrastructure improvements

Settings saves now write and flush a sibling staging file before replacement. Failed serialization cannot stream a partial file over the saved configuration. Unreadable settings receive a uniquely named recovery copy; if that fails, the original file is protected from subsequent saves during the session. Existing NBT filenames and formats are preserved.

Invalid numeric settings now report an error instead of a false success message. Nonfinite floating-point values are rejected. Clipboard NBT imports have size/allocation limits. Shared TPS consumers use the same monotonic timing estimate as combat.

See `rewrite-status.md` for implementation provenance, reviewed scope, and the remaining full-client rewrite work.

### Inventory and repair automation

Shared inventory actions now own their builder state and are bound to the container menu in which they were created. Slot resolution reads the current menu's actual inventory-backed slots, covering containers without maintaining a table of fixed offsets. Armor helpers use the actual boots-to-helmet inventory order.

AutoReplenish retains an exhausted slot's item template until you edit your inventory, so it can refill when supplies arrive later. It searches ordinary inventory slots and avoids a carried cursor stack. AutoArmor pauses for other container menus/cursor items, understands component-based gliders, and breaks equal-score ties using remaining durability. AutoEXP re-resolves experience bottles after replenishment and immediately before the rotation callback, preventing use of the old source slot; queued throws are cancelled when disabled, paused, or moved to another world. Throw Delay (four ticks by default) leaves time for XP absorption before spending another bottle. Chat and module menus do not erase AutoReplenish's exhausted-slot template; closing an inventory screen refreshes it to respect manual edits.

## Advanced Storage ESP

Choose **Storage ESP → Box Style → Hologram** for corner rails, a scanning band and broken locator rings. Corners, Full and Shader remain available. Signature Visuals controls animation, accent and label scale. Closest loaded blocks receive the render budget first. Matching double-chest halves share a box and label.

Container Intelligence adds nonoverlapping labels and a reticle inspector. Labels show type/custom name, distance, capacity, observed occupied slots, item totals and observation age. Optional coordinates and separate label distance/count limits keep dense storage areas readable. Point near a container to inspect its registry type, XYZ/chunk/dimension, current block-state properties, and chest block-above obstruction. The block-above check does not detect cats or plugin access rules.

**Contents unknown** means no usable server inventory observation exists. Opening a compatible container records only its server-sent storage slots, excluding player inventory and cursor. Later slot packets update the summary. Predicted inventory clicks are also sampled from the initialized menu because accepted clicks may not produce a slot echo; those summaries explicitly read **Client menu view**, rather than **Server snapshot**. The inspector lists the most numerous item types, custom names, enchanted-stack count and lowest equipment durability. Double chests, normal storage menus and container vehicles are supported. Server plugins can use different menus; compatible menu association is based on your recent interaction and is not an authoritative remote inventory API.

Observations stay in memory for this world session, are limited to 256 containers, and expire after the configured interval (five minutes by default). Replacement/unload invalidates object identity; changes to a paired chest invalidate its old summary. Another player can change contents after you close a container, so every remembered summary shows its age. No automatic opening or hidden-inventory requests are made. Ender-chest observations are the player's own inventory seen through that opened chest, not another player's contents. Clearing the rendering cache also clears content observations. Disabling Remember Contents clears them immediately.

Hide Opened now requires an actual unexpired observed inventory; merely attempting to interact with a blocked container no longer hides it.

InventoryTweaks sorting now plans using copied item components, so custom names, enchantments and other stack metadata are preserved in the plan. Each move checks the same screen/menu and expected source/destination contents; manual changes cancel remaining stale moves. Equal-count tools of the same type place the less-damaged tool first. When Disable in Creative is off, the creative player-inventory tab uses Minecraft's creative click handler and validates the wrapped slots. Item-catalog tabs are not sortable storage; changing tabs cancels an existing plan.

## Profile management and recovery

Use **Profiles → Refresh** or `.profiles refresh` (with your configured command prefix) to discover copied profile folders. Discovery also runs at startup when the profile index is missing. It reads the saved files without replacing them with current settings.

Imports choose an unused name, adding a number when needed, and publish the folder only after all settings files have been written. Existing profiles are preserved. Export includes only the selected components; selecting waypoints does not include unchecked HUD, macros or modules. Recovery backup files and symbolic links are excluded.

Editing uses a temporary copy until Save. Renaming moves the existing saved folder; cancelling discards edits. Names must contain letters, numbers, spaces, underscores or hyphens, with no leading/trailing spaces or reserved device names. Conflicting names are rejected. Filesystem operations across an entire profile/index are not a single transaction; recovered folders can be rediscovered with Refresh if an index save fails.

## Freecam travel and continuous storage search

- Install a compatible **Baritone for Minecraft 26.2** beside Client33. Navigation reports an actionable error when no supported backend is installed.
- In **Freecam → Pathing**, enable **click-to-path**. Middle-click a viewed block (up to 256 blocks from the camera) to save a waypoint. Aim at a visible waypoint in the current dimension and left-click to travel there; blocks/entities can also be selected. Freecam exits so pathfinder movement can control the player. The optional double-click setting still applies.
- **Allow mining** controls whether the route can break obstacles. The prior backend setting is restored when the route ends, is cancelled, or the world changes. Reachable targets are approached within three blocks; this is not a teleport. Thirty seconds without moving ends a stuck route.
- **Waypoints** now provides **Walk to**, **Mine to**, and **Stop navigation**. Commands: `.waypoint goto <name>`, `.waypoint mine-to <name>`, `.waypoint stop`, and `.waypoint status`.
- **Base Finder** scans loaded block entities while walking an expanding square search. Configure double chests (one pair counts once), dispensers/droppers, other storage, cluster radius, and minimum containers. Set the minimum to **1** to stop at a single double chest or dispenser. Defaults require two containers to reduce incidental hits. On discovery it saves a **Storage candidate** waypoint, approaches it, and stops. A container is evidence of storage, not proof of a player base.
- Disable Base Finder to stop. A replacement/cancelled route, death, world change, enabling Freecam, eight consecutive stuck routes, or reaching the world border also stops it. Failed exploration legs advance to the next grid point. Mining is optional; pathfinding still depends on terrain, equipment, hunger, and server behavior. Exploration loads new terrain normally; it cannot inspect chunks the server has not sent.

## Logout locations

**Logout Spots** compares player identities every tick, including simultaneous joins/leaves with the same player count. It records immutable positions only for players seen within the previous two seconds; walking out of render distance alone is not a logout. Spots expire, have a configurable count limit, disappear on rejoin, and reset on world/session changes. Labels optionally include age, distance, and coordinates. The module screen can save a waypoint, walk to a spot, clear memory, or stop navigation. Flat boxes now cover the player's footprint.

## Containers behind walls and storage memory

- Enable **Ghost Hand** and right-click toward a nearby chest, barrel, or other menu-bearing container. A cyan outline previews the selected target behind intervening blocks. Interaction remains limited by the real player's reach and the server's checks. Sneaking preserves normal interactions.
- **Ghost Hand → Mode → Probe** previews one **unconfirmed** target in amber. Adjust **probe-depth** to the desired block behind the visible wall, then right-click with an empty main hand (select an empty hotbar slot). This sends one ordinary interaction attempt, with a 250 ms cooldown; it does not assume the target is a chest or automatically mark it as confirmed. It can reach a server-side container whose client block appearance differs, when that server accepts the interaction. Plugins may reject it.
- Freecam's existing container interaction obeys the real player's reach; Ghost Hand's explicit Probe mode also works from a nearby Freecam view. Moving the camera does not move the player or extend server reach.
- **Storage ESP → Use maximum loaded range** removes distance fading and raises the render budget to 4096. Fresh settings show all received storage by default; existing saved preferences are preserved. Label range can be zero for no distance limit. Dense areas remain subject to rendering and label budgets.
- Newly received chunks and changed blocks refresh discovery. **Remember locations** retains bounded session history when a known container is unloaded, removed, or disguised. Amber outlines and **LAST SEEN** labels are unconfirmed history, with separate expiry; they never inherit a live inventory snapshot. Clear Rendering Cache clears both live and remembered data.
- A client cannot identify never-revealed chests that the server sends as stone, recover withheld container contents, force distant chunks to load, or guarantee passage through a solid wall. This feature implements reachable wall interaction and honest observation memory; it does not claim a universal anti-obfuscation or pearl-phasing bypass.

## Auto Farm

Open **World → Auto Farm**, or run `.settings auto-farm`. Enable it next to an existing farm with supplies in the hotbar or offhand.

- **Cycle** harvests mature wheat, carrots, potatoes, beetroot and nether wart, then replants the original crop. It requires the matching planting item before harvesting. Empty soil uses **Plant Empty With**.
- **Harvest** only breaks mature crops. **Plant** fills clear farmland (or soul sand for nether wart). **Bonemeal** grows supported immature crops; nether wart is excluded. **Hoe** prepares clear dirt, grass and dirt paths with a usable hoe.
- **Prepare Soil** and **Use Bonemeal** are optional additions to Cycle, both off by default. Hoes with one durability point remaining are protected.
- **Radius Blocks** is measured from the eyes and capped by player block reach. **Timing** contains separate harvest/use delays in milliseconds. **Advanced** offers Silent / Visible / None rotation and line-of-sight checking, enabled by default.
- Menus and manual attack/use pause automation. The module HUD shows its current action or missing supplies. Switching mode, disabling or leaving a world cancels pending work.

The module operates nearby; it does not walk, refill from inventory/chests, manage water/light, farm stems/trees/sugar cane, or bypass server reach/protection. Each unchanged target has a 350 ms retry interval and a five-second backoff after three attempts. Replant memory is bounded to 256 locations for five minutes and resets with the module; this also preserves crop identity across server placement rollbacks. Server rules can still reject interactions.

At small window sizes, Operation and Cycle Options show the common controls first. Timing and Advanced start collapsed, and irrelevant settings disappear when the mode changes. Hover a setting for its explanation.

## Speed Mine

Open **Player → Speed Mine** or `.settings speed-mine`.

- **Damage** finishes through the normal mining update once accumulated progress reaches **Finish Threshold** (default 0.7). **Instamine** uses the same threshold for one-click completion. Set the threshold closer to 1.0 for less early completion.
- **Normal** multiplies progress for the block actually being mined, including targets chosen by other modules. Boosting a normally non-instant block is capped below instant progress so Minecraft still sends its normal completion packet.
- **Haste** provides a mining bonus equivalent to **Haste Amplifier** (1 = Haste I). It supplements weaker Haste/Conduit Power rather than stacking the full bonus again. It does not add, overwrite or remove potion effects, or change attack speed.
- **Blocks / Blocks Filter** control Damage and Normal modes. An empty Blacklist permits all blocks. Haste applies to all blocks.
- Instamine respects **Break Delay → No Insta Break**, cancelled mining actions, and vanilla adventure restrictions. Highway Builder uses the same instant-mining eligibility check.
- **Grim Bypass** is a legacy extra-abort option, disabled by default. It sends one extra abort after Speed Mine's accelerated completion packet; it has no verified guarantee on current Grim versions.

Mining acceptance is controlled by the server. High multipliers and early completion can be rejected. Naturally instant crops remain instant, and unbreakable blocks stay unbreakable.

## Baritone task dashboard

Open the **Baritone** tab or `.settings baritone`. Install the compatible optional Baritone 26.2 backend to enable task controls; the tab explains installation when it is absent.

- Select **Go To**, **Mine**, **Follow**, **Explore**, **Farm**, **Tunnel**, **Build**, or **Command**. Only relevant inputs appear. **Use my position** fills coordinate fields from the player, including the schematic origin.
- **Start task** submits the form. Structured tasks replace the existing task after input validation. **Pause**, **Resume**, and **Stop** provide immediate controls, with live status. Client navigation routes preserve their timeout while paused.
- **Movement** contains readable shortcuts for breaking, placing, sprinting, parkour, and inventory use. These modify the same backend settings as Advanced Settings.
- **Command library** searches the installed backend's actual command names/descriptions. Hover a command for syntax, then select it to fill the custom command field. Edit arguments before pressing Start. Custom commands accept the configured prefix or `#`; informational commands do not automatically cancel a route.
- **Advanced settings** keeps the remaining backend controls searchable and separate from task inputs. Long backend labels wrap to fit smaller windows. Settings save when the screen closes.

Mine's quantity is the item count to collect; zero runs indefinitely. Farm radius zero is unrestricted. Tunnel fields are height, width, and length in that order. Put build files in the Minecraft game directory's `schematics/` folder; use a simple filename. Supported schematic formats and command availability depend on your installed backend. Submitted commands can still report errors in Baritone chat (for example missing files or unreachable goals).

Menus now use rounded panels, headers, buttons, inputs, dropdowns, tooltips, module highlights, scrollbar handles, and slider handles. Clear grouping and text status keep task feedback understandable without relying only on color. This applies to Client33's GUI; Minecraft's own screens retain their native styling.

**Players and logout spots** in the Baritone dashboard lists loaded players and recorded logouts in the current dimension. Refresh the list, then choose **Walk to**, **Mine to**, or **Follow** for a loaded player. Walking/mining targets a position captured at click time; Follow uses Baritone's follow process. Logout entries show their age and offer Walk/Mine to that historical spot. Enable **Logout Spots** while nearby players are present to collect records. Entries expire and clear across worlds; clicking a stale record asks you to refresh. The Logout Spots module also offers Mine to alongside its waypoint and walk controls.

Other online players may appear in the server player list without coordinates. The dashboard reports this count instead of inventing positions. The client cannot locate never-observed players, track offline players' current positions, or retrieve server-withheld positions.

**Saved waypoints** in Baritone offers search, refresh, Walk to and Mine to. It includes compatible dimensions and uses existing Overworld/Nether waypoint scaling when enabled on a waypoint. Each action checks that the waypoint still exists and that the world/dimension is compatible before starting.

## Auto RTP chest search

Search **Auto RTP** in the World category and enable it on a multiplayer server with an RTP command. It scans the current area before sending `/rtp` and repeats until it sees enough loaded chest containers. Defaults: eight containers within 64 blocks, at least 30 real seconds between commands, and a 10-second chunk-loading wait. Set Command, Command Interval, Load Wait, Minimum Chests and Search Radius for your server. Double chests count once; trapped chests count, ender chests do not. Chest contents and unloaded terrain are not inspected, so a match is a storage candidate rather than proof of a player base.

Commands pause while any screen is open. Discovery visits at most four chunks and 256 block-entity positions per tick, finishes a scan before retrying, and rechecks matching chests before stopping. Teleports/world changes discard old scan data; disconnect, death or disabling the module stops it. A match saves a waypoint by default and disables Auto RTP. It requires no Baritone. Server command permissions, teleport warmups and cooldowns still apply; rejected commands are retried at the configured interval.

## Tracer ribbons

Tracers defaults to **Ribbon**: a softly curved blue-to-green HSV gradient with feathered edges, a traveling light pulse, broken endpoint halos and subtle ground orbits. Existing Lines and Offscreen selections remain available; choose Ribbon in Appearance if a saved configuration selects Lines. Width is specified in screen pixels, with 48 curve segments and a configurable entity budget (48 by default). Ribbon Arc, animation, pulse speed and endpoint markers are adjustable. Through Walls selects either the overlay or terrain depth pipeline. Friend/entity filters, distance colors, target selection and custom colors are preserved. Offscreen arrows interpolate movement and have a brighter rim; invalid projections are omitted. GUI palette and tracer defaults use the green/yellow/natural blue direction; reset saved color settings to apply those defaults.

## Minecraft tuning and camera improvements

Open **Right Shift → Studio → Scene & Camera → Minecraft tuning**. This edits Minecraft 26.2's native options through their normal validators and callbacks: graphics preset, view distance, smooth lighting, improved transparency, vignette, clouds, particles, chunk fade, brightness, FOV, camera effects and performance limits. Slider changes commit on release to avoid rebuilding terrain throughout a drag; typed values commit when focus leaves the field. Settings save when leaving the screen, including the focused editor. **All Minecraft options** opens the vanilla options screen and returns here with refreshed values. Graphics presets also change native view distance and other quality options. Multiplayer servers still determine received terrain and simulation range; active modules such as Zoom/Fullbright may override the displayed result.

**Gentle motion** reduces sprint FOV shifts, damage tilt, portal/nausea effects and darkness pulsing; it disables view bobbing and lightning flashes. **Undo motion preset** restores the motion values captured before the latest preset click during this screen session. Opening the tuning screen alone changes no options.

**Camera Tweaks** now bounds distance to 0–64 blocks. Its scroll target uses a proportional curve that cannot flip distance negative and can recover from zero. **Smooth Distance** eases scroll changes consistently across normal frame rates; **Smoothing Speed** controls response. Disable smoothing for immediate changes. Alt + scroll remains the default binding; first person and open screens leave scrolling to Minecraft.

**Ambience** defaults now use HSV natural blues, greens and warm highlights for sky, clouds, water, grass, foliage, fog, lava and lightning. Overrides remain opt-in, and saved custom colors remain unchanged. Reset individual colors to use the new defaults. Disabling Ambience now refreshes terrain so baked grass/water/foliage tint returns to normal.

**Natural GUI colors** use forest and teal surfaces, sky-lit header gradients, leaf green controls and sunshine yellow titles. Studio cards carry green, blue or yellow markers to separate their roles. The **Natural colors** button beside module search applies the palette to existing saved GUI colors and saves it; GUI scale, layout and module settings are retained. Each color remains editable in the theme settings.

## Import and automatically build a building

Open **Studio → Scene & Camera → Import & build a building**, or **Baritone → Import & build a schematic**.

1. **Browse & convert building**, or drop one file on Building Studio. Supported inputs: Alpha/MCEdit `.schematic`, Sponge v1–3 `.schem`, Litematica v4–7 `.litematic`, and vanilla structure `.nbt`. Gzip and uncompressed NBT are accepted. Conversion runs in the background.
2. Review dimensions, Minecraft data versions and **Materials — block counts**. Choose the lowest X/Y/Z corner, or **Use my position**. Imported offsets are normalized to that corner; buildings extend east, up and south. Rotation/mirroring are not provided by this importer.
3. Bring the required blocks and press **Build building**, then close the screen to let Baritone move. **Pause**, **Resume** and **Stop** control the current backend task. Building requires an installed Baritone for Minecraft 26.2; importing itself does not.

A converted Sponge v2 copy is saved to your game directory's `schematics/` folder under a new name. Originals remain untouched. **Load saved** restores a previous converted building when you reopen the screen. Minecraft's data fixer upgrades historical block names/properties; pre-1.13 numeric IDs are flattened first. This converts block geometry/state, not a complete world or every feature of an old mod. Unavailable blocks, invalid properties and files from newer Minecraft versions fail clearly instead of silently becoming air. Legacy modded numeric IDs and overlapping Litematica regions are rejected; merge/replace those in the source first. Multiple nonoverlapping Litematica regions and negative region sizes are supported.

**Keep existing blocks where the schematic is air** is on by default. Turn it off to also clear schematic air cells. Unspecified vanilla structure cells, structure-void markers and gaps between Litematica regions are always preserved. Review the origin before building; the builder may replace blocks inside the requested solid geometry. World-height/border checks run before replacing another task.

The builder places blocks from your inventory using normal gameplay. Entities, block-entity data (including chest contents and sign text), and automatic material gathering are not included. Material counts report block cells; item costs can differ for doors, beds, crops or slabs. Missing materials, protected land or unreachable placement can stall Baritone; check its chat/status. Imports are limited to 64 MiB on disk, 128 MiB NBT accounting, 512 blocks per side and two million cells. These bounds also apply to a multi-region bounding box.

Building Studio clears the previous selection when starting another import; a failed import cannot leave an older building selected for Build. The selected filename is shown above its dimensions. Incomplete or noninteger Litematica region positions/sizes and malformed structure lists/properties are rejected before conversion.

## Step on multiplayer servers

**Movement → Step → Mode: Auto** (default) uses normal Minecraft jumps in multiplayer and instant Native stepping in singleplayer. **Jump** always uses normal jumps. **Native** raises the client step-height attribute and needs the server to allow that movement; it cannot override a server movement plugin's rules.

Jump assistance requires a grounded moving player, a horizontal obstruction and clearance for the full ordinary jump arc. It skips tall walls, liquids, ladders, flight, vehicles, open menus and manually pressed jump. The configured height still limits obstacles; ordinary jumping is capped at 1.25 blocks. Sneaking suppresses assistance unless Active When explicitly selects Sneaking.

Server position corrections/teleports pause Step assistance for two real seconds and restore the normal attribute. A correction received while Native stepping is applied switches to **Jump fallback** until you re-enable Step. Corrections are accepted normally; no movement packets are fabricated or cancelled. Repeated corrections extend the pause. The module restores the captured attribute base value (retaining modifiers), preserves later server attribute updates and handles player replacement/disconnect safely. Baritone's instant-step setting is enabled only during Native stepping and restored otherwise.

Safe Step's crystal exposure probes always restore the player's bounding box, including on exceptions, and use yaw-correct movement offsets. Server-specific plugin compatibility still requires testing on that server; custom movement restrictions can also reject ordinary jumps.

## Sprint

Sprint **Strict** (default) uses Minecraft 26.2's native start/run/swim eligibility, including food, mobility, shallow water, item use and vehicle restrictions. **Rage** adds backwards/sideways sprint and optional stationary sprint while retaining mobility, food, collision and item-use eligibility. Its water setting now stops sprint immediately; Strict uses normal Minecraft swimming rules. GUI sprint requires active GUI Move, its Sprint option and an allowed screen; chat and text-entry screens do not qualify.

Sprint state is applied after movement input updates, before ordinary movement packets. Unsprint On Hit synchronizes the vanilla sprint state before the attack and resumes automatic sprint on the next movement update; it does not immediately restart inside the attack. Keep Sprint preserves ordinary attack sprint when Unsprint On Hit is off. Critical/sweep attacks still need normal Minecraft conditions. Disabling releases automatic sprint unless the manual sprint key is held. Existing mode/setting names remain compatible with saved configurations.

## Display names

Display names refresh when the language or resource packs change. Enchantment keys always resolve against the current server registry, with a translation or namespaced identifier fallback when disconnected; unbound holders are handled safely. Names no longer retain server enchantment holders in global caches. Custom stack names remain live and all display names consistently remove legacy formatting codes. Missing sound subtitles use readable words. Built-in name caches use thread-safe maps and replace their entire generation on resource reload.

## Reach

Reach now has independent block/entity channels. **Extra** (default) adds the saved `extra-block-reach` / `extra-entity-reach` values to Minecraft's current attributes, preserving creative and other native modifiers. **Total** selects a final distance per channel. Optional **Only While Sneaking** and **Creative Only** conditions return to native ranges when unmet. Custom ranges are bounded to 0–64 blocks; negative Extra values can reduce range. The HUD info shows effective block/entity ranges. Only the local client player's getters are changed; native attributes and other players remain untouched.

Server validation still determines whether mining, placement, containers or attacks at the selected range succeed. There is no universal extra-reach allowance or plugin bypass; the old blanket vanilla/Paper claim has been removed.

## AFK Baritone watching

Open **Path Manager → AFK viewing → Watch in third person**, or enable **Render → Baritone Visuals** and select the rear third-person view. The module adds a depth-tested feathered green/blue route ribbon, soft traveling light, a warm destination ring for concrete goals, and a rounded journey card. Progress/approximate game-time remaining refer to the **current calculated path segment**, not an entire mining/build task; these can reset when Baritone recalculates. No concrete marker is invented for composite or XZ-only goals.

The AFK camera defaults to six blocks back and an 18-degree downward viewing angle, with eased turns. It changes only the rendered camera while a backend task is active. Player rotations, commands, movement and pathfinding remain unchanged. Camera collision stays enabled during watching, including when Camera Tweaks normally clips. First person, front third person, Freecam, Free Look and Highway Builder keep their own camera behavior. Disable Smooth Watch Camera to use the ordinary camera, or tune distance/pitch/response. Disable Animation for still geometry. Path snapshots refresh every five game ticks, contain at most 256 nodes and reset on world change/disable; visual geometry is distance-limited. Backends without visual-path support show no overlay.

## KillAura watching and target selection

**Combat → KillAura → Presentation → Smooth Combat Animation** eases only the local player's rendered third-person body/head turns; Animation Response controls how closely they follow sent aim. Rotation packets, player angles, cooldowns, attack callbacks, target-count settings and hit rules remain unchanged. The visible pose can briefly lag actual aim while easing. Disable the setting to display exact sent angles. This is presentation smoothing, not a claim that automation behaves like a human.

Target selection now keeps only the best requested candidates in a bounded heap instead of sorting all eligible entities. It preserves the previous stable ordering, tie order, sticky primary target and all entity filters. It reduces sorting work for small target limits in crowded areas, without introducing acquisition waits, random aim or attack delays. Gear, latency, server rules and opponents still determine fight outcomes.

## Counter-Mine 2 gun aiming

**Combat → Counter Mine Rage** is a separate, disabled-by-default gun module. Hold a gun, use **Add held gun name** if its name is not recognized, and enable the module. The default profile permits `cherry.pizza` and its subdomains; add an alternate address to Server Hosts when needed. Configurable CS-style aliases are examples, not verified server item IDs. Prefer names over base items when guns and grenades share a vanilla item.

It instantly aims at visible players within 128 blocks by default, preferring an exposed head then torso. Friends, scoreboard allies, spectators and dead players are excluded. Range, acquisition cone, head/chest/visible aim points, priority and sticky targeting are adjustable. Always tracks while holding the gun; While Firing follows the configured Attack or Use key. **Firing remains manual.** Aim is synchronized with an ordinary rotation packet before vanilla key handling. Optional motion prediction is off for direct hitscan aim; when enabled it is bounded and ignores teleport deltas. Disable competing aim modules to avoid conflicting rotations.

Counter-Mine 2's public listing describes server-controlled recoil, spread and wall penetration and supplies no weapon protocol. This module does not claim to cancel spread/recoil, infer custom teams, or guarantee hits. Require Visible can be disabled to aim through cover, but the server decides penetration. The optional CherryPizza modpack currently lists Minecraft 1.21.11; this build targets 26.2, so connecting, gun names, controls and actual shot behavior need verification on the server. Menus, pause, disconnect, world replacement and invalid targets stop tracking.
