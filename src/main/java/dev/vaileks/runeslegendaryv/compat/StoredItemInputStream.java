package dev.vaileks.runeslegendaryv.compat;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Map;

/** Accept only Bukkit's standard item-value types, never plugin-defined serialized classes. */
final class StoredItemInputStream extends BukkitObjectInputStream {
    StoredItemInputStream(InputStream input) throws IOException {
        super(input);
        setObjectInputFilter(
                info -> {
                    if (info.depth() > 32
                            || info.references() > 10000
                            || info.streamBytes() > 1048576
                            || info.arrayLength() > 1048576)
                        return ObjectInputFilter.Status.REJECTED;
                    Class<?> type = info.serialClass();
                    if (type == null) return ObjectInputFilter.Status.UNDECIDED;
                    while (type.isArray()) type = type.getComponentType();
                    String name = type.getName();
                    boolean allowed =
                            type.isPrimitive()
                                    || type == Object.class
                                    || type == String.class
                                    || type == Number.class
                                    || type == Enum.class
                                    || type == Boolean.class
                                    || type == Character.class
                                    || (Number.class.isAssignableFrom(type)
                                            && name.startsWith("java.lang."))
                                    || name.startsWith("java.util.")
                                    || name.equals("org.bukkit.util.io.Wrapper")
                                    || (name.startsWith("com.google.common.collect.")
                                            && name.contains("Immutable"))
                                    || (name.startsWith("org.bukkit.")
                                            && (type.isEnum()
                                                    || ConfigurationSerializable.class
                                                            .isAssignableFrom(type)));
                    if (!allowed)
                        throw new IllegalArgumentException(
                                "Unsupported stored item class: " + name);
                    return ObjectInputFilter.Status.ALLOWED;
                });
    }

    @Override
    protected Object resolveObject(Object object) throws IOException {
        if (object != null && object.getClass().getName().equals("org.bukkit.util.io.Wrapper")) {
            try {
                Field field = object.getClass().getDeclaredField("map");
                field.setAccessible(true);
                Map<?, ?> data = (Map<?, ?>) field.get(object);
                Object alias = data.get("==");
                Class<?> valueType =
                        alias instanceof String name
                                ? org.bukkit.configuration.serialization.ConfigurationSerialization
                                        .getClassByAlias(name)
                                : null;
                if (valueType == null || !valueType.getName().startsWith("org.bukkit."))
                    throw new InvalidObjectException("Unsupported stored item alias: " + alias);
                ClassLoader loader = valueType.getClassLoader();
                if (loader != org.bukkit.Bukkit.getServer().getClass().getClassLoader()
                        && loader != ConfigurationSerializable.class.getClassLoader())
                    throw new InvalidObjectException(
                            "Stored item alias belongs to a plugin: " + alias);
            } catch (ReflectiveOperationException e) {
                throw new IOException("Cannot validate stored Bukkit item", e);
            }
        }
        return super.resolveObject(object);
    }
}
