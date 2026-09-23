package com.game.Race;

public final class RaceUser {
    private final RaceParticipantAPI raceParticipant;
    private final long startTime;

    public RaceUser(RaceParticipantAPI raceParticipant, long startTime) {
        this.raceParticipant = raceParticipant;
        this.startTime = startTime;
    }

    public double getVelocity() {
        long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
        return raceParticipant.getCharTyped() * 1000.0 / elapsed;
    }

    public int getDistance() { return raceParticipant.getCharTyped(); }

    public void setParameters() {
        // Metrics are calculated on demand so they never become stale.
    }
}
