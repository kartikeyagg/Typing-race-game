package com.game.API;

import com.game.Race.RaceParticipantAPI;

public interface RaceAPIRaceServer {
    boolean isJoinable();
    void addUser(RaceParticipantAPI user);
}
