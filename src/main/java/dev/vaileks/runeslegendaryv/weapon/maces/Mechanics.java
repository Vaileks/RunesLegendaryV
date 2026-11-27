package dev.vaileks.runeslegendaryv.weapon.maces;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;

final class Mechanics {
    enum Proc {
        NONE,
        LOCAL,
        BORDER
    }

    static Proc roll(double value, double local, double border) {
        if (value < border) return Proc.BORDER;
        return value < border + local ? Proc.LOCAL : Proc.NONE;
    }

    static double[] local(double x, double z, double angle, double distance) {
        return new double[] {x + Math.cos(angle) * distance, z + Math.sin(angle) * distance};
    }

    static double[] border(
            double cx, double cz, double size, int side, double along, double inset) {
        double half = size / 2;
        double edge = Math.max(0, half - inset);
        double offset = (along * 2 - 1) * edge;
        return switch (side) {
            case 0 -> new double[] {cx + edge, cz + offset};
            case 1 -> new double[] {cx - edge, cz + offset};
            case 2 -> new double[] {cx + offset, cz + edge};
            default -> new double[] {cx + offset, cz - edge};
        };
    }

    static boolean canCatch(int age) {
        return age >= 0;
    }

    static Vector toward(Vector from, Vector target, Vector fallback, double distance) {
        Vector direction = target.clone().subtract(from).setY(0);
        if (direction.lengthSquared() < 1e-8) direction = fallback.clone().setY(0);
        if (direction.lengthSquared() < 1e-8) direction = new Vector(1, 0, 0);
        return direction.normalize().multiply(distance);
    }

    static Vector displacement(Vector end, double age, int liftTicks, int duration, double lift) {
        double up = Math.max(0, Math.min(1, (double) age / liftTicks));
        double side = Math.max(0, Math.min(1, (double) (age - liftTicks) / (duration - liftTicks)));
        double ease = side * side * (3 - 2 * side);
        double height = lift * up * up * (3 - 2 * up);
        // Hover starts at zero displacement, avoiding a jump at the end of the flight.
        if (age > duration) height += Math.sin((age - duration) * .12) * .06;
        return end.clone().multiply(ease).add(new Vector(0, height, 0));
    }

    static Quaternionf rotation(double age) {
        return new Quaternionf().rotateY((float) (age * .18)).rotateZ((float) (age * .065));
    }

    private Mechanics() {}
}
