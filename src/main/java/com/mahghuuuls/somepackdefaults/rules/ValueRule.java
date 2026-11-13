package com.mahghuuuls.somepackdefaults.rules;

/**
 * What one server line accepts. A value passes only when vanilla 1.12.2 would use it unchanged; a
 * value vanilla would silently replace, clamp, or round is refused.
 */
abstract class ValueRule {

    /** Returns null when the value passes, otherwise what is wrong with it. */
    abstract String check(String value);

    /** The allowed values, for messages. */
    abstract String allowed();

    static ValueRule bool() {
        return new ValueRule() {
            @Override
            String check(String value) {
                // Vanilla reads these with Boolean.parseBoolean: anything but "true" silently means false.
                return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) ? null : "not true or false";
            }

            @Override
            String allowed() {
                return "true or false";
            }
        };
    }

    static ValueRule integer(final long min, final long max, final long step) {
        return new ValueRule() {
            @Override
            String check(String value) {
                long number;
                try {
                    number = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return "not a whole number";
                }
                if (number < min || number > max) {
                    return "out of range";
                }
                if (step > 1 && (number - min) % step != 0) {
                    return "not a step of " + step;
                }
                return null;
            }

            @Override
            String allowed() {
                String range = min + " to " + max;
                return step > 1 ? range + ", in steps of " + step : range;
            }
        };
    }

    static ValueRule integer(long min, long max) {
        return integer(min, max, 1);
    }

    /** Any whole number in the range of a Java int, which is how vanilla reads these lines. */
    static ValueRule anyInteger() {
        return new ValueRule() {
            @Override
            String check(String value) {
                try {
                    Integer.parseInt(value);
                    return null;
                } catch (NumberFormatException e) {
                    return "not a whole number";
                }
            }

            @Override
            String allowed() {
                return "a whole number";
            }
        };
    }

    static ValueRule anyLong() {
        return new ValueRule() {
            @Override
            String check(String value) {
                try {
                    Long.parseLong(value);
                    return null;
                } catch (NumberFormatException e) {
                    return "not a whole number";
                }
            }

            @Override
            String allowed() {
                return "a whole number";
            }
        };
    }

    static ValueRule anyText() {
        return new ValueRule() {
            @Override
            String check(String value) {
                return null;
            }

            @Override
            String allowed() {
                return "any text";
            }
        };
    }

    static ValueRule nonEmptyText() {
        return new ValueRule() {
            @Override
            String check(String value) {
                return value.isEmpty() ? "empty" : null;
            }

            @Override
            String allowed() {
                return "any text that is not empty";
            }
        };
    }
}
