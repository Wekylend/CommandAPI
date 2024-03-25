/*******************************************************************************
 * Copyright 2018, 2020 Jorel Ali (Skepter) - MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package dev.jorel.commandapi;

import dev.jorel.commandapi.executors.CommandArguments;

import java.util.Objects;

/**
 * A class that represents information which you can use to generate
 * suggestions.
 */
public final class SuggestionInfo<CommandSender> {
    private final CommandSender sender;
    private final CommandArguments previousArgs;
    private final String currentInput;
    private final String currentArg;

    /**
     * @param sender       - the CommandSender typing this command
     * @param previousArgs - a {@link CommandArguments} object holding previously declared (and parsed) arguments. This can
     *                     be used as if it were arguments in a command executor method
     * @param currentInput - a string representing the full current input (including
     *                     /)
     * @param currentArg   - the current partially typed argument. For example
     *                     "/mycmd tes" will return "tes"
     */
    public SuggestionInfo(CommandSender sender, CommandArguments previousArgs, String currentInput, String currentArg) {
        this.sender = sender;
        this.previousArgs = previousArgs;
        this.currentInput = currentInput;
        this.currentArg = currentArg;
    }

    public CommandSender sender() {
        return sender;
    }

    public CommandArguments previousArgs() {
        return previousArgs;
    }

    public String currentInput() {
        return currentInput;
    }

    public String currentArg() {
        return currentArg;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SuggestionInfo) obj;
        return Objects.equals(this.sender, that.sender) &&
               Objects.equals(this.previousArgs, that.previousArgs) &&
               Objects.equals(this.currentInput, that.currentInput) &&
               Objects.equals(this.currentArg, that.currentArg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender, previousArgs, currentInput, currentArg);
    }

    @Override
    public String toString() {
        return "SuggestionInfo[" +
               "sender=" + sender + ", " +
               "previousArgs=" + previousArgs + ", " +
               "currentInput=" + currentInput + ", " +
               "currentArg=" + currentArg + ']';
    }

}
