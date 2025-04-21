<template>
    <div>
        <p class="title">更新部门</p>
        <el-form label-position="right" :model="updatedOrganization" size="medium" class="demo-form-inline">
            <el-form-item label="部门名称">
                <el-input v-model.trim="updatedOrganization.name" placeholder="部门名称"></el-input>
            </el-form-item>
            <el-form-item label="部门负责人">
                <el-input disabled>
                    <div slot="prepend">
                        <el-tag v-if="manager" closable @close="handleCloseTag(manager)" type="info">
                            {{ manager.name }}
                        </el-tag>
                    </div>
                    <el-button slot="append" type="text" icon="el-icon-edit"
                        @click="showChooseMemberDialog = true"></el-button>
                </el-input>
            </el-form-item>
            <el-form-item v-if="!currentDepartment.groupId" label="是否创建部门群">
                <el-checkbox v-model="createOrganizationGroup"></el-checkbox>
            </el-form-item>
        </el-form>
        <div class="action-container">
            <el-button @click="onUpdateDepartment(false)">取消</el-button>
            <el-button type="primary" :disabled="!confirmButtonEnable" @click="onConfirm">确定</el-button>
        </div>

        <!-- 集成 ChooseMember 对话框 -->
        <el-dialog :visible.sync="showChooseMemberDialog" append-to-body title="选择成员">
            <ChooseMember :initial-checked-members="initialCheckedMembers" :max-choose-count="1"
                :on-cancel="() => this.showChooseMemberDialog = false" :on-confirm="onCheckMember" />
        </el-dialog>
    </div>
</template>

<script>
import { useOrgStore } from "@/store/stores/orgStore";
import api from "@/api/api";
import ChooseMember from "@/components/page/organization/dialog/ChooseMember";

export default {
    name: "UpdateDepartment",
    components: { ChooseMember },
    props: {
        currentDepartment: {
            type: Object,
            required: true,
        },
        onUpdateDepartment: {
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
            updatedOrganization: {
                name: this.currentDepartment.name,
            },
            createOrganizationGroup: !this.currentDepartment.groupId,
            manager: null,
            showChooseMemberDialog: false,
        }
    },
    computed: {
        confirmButtonEnable() {
            return this.updatedOrganization.name && this.manager;
        },
        initialCheckedMembers() {
            return this.manager ? [this.manager.employeeId] : [];
        }
    },

    async mounted() {
        this.manager = this.currentDepartment.employees.filter(m => m.employeeId === this.currentDepartment.managerId)[0];
    },

    unmounted() {
        this.updatedOrganization = {}
    },

    methods: {
        handleCloseTag() {
            this.manager = null;
        },
        onCheckMember(members) {
            this.showChooseMemberDialog = false;
            console.log('onCheckMembers ', members)
            if (members && members.length > 0) {
                this.manager = members[0];
                console.log('xxxxxx', this.manager)
            }
        },
        onConfirm() {
            if (!this.manager) {
                this.$message.error('请选择部门负责人');
                return;
            }

            this.updatedOrganization.managerId = this.manager.employeeId;
            this.currentDepartment.name = this.updatedOrganization.name;
            this.currentDepartment.managerId = this.manager.employeeId;

            this.orgStore.updateOrganization(this.currentDepartment)
                .then(res => {
                    console.log('更新部门成功', res)
                    this.onUpdateDepartment(true);
                })
                .catch(err => {
                    console.log('更新部门失败', err)
                    this.onUpdateDepartment(false);
                })

            if (this.createOrganizationGroup) {
                api.createOrganizationGroup(this.currentDepartment.id, '')
            }
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

/*>>> .el-form-item__content {*/
/*    width: 300px;*/
/*}*/

.action-container {
    display: flex;
    justify-content: flex-end;
    margin-right: 10px;
}
</style>