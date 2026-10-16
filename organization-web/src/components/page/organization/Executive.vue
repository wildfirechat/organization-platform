<template>
    <div class="executive-page">
        <PageHeader title="高管管理"
                    description="维护高管名单：名单里的员工在组织架构里会带「高管」标记，可随时添加或移除">
            <template #extra>
                <el-button type="primary" :icon="Plus" @click="openAddDialog">添加高管</el-button>
            </template>
        </PageHeader>

        <div class="wf-panel executive-panel">
            <el-table :data="executives" v-loading="loading" empty-text="还没有高管，点右上角「添加高管」" height="100%">
                <el-table-column label="姓名" min-width="180">
                    <template v-slot="scope">
                        <div class="member-cell">
                            <el-avatar :size="28" :src="scope.row.portraitUrl">
                                {{ (scope.row.name || '?').slice(0, 1) }}
                            </el-avatar>
                            <span class="wf-ellipsis">{{ scope.row.name || scope.row.employeeId }}</span>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column prop="mobile" label="手机号" width="150">
                    <template v-slot="scope">
                        <span v-if="scope.row.mobile" class="wf-num">{{ scope.row.mobile }}</span>
                        <span v-else class="wf-muted">—</span>
                    </template>
                </el-table-column>
                <el-table-column label="部门" min-width="180">
                    <template v-slot="scope">
                        <span v-if="scope.row.departmentName">{{ scope.row.departmentName }}</span>
                        <span v-else class="wf-muted">—</span>
                    </template>
                </el-table-column>
                <el-table-column label="职务" min-width="150">
                    <template v-slot="scope">
                        <span v-if="scope.row.title">{{ scope.row.title }}</span>
                        <span v-else class="wf-muted">—</span>
                    </template>
                </el-table-column>
                <el-table-column label="加入时间" width="180">
                    <template v-slot="scope">
                        <span class="wf-num wf-muted">{{ formatTime(scope.row.createdAt) }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="90" align="center">
                    <template v-slot="scope">
                        <el-button link type="danger" @click="removeExecutive(scope.row)">移除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </div>

        <el-dialog v-model="showAddDialog" append-to-body title="添加高管" width="760px">
            <ChooseMember :on-cancel="() => showAddDialog = false" :on-confirm="onChosen"/>
        </el-dialog>
    </div>
</template>

<script>
import api from "@/api/api";
import PageHeader from "@/components/common/PageHeader.vue";
import ChooseMember from "@/components/page/organization/dialog/ChooseMember.vue";
import {useOrgStore} from "@/store/stores/orgStore";
import {Plus} from '@element-plus/icons-vue';

export default {
    name: "Executive",
    components: {PageHeader, ChooseMember},
    setup() {
        const orgStore = useOrgStore();
        return {orgStore};
    },
    data() {
        return {
            Plus,
            executives: [],
            loading: false,
            showAddDialog: false
        }
    },
    created() {
        this.loadExecutives();
    },
    methods: {
        async loadExecutives() {
            this.loading = true;
            try {
                const result = await api.listExecutives();
                this.executives = result || [];
            } catch (e) {
                console.error('load executives failed', e);
            } finally {
                this.loading = false;
            }
        },
        async openAddDialog() {
            // 直接进本页时组织树可能还没加载，选择成员需要它
            if (!this.orgStore.rootOrganizationsLoaded) {
                try {
                    await this.orgStore.getRootOrganizationsWithChildren();
                } catch (e) {
                    console.error('load organizations failed', e);
                    this.$message.error('加载组织架构失败，请稍后重试');
                    return;
                }
            }
            this.showAddDialog = true;
        },
        async onChosen(members) {
            const ids = (members || []).map(m => m.employeeId).filter(Boolean);
            this.showAddDialog = false;
            if (!ids.length) {
                return;
            }
            try {
                const result = await api.addExecutives(ids);
                const added = result && result.added != null ? result.added : ids.length;
                this.$message.success('已添加 ' + added + ' 位高管');
                await this.loadExecutives();
            } catch (e) {
                console.error('add executives failed', e);
            }
        },
        async removeExecutive(row) {
            try {
                await this.$confirm('确定把「' + (row.name || row.employeeId) + '」移出高管名单吗？', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                });
            } catch (e) {
                return; // 取消
            }
            try {
                await api.deleteExecutive(row.employeeId);
                this.$message.success('已移除');
                await this.loadExecutives();
            } catch (e) {
                console.error('remove executive failed', e);
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
.executive-page {
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
}

.executive-panel {
    flex: 1;
    min-height: 0;
}

.member-cell {
    display: flex;
    align-items: center;
    gap: 8px;
    overflow: hidden;
}
</style>
