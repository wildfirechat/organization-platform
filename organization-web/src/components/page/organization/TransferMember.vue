<template>
    <div>
        <div v-if="employee">
            <p>将 <b>{{ employee.name }}</b> 变更至：</p>
            <el-input disabled style="margin: 20px 0;">
                <div v-if="selectedDepartments && selectedDepartments.length" slot="prepend">
                    <el-tag
                        v-for="(depart, index) in selectedDepartments"
                        :key="index"
                        closable
                        @close="onUncheckDepartment(depart)"
                        type="info">
                        {{ depart && depart.name }}
                    </el-tag>
                </div>
                <el-button slot="append" type="text" icon="el-icon-edit" @click="showChooseDepartmentDialog = true"></el-button>
            </el-input>
            <div style="text-align: right; margin-top: 20px;">
                <el-button @click="onCancel">取消</el-button>
                <el-button type="primary" :disabled="selectedDepartments.length === 0" @click="confirmTransfer">确定</el-button>
            </div>
        </div>

        <!-- 选择部门对话框 -->
        <el-dialog :visible.sync="showChooseDepartmentDialog" append-to-body>
            <ChooseDepartment
                :on-cancel="() => this.showChooseDepartmentDialog = false"
                :on-confirm="onCheckDepartment"/>
        </el-dialog>
    </div>
</template>

<script>
import { useOrgStore } from "@/store/stores/orgStore";
import ChooseDepartment from "@/components/page/organization/dialog/ChooseDepartment";

export default {
    name: "TransferMember",
    components: { ChooseDepartment },
    props: {
        employee: {
            type: Object,
            required: true
        },
        onSuccess: {
            type: Function,
            required: false,
            default: () => {}
        },
        onCancel: {
            type: Function,
            required: true
        }
    },

    setup() {
        const orgStore = useOrgStore();
        return { orgStore };
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
/* 可以添加样式 */
</style>