package com.plux.login.adapter;

import java.util.logging.Logger;

public class NMSAdapterFactory {

    private static volatile NMSAdapter instance;

    public static NMSAdapter createAdapter(Logger logger) {
        if (instance != null) {
            return instance;
        }

        VersionUtil.init();

        Logger log = (logger != null) ? logger : Logger.getLogger("PluxLogin");
        String mc = VersionUtil.getMcVersion();

        synchronized (NMSAdapterFactory.class) {
            if (instance != null) {
                return instance;
            }
            if (VersionUtil.isBelow_1_8()) {
                log.info("[PluxLogin] 检测到 1.7.x 服务端 (MC " + mc + ")，使用 v1_7_R4 NMS 反射适配器");
                instance = new NMSAdapter_v1_7_R4();
            } else {
                // 1.8 ~ 26.3+ 统一使用纯 API 现代适配器，无需 NMS 反射，天然跨版本
                log.info("[PluxLogin] 检测到 1.8+ 服务端 (MC " + mc + "，NMS "
                        + (VersionUtil.isVersionedNms() ? VersionUtil.getNMSVersion() : "relocatable")
                        + ")，使用现代 API 适配器");
                instance = new NMSAdapter_Modern();
            }
        }

        return instance;
    }

    public static NMSAdapter getAdapter() {
        return instance;
    }
}
