package com.gaspoweredcake.client33.utils.render;

import com.google.gson.Gson;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.systems.accounts.TexturesJson;
import com.gaspoweredcake.client33.systems.accounts.UuidToProfileResponse;
import com.gaspoweredcake.client33.utils.PostInit;
import com.gaspoweredcake.client33.utils.network.Http;

import java.util.Base64;
import java.util.UUID;

public class PlayerHeadUtils {
    public static PlayerHeadTexture STEVE_HEAD;

    private PlayerHeadUtils() {
    }

    @PostInit
    public static void init() {
        STEVE_HEAD = new PlayerHeadTexture();
    }

    public static byte[] fetchHead(UUID id) {
        if (id == null) return null;

        String url = getSkinUrl(id);
        if (url == null) return null;

        try {
            return PlayerHeadTexture.downloadHead(url);
        } catch (java.io.IOException e) {
            Client33.LOG.error("Could not fetch player head for {}.", id, e);
            return null;
        }
    }

    public static String getSkinUrl(UUID id) {
        UuidToProfileResponse res2 = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
            .exceptionHandler(e -> Client33.LOG.error("Could not contact mojang session servers.", e))
            .sendJson(UuidToProfileResponse.class);
        if (res2 == null) return null;

        String base64Textures = res2.getPropertyValue("textures");
        if (base64Textures == null) return null;

        try {
            TexturesJson textures = new Gson().fromJson(new String(Base64.getDecoder().decode(base64Textures), java.nio.charset.StandardCharsets.UTF_8), TexturesJson.class);
            if (textures == null || textures.textures == null || textures.textures.SKIN == null) return null;
            return textures.textures.SKIN.url;
        } catch (IllegalArgumentException | com.google.gson.JsonParseException malformed) {
            return null;
        }
    }
}
