package com.minecraftdiscordbridge.paper;

final class PaperProxy {
    private static final String GLOBAL_CONFIGURATION = "io.papermc.paper.configuration.GlobalConfiguration";

    private PaperProxy() {}

    static boolean secureForwarding() {
        try {
            Class<?> global = Class.forName(GLOBAL_CONFIGURATION);
            Object config = global.getMethod("get").invoke(null);
            Object proxies = global.getField("proxies").get(config);
            Object velocity = proxies.getClass().getField("velocity").get(proxies);
            Class<?> settings = velocity.getClass();
            return settings.getField("enabled").getBoolean(velocity)
                    && settings.getField("onlineMode").getBoolean(velocity);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unknown) {
            return false;
        }
    }
}
