<template>
    <div>
        <p class="title">修改部门名称</p>
        <el-form label-position="right" size="medium" class="demo-form-inline">
            <el-form-item label="部门名称">
                <el-input v-model.trim="orgName" placeholder="部门名称"></el-input>
            </el-form-item>
        </el-form>
        <div class="action-container">
            <el-button @click="onUpdateDepartment(false)">取消</el-button>
            <el-button type="primary" :disabled="!orgName" @click="onConfirm">确定</el-button>
        </div>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";

export default {
    name: "UpdateDepartment",
    props: {
        currentDepartment: {
            type: Object,
            required: true,
        },
        onUpdateDepartment: {
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
            orgName: this.currentDepartment.name,
        }
    },

    methods: {
        onConfirm() {
            if (!this.orgName) {
                this.$message.error('请输入部门名称');
                return;
            }

            this.currentDepartment.name = this.orgName;
            this.orgStore.updateOrganization(this.currentDepartment)
                .then(res => {
                    console.log('更新部门成功', res)
                    this.onUpdateDepartment(true);
                })
                .catch(err => {
                    console.log('更新部门失败', err)
                    this.onUpdateDepartment(false);
                })
        }
    }
}
</script>

<style scoped>
.title {
    position: absolute;
    top: 20px;
    left: 20px;
    font-size: 16px;
    color: #1f2329;
}

.action-container {
    display: flex;
    justify-content: flex-end;
    margin-right: 10px;
}
</style>
