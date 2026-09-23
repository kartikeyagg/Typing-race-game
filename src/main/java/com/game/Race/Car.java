package com.game.Race;

import com.game.API.CarAPI;

public final class Car implements CarAPI {
    private int color;

    public Car(int color) {
        setColor(color);
    }

    public void setColor(int color) {
        this.color = color & 0xFFFFFF;
    }

    @Override
    public int getColor() {
        return color;
    }

    public String getHexColor() {
        return String.format("#%06X", color);
    }
}
