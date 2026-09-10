<template>
    <div class="delete-employee">
        <div class="profile">
            <el-avatar :size="52" :src="employee.portraitUrl">
                {{ (employee.name || '').slice(0, 1) }}
            </el-avatar>
            <div class="profile-meta">
                <p class="name">{{ employee.name }}</p>
                <p class="id wf-muted">{{ `用户 ID: ${employee.employeeId}` }}</p>
            </div>
        </div>

        <el-alert type="warning" :closable="false" show-icon
                  title="离职后该成员将从所在部门中移除"
                  description="此操作不可撤销，请确认无误后再继续"/>

        <el-checkbox v-model="destroyIMUser" class="destroy-check">同时删除对应的 IM 用户</el-checkbox>

        <div class="wf-dialog-actions">
            <el-button @click="onDeleteEmployee(false)">取消</el-button>
            <el-button type="danger" @click="onConfirm">确认离职</el-button>
        </div>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";

export default {
    name: "DeleteEmployee",
    props: {
        employee: {
            type: Object,
            required: true,
        },
        onDeleteEmployee: {
            type: Function,
            required: true,
        }
    },

    setup() {
        const orgStore = useOrgStore();
        return {orgStore};
    },

    data() {
        return {
            destroyIMUser: false,
        }
    },
    methods: {
        onConfirm() {
            this.orgStore.deleteEmployee({employeeId: this.employee.employeeId, destroyIMUser: this.destroyIMUser})
                .then(() => {
                    this.onDeleteEmployee(true);
                })
                .catch(e => {
                    console.error('deleteEmployee error', e);
                    this.onDeleteEmployee(false);
                })
        }
    }
}
</script>

<style scoped>
.profile {
    display: flex;
    align-items: center;
    gap: 14px;
    padding-bottom: 16px;
    margin-bottom: 16px;
    border-bottom: 1px solid var(--wf-border-light);
}

.profile :deep(.el-avatar) {
    background: var(--wf-brand-bg);
    color: var(--wf-brand);
    font-size: 18px;
}

.name {
    font-size: 16px;
    font-weight: 600;
    color: var(--wf-text-1);
}

.id {
    margin-top: 2px;
    font-size: 13px;
}

.destroy-check {
    margin-top: 16px;
}
</style>
