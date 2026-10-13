/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.accounts;

public class UuidToProfileResponse {
    public Property[] properties;

    public String getPropertyValue(String name) {
        if (properties == null) return null;
        for (Property property : properties) {
            if (property != null && name.equals(property.name)) return property.value;
        }

        return null;
    }

    public static class Property {
        public String name;
        public String value;
    }
}
