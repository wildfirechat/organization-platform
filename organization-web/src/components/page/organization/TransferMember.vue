<template>
    <div v-if="employee">
        <p class="lead">将 <b>{{ employee.name }}</b> 变更至以下部门：</p>

        <PickerField :items="selectedDepartments" placeholder="点击选择目标部门（可多选）"
                     @open="showChooseDepartmentDialog = true" @remove="onUncheckDepartment"/>

        <div class="wf-dialog-actions">
            <el-button @click="onCancel">取消</el-button>
            <el-button type="primary" :disabled="selectedDepartments.length === 0" @click="confirmTransfer">
                确定
            </el-button>
        </div>

        <el-dialog v-model="showChooseDepartmentDialog" append-to-body title="选择部门" width="760px">
            <ChooseDepartment
                :on-cancel="() => showChooseDepartmentDialog = false"
                :on-confirm="onCheckDepartment"/>
        </el-dialog>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";
import ChooseDepartment from "@/components/page/organization/dialog/ChooseDepartment.vue";
import PickerField from "@/components/common/PickerField.vue";

export default {
    name: "TransferMember",
    components: {PickerField, ChooseDepartment},
    props: {
        employee: {
            type: Object,
            required: true
        },
        onSuccess: {
            type: Function,
            required: false,
            default: () => {
            }
        },
        onCancel: {
            type: Function,
            required: true
        }
    },

    setup() {
        const orgStore = useOrgStore();
        return {orgStore};
    },

    data() {
        return {
            selectedDepartments: [],
            showChooseDepartmentDialog: false
        };
    },

    methods: {
        onCheckDepartment(departments) {
            this.selectedDepartments = departments;
            this.showChooseDepartmentDialog = false;
        },

        onUncheckDepartment(department) {
            this.selectedDepartments = this.selectedDepartments.filter(d => d.id !== department.id);
        },

        confirmTransfer() {
            if (this.employee && this.selectedDepartments.length > 0) {
                const targetOrgIds = this.selectedDepartments.map(d => d.id);

                this.orgStore.transferEmployee(this.employee.employeeId, targetOrgIds)
                    .then(() => {
                        this.$message.success('变更部门成功');
                        this.onSuccess();
                    })
                    .catch((error) => {
                        console.error('变更部门失败', error);
                        this.$message.error('变更部门失败');
                    });
            } else {
                this.$message.warning('请选择至少一个部门');
            }
        }
    }
}
</script>

<style scoped>
.lead {
    margin-bottom: 12px;
    color: var(--wf-text-2);
}
</style>
