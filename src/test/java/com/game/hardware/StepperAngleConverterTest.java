package com.game.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StepperAngleConverterTest {
    @Test
    void convertsDistanceToExactRadiansAndWholeMotorSteps() {
        StepperAngleConverter converter = new StepperAngleConverter(0.5);
        StepperAngleConverter.Rotation rotation = converter.convert(Math.PI * 0.5);

        assertEquals(Math.PI, rotation.getRadians(), 1e-10);
        assertEquals(180.0, rotation.getDegrees(), 1e-10);
        assertEquals(100, rotation.getSteps());
    }

    @Test
    void snapsDegreesToNearestOnePointEightDegreeStep() {
        StepperAngleConverter converter = new StepperAngleConverter(1.0);
        double distanceForOneDegree = Math.toRadians(1.0);

        StepperAngleConverter.Rotation rotation = converter.convert(distanceForOneDegree);

        assertEquals(1.8, rotation.getDegrees(), 1e-10);
        assertEquals(1, rotation.getSteps());
        assertEquals(0, rotation.getDegrees() % 1.8, 1e-10);
    }

    @Test
    void rejectsInvalidPhysicalInputs() {
        assertThrows(IllegalArgumentException.class, () -> new StepperAngleConverter(0));
        StepperAngleConverter converter = new StepperAngleConverter(0.03);
        assertThrows(IllegalArgumentException.class, () -> converter.convert(-0.1));
        assertThrows(IllegalArgumentException.class, () -> converter.convert(Double.NaN));
    }
}
