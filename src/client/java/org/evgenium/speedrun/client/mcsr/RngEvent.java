package org.evgenium.speedrun.client.mcsr;

import org.evgenium.speedrun.mcsr.RngStream;

public record RngEvent(
    long ordinal,
    RngStream stream,
    long index,
    String operation,
    long rawValue,
    String result
) {
    public String compactLine() {
        String suffix = result == null || result.isBlank() ? "" : " -> " + result;
        return "#" + ordinal + " " + stream.name() + "[" + index + "] " + operation + suffix;
    }
}
