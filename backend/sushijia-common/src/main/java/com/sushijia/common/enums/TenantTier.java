package com.sushijia.common.enums;

/**
 * 租户套餐版本
 */
public enum TenantTier {
    TRIAL("trial", "试用版", 500),
    BASIC("basic", "基础版", 0),
    PRO("pro", "专业版", 0),
    FLAGSHIP("flagship", "旗舰版", 0);

    private final String key;
    private final String label;
    private final int freeCredits;

    TenantTier(String key, String label, int freeCredits) {
        this.key = key;
        this.label = label;
        this.freeCredits = freeCredits;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public int getFreeCredits() { return freeCredits; }

    public static TenantTier fromKey(String key) {
        for (TenantTier t : values()) {
            if (t.key.equals(key)) return t;
        }
        return TRIAL;
    }
}
