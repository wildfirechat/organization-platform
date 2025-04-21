<template>
    <el-container style="height: 100%">
        <el-main>
            <el-row type="flex" justify="start" align="middle">
                <el-col :span="4">
                    <el-avatar :size="60" src="https//empty">
                    </el-avatar>
                </el-col>
                <el-col :offset="1">
                    <p class="name">{{ employee.name }}</p>
                    <p class="id">{{ `用户 ID: ${employee.employeeId}` }}</p>
                </el-col>
            </el-row>
            <el-row style="margin-top: 20px">
                <el-checkbox v-model="destroyIMUser">删除 IM 用户</el-checkbox>
            </el-row>
        </el-main>
        <el-footer>
            <el-row type="flex" justify="end">
                <el-button @click="onDeleteEmployee(false)">取消</el-button>
                <el-button type="danger" @click="onConfirm">确认离职</el-button>
            </el-row>
        </el-footer>
    </el-container>
</template>

<script>
import { useOrgStore } from "@/store/stores/orgStore";

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
        return { orgStore };
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

</style>