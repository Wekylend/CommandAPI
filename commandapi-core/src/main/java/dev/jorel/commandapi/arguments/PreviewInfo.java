package dev.jorel.commandapi.arguments;

import dev.jorel.commandapi.commandsenders.AbstractPlayer;

import java.util.Objects;

public final class PreviewInfo<T> {
    private final AbstractPlayer<?> player;
    private final String input;
    private final String fullInput;
    private final T parsedInput;

	/**
	 * @param player the Player typing this command
	 * @param input 		the current partially typed argument. For example "/mycmd tes" will return "tes"
	 * @param fullInput 	a string representing the full current input (including /)
	 * @param parsedInput the parsed input as a BaseComponent[] (spigot) or Component (paper)
	 */
    public PreviewInfo(AbstractPlayer<?> player, String input, String fullInput, T parsedInput) {
        this.player = player;
        this.input = input;
        this.fullInput = fullInput;
        this.parsedInput = parsedInput;
    }

    public AbstractPlayer<?> player() {
        return player;
    }

    public String input() {
        return input;
    }

    public String fullInput() {
        return fullInput;
    }

    public T parsedInput() {
        return parsedInput;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PreviewInfo) obj;
        return Objects.equals(this.player, that.player) &&
               Objects.equals(this.input, that.input) &&
               Objects.equals(this.fullInput, that.fullInput) &&
               Objects.equals(this.parsedInput, that.parsedInput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(player, input, fullInput, parsedInput);
    }

    @Override
    public String toString() {
        return "PreviewInfo[" +
               "player=" + player + ", " +
               "input=" + input + ", " +
               "fullInput=" + fullInput + ", " +
               "parsedInput=" + parsedInput + ']';
    }
}
