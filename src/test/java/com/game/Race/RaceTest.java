package com.game.Race;

import com.game.hardware.StepperAngleConverter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceTest {
    @Test
    @SuppressWarnings("unchecked")
    void exposesHumanDistanceAndQuantizedRotation() {
        Race race = new Race(new StepperAngleConverter(0.04));
        User user = race.join("Test Driver", 0x22D3EE);
        String passage = (String) race.state().get("passage");
        int typed = passage.length() / 2;

        race.updateProgress(user.getId(), typed, 2);
        Map<String, Object> response = race.distances();
        List<Map<String, Object>> participants = (List<Map<String, Object>>) response.get("participants");
        Map<String, Object> human = participants.stream()
                .filter(value -> ((Number) value.get("id")).longValue() == user.getId())
                .findFirst().orElse(null);

        assertNotNull(human);
        assertEquals(typed, human.get("typedCharacters"));
        assertTrue(((Number) human.get("distanceMeters")).doubleValue() > 49.0);
        Map<String, Object> rotation = (Map<String, Object>) human.get("rotation");
        double degrees = ((Number) rotation.get("degrees")).doubleValue();
        assertEquals(Math.rint(degrees / 1.8), degrees / 1.8, 1e-7);
        assertEquals(0.04, response.get("wheelRadiusMeters"));
    }

    @Test
    void progressCannotMoveBackwards() {
        Race race = new Race(new StepperAngleConverter(0.03));
        User user = race.join("Monotonic", 0xF97316);
        race.updateProgress(user.getId(), 25, 1);
        race.updateProgress(user.getId(), 10, 0);

        assertEquals(25, user.getCharTyped());
        assertEquals(1, user.getErrors());
    }
}
