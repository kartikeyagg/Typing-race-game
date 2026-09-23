package com.game.hardware;

/** Converts linear travel into wheel rotation suitable for a 1.8 degree stepper motor. */
public final class StepperAngleConverter {
    public static final double DEFAULT_STEP_ANGLE_DEGREES = 1.8;

    private final double radius;
    private final double stepAngleDegrees;

    public StepperAngleConverter(double radius) {
        this(radius, DEFAULT_STEP_ANGLE_DEGREES);
    }

    public StepperAngleConverter(double radius, double stepAngleDegrees) {
        if (!Double.isFinite(radius) || radius <= 0) {
            throw new IllegalArgumentException("Radius must be a positive finite number");
        }
        if (!Double.isFinite(stepAngleDegrees) || stepAngleDegrees <= 0) {
            throw new IllegalArgumentException("Step angle must be a positive finite number");
        }
        this.radius = radius;
        this.stepAngleDegrees = stepAngleDegrees;
    }

    public Rotation convert(double distance) {
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("Distance must be a non-negative finite number");
        }
        double exactRadians = distance / radius;
        long steps = Math.round(Math.toDegrees(exactRadians) / stepAngleDegrees);
        double stepperDegrees = steps * stepAngleDegrees;
        return new Rotation(exactRadians, stepperDegrees == -0.0d ? 0.0d : stepperDegrees, steps);
    }

    public double getRadius() { return radius; }

    public double getStepAngleDegrees() { return stepAngleDegrees; }

    public static final class Rotation {
        private final double radians;
        private final double degrees;
        private final long steps;

        private Rotation(double radians, double degrees, long steps) {
            this.radians = radians;
            this.degrees = degrees;
            this.steps = steps;
        }

        public double getRadians() { return radians; }
        public double getDegrees() { return degrees; }
        public long getSteps() { return steps; }
    }
}
