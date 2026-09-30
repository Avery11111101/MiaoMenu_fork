package com.fluxcraft.MiaoMenu.constants;

public final class Constants {

    private Constants() {}

    public static class Config {
        public static final int INVENTORY_MAX_ROWS = 6;
        public static final int INVENTORY_MIN_ROWS = 1;
        public static final int INVENTORY_ROW_SIZE = 9;
        public static final int TITLE_MAX_LENGTH = 32;
        public static final int DEFAULT_MENU_ROWS = 3;
    }

    public static class ConfigKeys {
        public static final String MENU_ITEMS = "menu.items";
        public static final String TEXT = "text";
        public static final String ICON = "icon";
        public static final String ICON_TYPE = "icon_type";
        public static final String DEFAULT_ICON_TYPE = "path";
        public static final String ICON_TYPE_URL = "url";
        public static final String ICON_TYPE_PATH = "path";
        public static final String COMMAND = "command";
        public static final String EXECUTE_AS = "execute_as";
        public static final String UNSUPPORTED_ON_BEDROCK = "unsupported_on_bedrock";
        public static final String MENU_TITLE = "menu.title";
        public static final String DEFAULT_TITLE = "Menu";
    }

    public static String stripLeadingSlash(String command) {
        if (command == null) return "";
        return command.startsWith("/") ? command.substring(1) : command;
    }
}