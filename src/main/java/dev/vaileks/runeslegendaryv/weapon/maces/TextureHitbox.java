package dev.vaileks.runeslegendaryv.weapon.maces;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Pixel-exact union of the opaque voxels of the flat generated TV sprite. No Interaction entity.
 */
final class TextureHitbox {
    private final boolean[][] pixels;

    TextureHitbox(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing tv-mask.txt");
        try (var reader =
                new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().toList();
            if (lines.size() != 16 || lines.stream().anyMatch(s -> !s.matches("[01]{16}")))
                throw new IOException("Invalid TV pixel mask");
            pixels = new boolean[16][16];
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) pixels[y][x] = lines.get(y).charAt(x) == '1';
        }
    }

    double intersect(
            Vector origin,
            Vector direction,
            Vector position,
            Quaternionf rotation,
            float scale,
            double max) {
        Quaternionf inverse = new Quaternionf(rotation).conjugate();
        Vector3f o = inverse.transform(origin.clone().subtract(position).toVector3f()).div(scale);
        Vector3f d = inverse.transform(direction.toVector3f()).div(scale);
        double nearest = Double.POSITIVE_INFINITY;
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                if (pixels[y][x]) {
                    double distance =
                            slab(
                                    o,
                                    d,
                                    x / 16.0 - .5,
                                    (15 - y) / 16.0 - .5,
                                    -.03125,
                                    (x + 1) / 16.0 - .5,
                                    (16 - y) / 16.0 - .5,
                                    .03125,
                                    max);
                    if (distance < nearest) nearest = distance;
                }
        return nearest;
    }

    static double slab(
            Vector3f o,
            Vector3f d,
            double x0,
            double y0,
            double z0,
            double x1,
            double y1,
            double z1,
            double max) {
        double near = 0, far = max;
        double[] os = {o.x, o.y, o.z}, ds = {d.x, d.y, d.z}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(ds[i]) < 1e-9) {
                if (os[i] < lo[i] || os[i] > hi[i]) return Double.POSITIVE_INFINITY;
                continue;
            }
            double a = (lo[i] - os[i]) / ds[i], b = (hi[i] - os[i]) / ds[i];
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (near > far) return Double.POSITIVE_INFINITY;
        }
        return near;
    }
}
