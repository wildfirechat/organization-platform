<template>
    <div class="log-page">
        <PageHeader title="操作日志" description="记录管理后台对部门与成员的每一次变更，便于事后追溯">
            <template #extra>
                <el-button type="danger" plain :icon="Delete" @click="clearLogs">清空日志</el-button>
            </template>
        </PageHeader>

        <div class="wf-panel log-panel">
            <el-table :data="logs" v-loading="loading" empty-text="暂无操作日志" class="log-table" height="100%">
                <el-table-column prop="id" label="ID" width="80">
                    <template v-slot="scope">
                        <span class="wf-num wf-muted">{{ scope.row.id }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="userId" label="操作人" width="180" show-overflow-tooltip/>
                <el-table-column prop="operation" label="操作" width="180" show-overflow-tooltip/>
                <el-table-column label="结果" width="90" align="center">
                    <template v-slot="scope">
                        <el-tag v-if="scope.row.result === 0" type="success" size="small" effect="light">成功</el-tag>
                        <el-tag v-else type="danger" size="small" effect="light">失败</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="value" label="描述" min-width="220" show-overflow-tooltip>
                    <template v-slot="scope">
                        <span v-if="scope.row.value">{{ scope.row.value }}</span>
                        <span v-else class="wf-muted">—</span>
                    </template>
                </el-table-column>
                <el-table-column label="时间" width="180">
                    <template v-slot="scope">
                        <span class="wf-num">{{ formatTime(scope.row.timestamp) }}</span>
                    </template>
                </el-table-column>
            </el-table>

            <el-pagination
                v-if="totalCount > 0"
                background
                layout="total, prev, pager, next"
                :current-page="page + 1"
                :page-size="count"
                :total="totalCount"
                @current-change="handlePageChange"/>
        </div>
    </div>
</template>

<script>
import api from "@/api/api";
import {Delete} from '@element-plus/icons-vue';
import PageHeader from "@/components/common/PageHeader.vue";

export default {
    name: "OperationLog",
    components: {PageHeader},
    data() {
        return {
            Delete,
            logs: [],
            loading: false,
            page: 0,
            count: 20,
            totalCount: 0,
            totalPages: 0
        }
    },
    created() {
        this.loadLogs();
    },
    methods: {
        async loadLogs() {
            this.loading = true;
            try {
                const result = await api.getLogs(this.page, this.count);
                if (result) {
                    this.logs = result.contents || [];
                    this.totalCount = result.totalCount || 0;
                    this.totalPages = result.totalPages || 0;
                }
            } catch (e) {
                console.error('load operation logs failed', e);
                this.$message.error('加载操作日志失败');
            } finally {
                this.loading = false;
            }
        },
        handlePageChange(currentPage) {
            this.page = currentPage - 1;
            this.loadLogs();
        },
        async clearLogs() {
            try {
                await this.$confirm('确定要清空所有操作日志吗？清空后不可恢复。', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                });
                await api.clearLogs();
                this.$message.success('日志已清空');
                this.page = 0;
                await this.loadLogs();
            } catch (e) {
                if (e !== 'cancel') {
                    console.error('clear logs failed', e);
                    this.$message.error('清空日志失败');
                }
            }
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
.log-page {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
}

/* 表格占满剩余高度，分页固定在卡片底部 */
.log-panel {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 360px;
    padding-bottom: 12px;
}

.log-table {
    flex: 1;
    min-height: 0;
}
</style>
