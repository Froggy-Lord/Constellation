package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): utils/Calculator.java, skyblock/calculators/SignCalculator.java
public final class PhoenixSignCalculator {
    private static final Pattern NUMBER = Pattern.compile("([_,\\d]+\\.?[_,\\d]*)([sekmbtq]?)");
    private static final Map<String, Long> MAGNITUDES = Map.of(
        "s", 64L, "e", 160L, "k", 1_000L, "m", 1_000_000L,
        "b", 1_000_000_000L, "t", 1_000_000_000_000L, "q", 1_000_000_000_000_000L
    );
    private static final Map<String, DoubleUnaryOperator> FUNCTIONS = functions();
    private static PhoenixConfig cfg;
    private static String lastRaw = "";
    private static String lastExpression = "";
    private static double result;
    private static String error = "";
    private static boolean valid;

    private PhoenixSignCalculator() {}

    public static void init(PhoenixConfig config) { cfg = config; }

    public static boolean active() {
        return cfg != null && cfg.enabled && cfg.signCalculator && ConstellationClient.loc().onHypixel();
    }

    public static boolean isInput(String[] messages) {
        if (messages == null || messages.length < 4) return false;
        boolean normal = messages[1].equals("^^^^^^^^^^^^^^^")
            && !messages[2].endsWith("your") && !messages[2].endsWith("query");
        boolean alternate = messages[1].equals("^^^^^^") && !messages[2].endsWith("your")
            && !messages[2].equals("Enter name");
        return normal || alternate || messages[1].equals("^^Flipping^^");
    }

    public static Component preview(String raw) {
        calculate(raw);
        if (raw == null || raw.isBlank()) return Component.empty();
        if (cfg.signCalculatorRequiresEquals && !raw.startsWith("=")) return Component.empty();
        if (!valid) return Component.literal(error).withColor(0xFF5555);
        return Component.literal(lastExpression + " = " + formatted(result)).withColor(0x55FF55);
    }

    public static String resolve(String raw, boolean price) {
        calculate(raw);
        if (!valid) return raw;
        String value;
        if (price) {
            int places = Math.clamp(cfg.signCalculatorDecimalPlaces, 0, 8);
            double scale = Math.pow(10, places);
            double rounded = Math.round(result * scale) / scale;
            value = places == 0 ? Long.toString(Math.round(rounded))
                : stripZeros(String.format(Locale.ROOT, "%." + places + "f", rounded));
        } else {
            value = Long.toString(Math.round(result));
        }
        int limit = Math.clamp(cfg.signCalculatorMaxLength, 1, 15);
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    public static double evaluate(String expression) {
        if (expression == null) throw new CalculationException("Enter an equation.");
        String input = expression.replace(" ", "").toLowerCase(Locale.ROOT).replace('x', '*').replace('×', '*');
        if (input.startsWith(".")) input = "0" + input;
        List<Token> rpn = shunt(lex(input));
        Deque<Double> values = new ArrayDeque<>();
        for (Token token : rpn) {
            switch (token.type) {
                case NUMBER -> values.push(number(token.value));
                case OPERATOR -> {
                    if (values.size() < 2) throw new CalculationException("An operator is missing a value.");
                    double right = values.pop(), left = values.pop();
                    double value = switch (token.value) {
                        case "+" -> left + right;
                        case "-" -> left - right;
                        case "*" -> left * right;
                        case "/" -> {
                            if (right == 0) throw new CalculationException("Cannot divide by zero.");
                            yield left / right;
                        }
                        case "%" -> {
                            if (right == 0) throw new CalculationException("Cannot use modulo zero.");
                            yield left % right;
                        }
                        case "^" -> Math.pow(left, right);
                        default -> throw new CalculationException("Unknown operator.");
                    };
                    if (!Double.isFinite(value)) throw new CalculationException("The result is too large.");
                    values.push(value);
                }
                case FUNCTION -> {
                    if (values.isEmpty()) throw new CalculationException("A function is missing a value.");
                    double value;
                    try { value = FUNCTIONS.get(token.value).applyAsDouble(values.pop()); }
                    catch (ArithmeticException exception) { throw new CalculationException(exception.getMessage()); }
                    if (!Double.isFinite(value)) throw new CalculationException("Invalid function input.");
                    values.push(value);
                }
                default -> throw new CalculationException("Parentheses are not balanced.");
            }
        }
        if (values.size() != 1) throw new CalculationException(values.isEmpty()
            ? "Enter an equation." : "The equation has extra values.");
        return values.pop();
    }

    private static void calculate(String raw) {
        String input = raw == null ? "" : raw;
        lastRaw = input;
        lastExpression = input.startsWith("=") ? input.substring(1) : input;
        valid = false;
        error = "";
        if (cfg.signCalculatorRequiresEquals && !input.startsWith("=")) return;
        try {
            result = evaluate(lastExpression);
            valid = true;
        } catch (CalculationException exception) {
            error = exception.getMessage();
        }
    }

    private static List<Token> lex(String input) {
        List<Token> tokens = new ArrayList<>();
        for (int index = 0; index < input.length();) {
            char character = input.charAt(index);
            if ("+-*/%^".indexOf(character) >= 0) {
                if (tokens.isEmpty() || tokens.getLast().type == Type.OPERATOR || tokens.getLast().type == Type.LEFT)
                    throw new CalculationException("An operator is missing a value.");
                tokens.add(new Token(Type.OPERATOR, Character.toString(character)));
                index++;
            } else if (character == '(') {
                if (!tokens.isEmpty() && (tokens.getLast().type == Type.NUMBER || tokens.getLast().type == Type.RIGHT))
                    tokens.add(new Token(Type.OPERATOR, "*"));
                tokens.add(new Token(Type.LEFT, "("));
                index++;
            } else if (character == ')') {
                tokens.add(new Token(Type.RIGHT, ")"));
                index++;
            } else if (Character.isDigit(character)) {
                Matcher matcher = NUMBER.matcher(input.substring(index));
                if (!matcher.lookingAt()) throw new CalculationException("Invalid number.");
                tokens.add(new Token(Type.NUMBER, matcher.group()));
                index += matcher.end();
            } else if (character == '.' || character == '_' || character == ',') {
                throw new CalculationException("Invalid number.");
            } else if (character == 'p') {
                if (!cfg.signCalculatorUsePurse) throw new CalculationException("Purse input is disabled.");
                tokens.add(new Token(Type.NUMBER, Long.toString(Math.max(0, LyraEconomy.currentPurse))));
                index += input.startsWith("purse", index) ? 5 : 1;
            } else if (Character.isLetter(character)) {
                int end = index + 1;
                while (end < input.length() && Character.isLetter(input.charAt(end))) end++;
                String function = input.substring(index, end);
                if (!FUNCTIONS.containsKey(function)) throw new CalculationException("Unknown function: " + function);
                if (!tokens.isEmpty() && (tokens.getLast().type == Type.NUMBER || tokens.getLast().type == Type.RIGHT))
                    tokens.add(new Token(Type.OPERATOR, "*"));
                tokens.add(new Token(Type.FUNCTION, function));
                index = end;
            } else {
                throw new CalculationException("Invalid character: " + character);
            }
        }
        return tokens;
    }

    private static List<Token> shunt(List<Token> tokens) {
        List<Token> output = new ArrayList<>();
        Deque<Token> operators = new ArrayDeque<>();
        for (Token token : tokens) {
            switch (token.type) {
                case NUMBER -> output.add(token);
                case OPERATOR -> {
                    while (!operators.isEmpty() && operators.peek().type != Type.LEFT
                        && (operators.peek().type == Type.FUNCTION
                        || precedence(operators.peek().value) > precedence(token.value)
                        || precedence(operators.peek().value) == precedence(token.value) && !token.value.equals("^")))
                        output.add(operators.pop());
                    operators.push(token);
                }
                case FUNCTION, LEFT -> operators.push(token);
                case RIGHT -> {
                    while (!operators.isEmpty() && operators.peek().type != Type.LEFT) output.add(operators.pop());
                    if (operators.isEmpty()) throw new CalculationException("Parentheses are not balanced.");
                    operators.pop();
                    if (!operators.isEmpty() && operators.peek().type == Type.FUNCTION) output.add(operators.pop());
                }
            }
        }
        while (!operators.isEmpty()) {
            Token token = operators.pop();
            if (token.type == Type.LEFT || token.type == Type.RIGHT)
                throw new CalculationException("Parentheses are not balanced.");
            output.add(token);
        }
        return output;
    }

    private static double number(String raw) {
        Matcher matcher = NUMBER.matcher(raw);
        if (!matcher.matches()) throw new CalculationException("Invalid number.");
        double number;
        try { number = Double.parseDouble(matcher.group(1).replace("_", "").replace(",", "")); }
        catch (NumberFormatException exception) { throw new CalculationException("Invalid number."); }
        String magnitude = matcher.group(2);
        if (!magnitude.isBlank()) number *= MAGNITUDES.get(magnitude);
        if (!Double.isFinite(number)) throw new CalculationException("The number is too large.");
        return number;
    }

    private static int precedence(String operator) {
        return switch (operator) {
            case "+", "-" -> 0;
            case "*", "/", "%" -> 1;
            case "^" -> 2;
            default -> 3;
        };
    }

    private static Map<String, DoubleUnaryOperator> functions() {
        Map<String, DoubleUnaryOperator> functions = new HashMap<>();
        functions.put("sqrt", value -> { if (value < 0) throw new ArithmeticException("Square root needs a positive value."); return Math.sqrt(value); });
        functions.put("log", value -> { if (value <= 0) throw new ArithmeticException("Log needs a positive value."); return Math.log10(value); });
        functions.put("lg", value -> { if (value <= 0) throw new ArithmeticException("Log needs a positive value."); return Math.log(value) / Math.log(2); });
        functions.put("ln", value -> { if (value <= 0) throw new ArithmeticException("Log needs a positive value."); return Math.log(value); });
        functions.put("factorial", value -> {
            if (value < 0 || value > 170 || value != Math.floor(value))
                throw new ArithmeticException("Factorial needs an integer from 0 to 170.");
            double result = 1;
            for (int index = 2; index <= (int) value; index++) result *= index;
            return result;
        });
        functions.put("sin", value -> Math.sin(Math.toRadians(value)));
        functions.put("cos", value -> Math.cos(Math.toRadians(value)));
        functions.put("tan", value -> Math.tan(Math.toRadians(value)));
        functions.put("asin", value -> { if (value < -1 || value > 1) throw new ArithmeticException("Asin needs -1 to 1."); return Math.toDegrees(Math.asin(value)); });
        functions.put("acos", value -> { if (value < -1 || value > 1) throw new ArithmeticException("Acos needs -1 to 1."); return Math.toDegrees(Math.acos(value)); });
        functions.put("atan", value -> Math.toDegrees(Math.atan(value)));
        functions.put("sinh", Math::sinh);
        functions.put("cosh", Math::cosh);
        functions.put("tanh", value -> value > 20 ? 1 : value < -20 ? -1 : Math.tanh(value));
        functions.put("abs", Math::abs);
        functions.put("floor", Math::floor);
        functions.put("ceil", Math::ceil);
        functions.put("round", Math::round);
        return Map.copyOf(functions);
    }

    private static String formatted(double value) {
        int places = Math.clamp(cfg.signCalculatorDecimalPlaces, 0, 8);
        return stripZeros(String.format(Locale.ROOT, "%,." + places + "f", value));
    }

    private static String stripZeros(String value) {
        if (!value.contains(".")) return value;
        return value.replaceFirst("0+$", "").replaceFirst("\\.$", "");
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("signcalculator")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "state"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("decimals")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("places", IntegerArgumentType.integer(0, 8))
                    .executes(context -> decimals(IntegerArgumentType.getInteger(context, "places")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("length")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("characters", IntegerArgumentType.integer(1, 15))
                    .executes(context -> length(IntegerArgumentType.getInteger(context, "characters"))))));
    }

    private static int option(String name, String raw) {
        Boolean value = state(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.signCalculator = value;
            case "equals" -> cfg.signCalculatorRequiresEquals = value;
            case "preview" -> cfg.signCalculatorPreview = value;
            case "purse" -> cfg.signCalculatorUsePurse = value;
            default -> { local("Option must be enabled, equals, preview, or purse."); return 0; }
        }
        save();
        return status();
    }

    private static int decimals(int value) { cfg.signCalculatorDecimalPlaces = value; save(); return status(); }
    private static int length(int value) { cfg.signCalculatorMaxLength = value; save(); return status(); }
    private static void save() { ConstellationClient.saveConfig(); }
    private static int status() {
        local("Sign calculator " + on(cfg.signCalculator) + ", equals " + on(cfg.signCalculatorRequiresEquals)
            + ", preview " + on(cfg.signCalculatorPreview) + ", purse " + on(cfg.signCalculatorUsePurse)
            + ", decimals " + cfg.signCalculatorDecimalPlaces
            + ", length " + cfg.signCalculatorMaxLength + ".");
        return 1;
    }
    private static Boolean state(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void local(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("§6[Sign Calculator] §f" + text));
    }

    private enum Type { NUMBER, OPERATOR, FUNCTION, LEFT, RIGHT }
    private record Token(Type type, String value) {}
    public static final class CalculationException extends IllegalArgumentException {
        public CalculationException(String message) { super(message); }
    }
}
