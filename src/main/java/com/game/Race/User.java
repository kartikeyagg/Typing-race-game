package com.game.Race;

import com.game.API.CarAPI;

public final class User implements RaceParticipantAPI {
    private int charTyped;
    private int errors;
    private final String name;
    private final long id;
    private final Car car;
    private final boolean bot;
    private long startedAt;
    private long finishedAt;

    public User(String name, long id, int color, boolean bot) {
        this.name = name;
        this.id = id;
        this.car = new Car(color);
        this.bot = bot;
    }

    public User(int charTyped, String name, long id) {
        this(name, id, 0x22D3EE, false);
        this.charTyped = Math.max(0, charTyped);
    }

    @Override
    public synchronized int getCharTyped() { return charTyped; }

    @Override
    public synchronized int getErrors() { return errors; }

    @Override
    public String getName() { return name; }

    @Override
    public long getId() { return id; }

    @Override
    public CarAPI getCar() { return car; }

    public Car getRaceCar() { return car; }

    @Override
    public boolean isBot() { return bot; }

    public synchronized void updateProgress(int typedCharacters, int errorCount, int passageLength) {
        int bounded = Math.min(Math.max(0, typedCharacters), passageLength);
        charTyped = Math.max(charTyped, bounded);
        errors = Math.max(errors, Math.max(0, errorCount));
        if (charTyped == passageLength && finishedAt == 0) {
            finishedAt = System.currentTimeMillis();
            onFinishLineCrosh();
        }
    }

    public synchronized long getStartedAt() { return startedAt; }

    public synchronized long getFinishedAt() { return finishedAt; }

    @Override
    public synchronized void onJoinRace() {
        charTyped = 0;
        errors = 0;
        startedAt = 0;
        finishedAt = 0;
    }

    @Override
    public synchronized void onStartRace() {
        startedAt = System.currentTimeMillis();
        finishedAt = 0;
    }

    @Override
    public void onFinishLineCrosh() { }

    @Override
    public void onFullRaceFinish() { }
}
