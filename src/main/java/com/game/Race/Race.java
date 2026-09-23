package com.game.Race;

import com.game.API.RaceAPIRaceServer;
import com.game.hardware.StepperAngleConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe, in-memory race shared by every browser connected to this server. */
public final class Race implements RaceAPIRaceServer {
    public static final double TRACK_LENGTH_METERS = 100.0;
    private static final String PASSAGE = "The engine settles into a low growl as the lights sweep across the starting line. "
            + "Every clean word sends the car forward, every second matters, and the final straight rewards the racer "
            + "who can stay calm, accurate, and fast.";
    private static final int MAX_PARTICIPANTS = 12;
    private static final int MIN_HUMANS_TO_START = 2;
    private static final long DEFAULT_COUNTDOWN_MILLIS = 3_000;

    private final Map<Long, User> participants = new LinkedHashMap<>();
    private final Map<Long, Integer> botSpeeds = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong(1000);
    private final StepperAngleConverter angleConverter;
    private final long countdownMillis;
    private String raceId = newRaceId();
    private Long hostParticipantId;
    private long countdownEndsAt;
    private long startedAt;
    private Status status = Status.WAITING;

    public Race(StepperAngleConverter angleConverter) {
        this(angleConverter, DEFAULT_COUNTDOWN_MILLIS);
    }

    public Race(StepperAngleConverter angleConverter, long countdownMillis) {
        if (countdownMillis < 0) {
            throw new IllegalArgumentException("Countdown duration cannot be negative");
        }
        this.angleConverter = angleConverter;
        this.countdownMillis = countdownMillis;
        addBot(-1, "Apex", 0xF97316, 46);
        addBot(-2, "Volt", 0xA78BFA, 58);
        addBot(-3, "Drift", 0x34D399, 38);
    }

    public synchronized User join(String rawName, int color) {
        if (status != Status.WAITING) {
            throw new IllegalStateException("This race has already started");
        }
        if (humanCount() >= MAX_PARTICIPANTS - botSpeeds.size()) {
            throw new IllegalStateException("This race is full");
        }
        String name = sanitizeName(rawName);
        User user = new User(name, ids.incrementAndGet(), color, false);
        user.onJoinRace();
        participants.put(user.getId(), user);
        if (hostParticipantId == null) {
            hostParticipantId = user.getId();
        }
        return user;
    }

    public synchronized Map<String, Object> start(long requesterId) {
        if (status != Status.WAITING) {
            throw new IllegalStateException("The race is not waiting to start");
        }
        if (hostParticipantId == null || hostParticipantId != requesterId) {
            throw new IllegalStateException("Only the lobby host can start the race");
        }
        if (humanCount() < MIN_HUMANS_TO_START) {
            throw new IllegalStateException("At least two drivers must join before starting");
        }
        countdownEndsAt = System.currentTimeMillis() + countdownMillis;
        status = Status.COUNTDOWN;
        advanceCountdown();
        return state();
    }

    public synchronized User updateProgress(long id, int typedCharacters, int errors) {
        advanceCountdown();
        User user = participants.get(id);
        if (user == null || user.isBot()) {
            throw new IllegalArgumentException("Participant not found");
        }
        if (status != Status.RUNNING) {
            throw new IllegalStateException("The race is not running");
        }
        user.updateProgress(typedCharacters, errors, PASSAGE.length());
        updateStatus();
        return user;
    }

    public synchronized Map<String, Object> state() {
        advanceCountdown();
        refreshBots();
        Map<String, Object> state = baseState();
        state.put("passage", PASSAGE);
        state.put("participants", participantSnapshots());
        return state;
    }

    public synchronized Map<String, Object> distances() {
        advanceCountdown();
        refreshBots();
        Map<String, Object> state = baseState();
        state.put("participants", participantSnapshots());
        return state;
    }

    public synchronized Map<String, Object> reset() {
        resetInternal();
        return state();
    }

    public synchronized User find(long id) {
        return participants.get(id);
    }

    @Override
    public synchronized boolean isJoinable() {
        return status == Status.WAITING && humanCount() < MAX_PARTICIPANTS - botSpeeds.size();
    }

    @Override
    public synchronized void addUser(RaceParticipantAPI participant) {
        if (!(participant instanceof User)) {
            throw new IllegalArgumentException("Only User participants are supported");
        }
        User user = (User) participant;
        participants.put(user.getId(), user);
        if (!user.isBot() && hostParticipantId == null) {
            hostParticipantId = user.getId();
        }
    }

    private void addBot(long id, String name, int color, int wordsPerMinute) {
        User bot = new User(name, id, color, true);
        participants.put(id, bot);
        botSpeeds.put(id, wordsPerMinute);
    }

    private void startRace() {
        startedAt = countdownEndsAt > 0 ? countdownEndsAt : System.currentTimeMillis();
        countdownEndsAt = 0;
        status = Status.RUNNING;
        for (User participant : participants.values()) {
            participant.onStartRace();
        }
    }

    private void resetInternal() {
        raceId = newRaceId();
        countdownEndsAt = 0;
        startedAt = 0;
        status = Status.WAITING;
        for (User participant : participants.values()) {
            participant.onJoinRace();
        }
    }

    private void refreshBots() {
        if (status != Status.RUNNING) {
            return;
        }
        long elapsed = Math.max(0, System.currentTimeMillis() - startedAt);
        for (Map.Entry<Long, Integer> entry : botSpeeds.entrySet()) {
            User bot = participants.get(entry.getKey());
            double baseCharacters = entry.getValue() * 5.0 * elapsed / 60_000.0;
            double variation = 1.0 + 0.035 * Math.sin(elapsed / 1500.0 + Math.abs(entry.getKey()));
            bot.updateProgress((int) Math.floor(baseCharacters * variation), 0, PASSAGE.length());
        }
        updateStatus();
    }

    private void advanceCountdown() {
        if (status == Status.COUNTDOWN && System.currentTimeMillis() >= countdownEndsAt) {
            startRace();
        }
    }

    private void updateStatus() {
        boolean hasHumans = false;
        boolean allHumansFinished = true;
        for (User participant : participants.values()) {
            if (!participant.isBot()) {
                hasHumans = true;
                allHumansFinished &= participant.getCharTyped() >= PASSAGE.length();
            }
        }
        if (hasHumans && allHumansFinished) {
            status = Status.FINISHED;
            for (User participant : participants.values()) {
                participant.onFullRaceFinish();
            }
        }
    }

    private Map<String, Object> baseState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("raceId", raceId);
        state.put("status", status.name());
        state.put("startedAt", startedAt == 0 ? null : startedAt);
        state.put("countdownEndsAt", countdownEndsAt == 0 ? null : countdownEndsAt);
        state.put("serverTime", System.currentTimeMillis());
        state.put("hostParticipantId", hostParticipantId);
        state.put("minimumParticipantsToStart", MIN_HUMANS_TO_START);
        state.put("trackLengthMeters", TRACK_LENGTH_METERS);
        state.put("wheelRadiusMeters", angleConverter.getRadius());
        state.put("stepAngleDegrees", angleConverter.getStepAngleDegrees());
        return state;
    }

    private List<Map<String, Object>> participantSnapshots() {
        List<User> ordered = new ArrayList<>(participants.values());
        ordered.sort(Comparator.comparingInt(User::getCharTyped).reversed().thenComparingLong(User::getId));
        List<Map<String, Object>> snapshots = new ArrayList<>();
        int position = 1;
        for (User user : ordered) {
            snapshots.add(snapshot(user, position++));
        }
        return snapshots;
    }

    private Map<String, Object> snapshot(User user, int position) {
        double progress = PASSAGE.isEmpty() ? 0 : user.getCharTyped() / (double) PASSAGE.length();
        double distance = progress * TRACK_LENGTH_METERS;
        StepperAngleConverter.Rotation rotation = angleConverter.convert(distance);
        long end = user.getFinishedAt() > 0 ? user.getFinishedAt() : System.currentTimeMillis();
        long elapsed = startedAt > 0 ? Math.max(1, end - startedAt) : 1;
        double wpm = user.getCharTyped() / 5.0 / (elapsed / 60_000.0);
        double accuracy = user.getCharTyped() + user.getErrors() == 0
                ? 100.0
                : 100.0 * user.getCharTyped() / (user.getCharTyped() + user.getErrors());

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", user.getId());
        value.put("name", user.getName());
        value.put("bot", user.isBot());
        value.put("color", user.getRaceCar().getHexColor());
        value.put("position", position);
        value.put("typedCharacters", user.getCharTyped());
        value.put("errors", user.getErrors());
        value.put("progressPercent", round(progress * 100.0, 2));
        value.put("distanceMeters", round(distance, 4));
        value.put("wpm", round(wpm, 1));
        value.put("accuracyPercent", round(accuracy, 1));
        value.put("finished", user.getCharTyped() >= PASSAGE.length());
        Map<String, Object> rotationValue = new LinkedHashMap<>();
        rotationValue.put("radians", round(rotation.getRadians(), 6));
        rotationValue.put("degrees", round(rotation.getDegrees(), 6));
        rotationValue.put("steps", rotation.getSteps());
        value.put("rotation", rotationValue);
        return value;
    }

    private int humanCount() {
        int count = 0;
        for (User participant : participants.values()) {
            if (!participant.isBot()) count++;
        }
        return count;
    }

    private static String sanitizeName(String rawName) {
        if (rawName == null) throw new IllegalArgumentException("Name is required");
        String name = rawName.replaceAll("[^\\p{L}\\p{N} _-]", "").trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) throw new IllegalArgumentException("Name is required");
        return name.substring(0, Math.min(18, name.length()));
    }

    private static String newRaceId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    private enum Status { WAITING, COUNTDOWN, RUNNING, FINISHED }
}
