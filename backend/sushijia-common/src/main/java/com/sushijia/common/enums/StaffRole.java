package com.sushijia.common.enums;

/**
 * 酒店端员工角色 —— 与前端 auth.ts 对齐
 */
public enum StaffRole {
    ADMIN("admin", "超级管理员"),
    MANAGER("manager", "店长"),
    FRONT_DESK("front_desk", "前台客服"),
    MARKETING("marketing", "营销专员");

    private final String key;
    private final String label;

    StaffRole(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
}
