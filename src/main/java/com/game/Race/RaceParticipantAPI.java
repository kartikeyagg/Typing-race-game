package com.game.Race;

import com.game.API.CarAPI;

public interface RaceParticipantAPI {
    String getName();
    int getCharTyped();
    int getErrors();
    long getId();
    CarAPI getCar();
    boolean isBot();
    void onJoinRace();
    void onStartRace();
    void onFinishLineCrosh();
    void onFullRaceFinish();
}
