package io.leikvolle.tileindicators;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TileStyle
{
    DEFAULT("Default"),
    RS3("Rs3"),
    RS3_NO_ARROW("Rs3(no arrow)");

    private String name;

    @Override
    public String toString()
    {
        return getName();
    }
}