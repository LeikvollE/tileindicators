package io.leikvolle.tileindicators;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TileStyle
{
    DEFAULT("Default"),
    RS3("RS3");

    private String name;

    @Override
    public String toString()
    {
        return getName();
    }
}