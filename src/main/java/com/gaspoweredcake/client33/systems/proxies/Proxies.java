/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.proxies;

import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.utils.network.CheckBatch;
import com.gaspoweredcake.client33.systems.System;
import com.gaspoweredcake.client33.systems.Systems;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import com.gaspoweredcake.client33.utils.network.Client33Executor;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.regex.Pattern;

public class Proxies extends System<Proxies> implements Iterable<Proxy> {
    public final Settings settings = new Settings();
    private final SettingGroup sgRefreshing = settings.createGroup("Refreshing");
    private final SettingGroup sgCleanup = settings.createGroup("Cleanup");

    private final Setting<Integer> threads = sgRefreshing.add(new IntSetting.Builder()
        .name("threads")
        .description("The number of concurrent threads to check proxies with.")
        .defaultValue(8)
        .range(1, 32)
        .sliderRange(1, 32)
        .build()
    );

    public final Setting<Integer> timeout = sgRefreshing.add(new IntSetting.Builder()
        .name("timeout")
        .description("The timeout in milliseconds for checking proxies.")
        .defaultValue(5000)
        .range(1, 60000)
        .sliderRange(1, 15000)
        .build()
    );

    private final Setting<Integer> tries = sgRefreshing.add(new IntSetting.Builder()
        .name("retries-on-timeout")
        .description("How many additional times to check a proxy if the check times out.")
        .defaultValue(1)
        .range(0, 5)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Boolean> sort = sgCleanup.add(new BoolSetting.Builder()
        .name("sort-by-latency")
        .description("Whether to sort the proxy list by latency.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pruneDead = sgCleanup.add(new BoolSetting.Builder()
        .name("prune-dead")
        .description("Whether to prune dead proxies.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> pruneLatency = sgCleanup.add(new IntSetting.Builder()
        .name("prune-by-latency")
        .description("Prune proxies at or above this latency in ms. 0 to disable.")
        .defaultValue(2000)
        .min(0)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Integer> pruneExcess = sgCleanup.add(new IntSetting.Builder()
        .name("prune-to-count")
        .description("If in excess, prune the number of proxies to this count. 0 to disable. Prioritises by latency.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 25)
        .build()
    );

    // https://regex101.com/r/gRHjnd/latest
    public static final Pattern PROXY_PATTERN = Pattern.compile("^(?:([\\w\\s]+)=)?((?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])(?i:@(socks[45]))?$", Pattern.MULTILINE);
    // https://regex101.com/r/QXATIS/1
    public static final Pattern PROXY_PATTERN_WEBSHARE = Pattern.compile("^((?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5]):([^:]+)(?::(.+))?$", Pattern.MULTILINE);
    // https://regex101.com/r/7M2LFx/1
    public static final Pattern PROXY_PATTERN_URI = Pattern.compile("^(?:(socks|socks4|socks5)://)?(?:(?<user>[\\w~-]+)(:(?<pass>[\\w~-]+))?@)?(?<addr>(?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(?<port>\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])$", Pattern.MULTILINE);

    private List<Proxy> proxies = new ArrayList<>();
    public volatile boolean refreshing;

    public Proxies() {
        super("proxies");
    }

    public static Proxies get() {
        return Systems.get(Proxies.class);
    }

    public boolean add(Proxy proxy) {
        for (Proxy p : proxies) {
            if (p.type.get().equals(proxy.type.get()) && p.address.get().equals(proxy.address.get()) && Objects.equals(p.port.get(), proxy.port.get()))
                return false;
        }

        if (proxies.isEmpty()) proxy.enabled.set(true);

        proxies.add(proxy);
        save();

        return true;
    }

    public void remove(Proxy proxy) {
        if (proxies.remove(proxy)) {
            save();
        }
    }

    public Proxy getEnabled() {
        for (Proxy proxy : proxies) {
            if (proxy.enabled.get()) return proxy;
        }

        return null;
    }

    public void setEnabled(Proxy proxy, boolean enabled) {
        for (Proxy p : proxies) {
            p.enabled.set(false);
        }

        proxy.enabled.set(enabled);
        save();
    }

    public void checkProxies(boolean all) {
        if (refreshing || isEmpty()) return;
        List<Proxy> snapshot = proxies.stream().filter(proxy -> all || proxy.status == Proxy.Status.UNCHECKED).toList();
        if (snapshot.isEmpty()) return;
        int workers = threads.get();
        int retries = tries.get();
        refreshing = true;
        try {
            Client33Executor.execute(() -> {
                try {
                    CheckBatch.run(snapshot, workers, retries, proxy -> proxy.checkStatus() == 3);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    Client33.LOG.error("Could not finish checking proxies.", e);
                } finally {
                    refreshing = false;
                }
            });
        } catch (RuntimeException e) {
            refreshing = false;
            throw e;
        }
    }

    public void clean() {
        if (refreshing) return;

        proxies.removeIf(proxy -> {
            if (pruneDead.get() && proxy.status == Proxy.Status.DEAD) return true;
            return pruneLatency.get() != 0 && proxy.status == Proxy.Status.ALIVE && proxy.latency >= pruneLatency.get();
        });

        List<Proxy> p = (sort.get() ? proxies : new ArrayList<>(proxies));
        p.sort(Comparator.comparingLong(proxy -> proxy.status == Proxy.Status.ALIVE ? proxy.latency : Long.MAX_VALUE));

        if (pruneExcess.get() > 0 && pruneExcess.get() < p.size()) {
            p.subList(pruneExcess.get(), p.size()).clear();
            if (!sort.get()) proxies.removeIf(proxy -> !p.contains(proxy));
        }
        save();
    }

    public boolean isEmpty() {
        return proxies.isEmpty();
    }

    public int size() {
        return proxies.size();
    }

    @NonNull
    @Override
    public Iterator<Proxy> iterator() {
        return proxies.iterator();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        tag.put("settings", settings.toTag());
        tag.put("proxies", NbtUtils.listToTag(proxies));

        return tag;
    }

    @Override
    public Proxies fromTag(CompoundTag tag) {
        if (tag.contains("settings")) settings.fromTag(tag.getCompoundOrEmpty("settings"));
        proxies = NbtUtils.listFromTag(tag.getListOrEmpty("proxies"), Proxy::new);

        return this;
    }
}
