package com.drtshock.playervaults.util;

public class Permission {
    private static final String PREFIX = "playervaults.";

    public static final String ADMIN = PREFIX + "admin";
    public static final String BYPASS_BLOCKED_ITEMS = PREFIX + "bypassblockeditems";
    public static final String COMMANDS_SEARCH = PREFIX + "commands.search";
    public static final String COMMANDS_USE = PREFIX + "commands.use";
    public static final String CONVERT = PREFIX + "convert";
    public static final String DELETE = PREFIX + "delete";
    public static final String DELETE_ALL = PREFIX + "delete.all";
    public static final String FREE = PREFIX + "free";

    public static final String SIGNS_BYPASS = PREFIX + "signs.bypass";
    public static final String SIGNS_USE = PREFIX + "signs.use";
    public static final String SIGNS_SET = PREFIX + "signs.set";

    private static final String AMOUNT_PREFIX = PREFIX + "amount.";
    private static final String SIZE_PREFIX = PREFIX + "size.";

    public static String amount(int amount) {
        return AMOUNT_PREFIX + amount;
    }

    public static String size(int size) {
        return SIZE_PREFIX + size;
    }
}
