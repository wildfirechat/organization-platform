<template>
    <div style="padding: 20px; height: 100%; display: flex; flex-direction: column;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
            <h3 style="margin: 0;">操作日志</h3>
            <el-button type="danger" plain icon="el-icon-delete" @click="clearLogs">清空日志</el-button>
        </div>
        <el-table :data="logs" border stripe style="flex: 1; overflow-y: auto;">
            <el-table-column prop="id" label="ID" width="80"></el-table-column>
            <el-table-column prop="userId" label="操作人" width="180"></el-table-column>
            <el-table-column prop="operation" label="操作" width="180"></el-table-column>
            <el-table-column label="结果" width="100">
                <template slot-scope="scope">
                    <el-tag v-if="scope.row.result === 0" type="success" size="small">成功</el-tag>
                    <el-tag v-else type="danger" size="small">失败</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="value" label="描述"></el-table-column>
            <el-table-column label="时间" width="180">
                <template slot-scope="scope">
                    {{ formatTime(scope.row.timestamp) }}
                </template>
            </el-table-column>
        </el-table>
        <div style="margin-top: 20px; display: flex; justify-content: flex-end;">
            <el-pagination
                background
                layout="total, prev, pager, next"
                :current-page="page + 1"
                :page-size="count"
                :total="totalCount"
                @current-change="handlePageChange">
            </el-pagination>
        </div>
    </div>
</template>

<script>
import api from "@/api/api";

export default {
    name: "OperationLog",
    data() {
        return {
            logs: [],
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
                return '-';
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

</style>
