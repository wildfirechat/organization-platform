<template>
    <div class="usage-today">
        <div class="usage-head">
            <div class="usage-dept">
                <span class="dept-name wf-ellipsis">{{ department.name }}</span>
                <el-tag size="small" type="info" effect="plain" round>含所有子部门</el-tag>
            </div>
            <div class="usage-summary">
                <span class="summary-item">
                    <span class="dot dot-online"></span>在线
                    <span class="wf-num summary-num online">{{ onlineCount }}</span>人
                </span>
                <span class="summary-item">
                    <span class="dot dot-offline"></span>未登录
                    <span class="wf-num summary-num">{{ notLoggedInCount }}</span>人
                </span>
                <span class="summary-item">共 <span class="wf-num summary-num">{{ totalCount }}</span> 人</span>
            </div>
            <el-button type="primary" plain :icon="Download" :loading="downloading"
                       :disabled="totalCount === 0" @click="downloadCsv">
                下载数据
            </el-button>
        </div>

        <el-table :data="rows" v-loading="loading" height="380"
                  :row-class-name="rowClassName"
                  empty-text="该部门下没有成员">
            <el-table-column label="状态" width="90">
                <template v-slot="scope">
                    <el-tag v-if="scope.row.status === 0" size="small" type="success" effect="light">在线</el-tag>
                    <el-tag v-else size="small" type="info" effect="plain">未登录</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="成员" min-width="170">
                <template v-slot="scope">
                    <div class="member-cell">
                        <el-avatar :size="30" :src="scope.row.portraitUrl">
                            {{ (scope.row.name || scope.row.employeeId || '?').slice(0, 1) }}
                        </el-avatar>
                        <div class="member-meta">
                            <span class="member-name wf-ellipsis">{{ scope.row.name || '未知成员' }}</span>
                            <span class="member-id wf-ellipsis wf-muted">{{ scope.row.employeeId }}</span>
                        </div>
                    </div>
                </template>
            </el-table-column>
            <el-table-column label="部门" min-width="130" show-overflow-tooltip>
                <template v-slot="scope">
                    <span v-if="scope.row.departmentName">{{ scope.row.departmentName }}</span>
                    <span v-else class="wf-muted">—</span>
                </template>
            </el-table-column>
            <el-table-column label="手机号" min-width="125">
                <template v-slot="scope">
                    <span v-if="scope.row.mobile" class="wf-num">{{ scope.row.mobile }}</span>
                    <span v-else class="wf-muted">—</span>
                </template>
            </el-table-column>
            <el-table-column label="平台" width="100">
                <template v-slot="scope">
                    <el-tag v-if="scope.row.status === 0" size="small" effect="plain">
                        {{ platformName(scope.row.platform) }}
                    </el-tag>
                    <span v-else class="wf-muted">—</span>
                </template>
            </el-table-column>
            <el-table-column label="客户端IP" width="130">
                <template v-slot="scope">
                    <span v-if="scope.row.ip" class="wf-num">{{ scope.row.ip }}</span>
                    <span v-else class="wf-muted">—</span>
                </template>
            </el-table-column>
            <el-table-column label="登录时间" width="165">
                <template v-slot="scope">
                    <span class="wf-num">{{ formatTime(scope.row.loginTime) }}</span>
                </template>
            </el-table-column>
        </el-table>

        <el-pagination
            v-if="totalCount > pageSize"
            background
            layout="total, prev, pager, next"
            :current-page="page + 1"
            :page-size="pageSize"
            :total="totalCount"
            @current-change="handlePageChange"/>

        <div class="wf-dialog-actions">
            <el-button @click="onClose()">关闭</el-button>
        </div>
    </div>
</template>

<script>
import api from "@/api/api";
import {Download} from '@element-plus/icons-vue';

export default {
    name: "UsageToday",
    props: {
        // 被点开菜单的部门节点
        department: {
            type: Object,
            required: true,
        },
        onClose: {
            type: Function,
            required: true,
        }
    },
    data() {
        return {
            Download,
            rows: [],
            loading: false,
            downloading: false,
            page: 0,
            pageSize: 20,
            totalCount: 0,
            onlineCount: 0,
            notLoggedInCount: 0,
        }
    },
    created() {
        this.loadUsage();
    },
    methods: {
        async loadUsage() {
            if (!this.department || !this.department.id) {
                return;
            }
            this.loading = true;
            try {
                const result = await api.getTodayUsage(this.department.id, this.page, this.pageSize);
                this.rows = (result && result.contents) || [];
                this.totalCount = (result && result.totalCount) || 0;
                this.onlineCount = (result && result.onlineCount) || 0;
                this.notLoggedInCount = (result && result.notLoggedInCount) || 0;
            } catch (e) {
                console.error('load today usage failed', e);
                // 业务错误拦截器已经弹过提示了，这里只兜住网络层面的异常，避免重复提示
                if (!e || e.code === undefined) {
                    this.$message.error('加载今日使用情况失败');
                }
            } finally {
                this.loading = false;
            }
        },
        handlePageChange(currentPage) {
            this.page = currentPage - 1;
            this.loadUsage();
        },
        async downloadCsv() {
            if (!this.department || !this.department.id || this.downloading) {
                return;
            }
            this.downloading = true;
            try {
                // 接口返回的是 CSV 文本，且开头已带 UTF-8 BOM，Excel 双击打开不会乱码
                const csv = await api.exportTodayUsage(this.department.id);
                const blob = new Blob([csv], {type: 'text/csv;charset=utf-8'});
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = this.buildFileName();
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                URL.revokeObjectURL(url);
            } catch (e) {
                console.error('export today usage failed', e);
                if (!e || e.code === undefined) {
                    this.$message.error('导出失败');
                }
            } finally {
                this.downloading = false;
            }
        },
        buildFileName() {
            // 部门名里可能有不能做文件名的字符，替换掉
            const name = ((this.department && this.department.name) || '部门').replace(/[\\/:*?"<>|]/g, '_');
            const now = new Date();
            const pad = (n) => n < 10 ? '0' + n : n;
            const stamp = '' + now.getFullYear() + pad(now.getMonth() + 1) + pad(now.getDate());
            return `今日使用情况-${name}-${stamp}.csv`;
        },
        // 未登录的行整体浅一点，让“上面在线、下面未登录”两段更清楚
        rowClassName({row}) {
            return row.status === 0 ? '' : 'row-not-logged-in';
        },
        /**
         * 平台枚举取自本工程自带的野火 SDK：
         * src/lib/common-*.jar 里 cn.wildfirechat.proto.ProtoConstants.Platform
         * 已用真实数据验证：某会话 _platform = 1 且 _phone_name = "iPhone 17 Pro"，
         * 故 1 = iOS 成立；官方“数据库表结构说明”该字段写的是 0-iOS 起，整体差一位，以 SDK 为准。
         * 未覆盖的取值原样显示数值便于排查。
         */
        platformName(platform) {
            const names = {
                0: '未知',
                1: 'iOS',
                2: 'Android',
                3: 'Windows',
                4: 'macOS',
                5: 'Web',
                6: '微信',
                7: 'Linux',
                8: 'iPad',
                9: 'Android Pad',
            };
            return names[platform] !== undefined ? names[platform] : `其他(${platform})`;
        },
        formatTime(timestamp) {
            if (!timestamp) {
                return '—';
            }
            const date = new Date(timestamp);
            const pad = (n) => n < 10 ? '0' + n : n;
            return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + ' ' +
                pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds());
        }
    }
}
</script>

<style scoped>
.usage-head {
    display: flex;
    align-items: center;
    gap: 12px;
    padding-bottom: 12px;
    margin-bottom: 12px;
    border-bottom: 1px solid var(--wf-border-light);
}

.usage-dept {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    min-width: 0;
}

.dept-name {
    font-size: 15px;
    font-weight: 600;
    color: var(--wf-text-1);
}

.usage-summary {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-shrink: 0;
    font-size: 13px;
    color: var(--wf-text-3);
}

.summary-item {
    display: flex;
    align-items: center;
    gap: 4px;
}

.summary-num {
    font-size: 15px;
    font-weight: 600;
    color: var(--wf-text-1);
}

.summary-num.online {
    color: var(--wf-success, #34a853);
}

.dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
}

.dot-online {
    background: var(--wf-success, #34a853);
}

.dot-offline {
    background: var(--wf-text-3);
}

.member-cell {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
}

.member-cell :deep(.el-avatar) {
    flex-shrink: 0;
    background: var(--wf-brand-bg);
    color: var(--wf-brand);
    font-size: 13px;
}

.member-meta {
    display: flex;
    flex-direction: column;
    min-width: 0;
    line-height: 18px;
}

.member-name {
    font-weight: 500;
}

.member-id {
    font-size: 12px;
}

/* 未登录的行弱化一点，让两段分界更明显 */
.usage-today :deep(.row-not-logged-in) {
    color: var(--wf-text-3);
}

.usage-today :deep(.el-pagination) {
    justify-content: flex-end;
    padding-top: 12px;
}
</style>
