package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 图书馆系统容量和业务规则配置，便于不同规模部署时调整。
@ConfigurationProperties(prefix = "library")
public class LibraryProperties {
    // 单个读者可同时持有的未还借阅数量。
    private int maxActiveBorrowCount = 3;
    // 所有分页接口允许的最大每页条数，防止大请求拖垮服务。
    private int maxPageSize = 100;
    // 是否在启动时全量修复旧数据；大型部署建议只在迁移窗口打开一次。
    private boolean repairLegacyDataOnStartup = false;

    public int getMaxActiveBorrowCount() {
        return maxActiveBorrowCount;
    }

    public void setMaxActiveBorrowCount(int maxActiveBorrowCount) {
        this.maxActiveBorrowCount = Math.max(1, maxActiveBorrowCount);
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = Math.max(1, maxPageSize);
    }

    public boolean isRepairLegacyDataOnStartup() {
        return repairLegacyDataOnStartup;
    }

    public void setRepairLegacyDataOnStartup(boolean repairLegacyDataOnStartup) {
        this.repairLegacyDataOnStartup = repairLegacyDataOnStartup;
    }

    public int normalizePageSize(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, maxPageSize);
    }
}
