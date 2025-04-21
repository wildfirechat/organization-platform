<template>
    <div>
        <p class="title">新建部门</p>
        <el-form
            label-position="right"
            :model="organization"
            size="medium"
            class="demo-form-inline">
            <el-form-item label="部门名称">
                <el-input v-model.trim="organization.name" placeholder="部门名称"></el-input>
            </el-form-item>
            <el-form-item label="上级部门">
                <el-input disabled :value="parentDepartment.name">
                </el-input>
            </el-form-item>
            <el-form-item label="部门负责人">
                <el-input disabled>
                    <div v-if="managers.length > 0" slot="prepend">
                        <el-tag
                            v-for="(member, index) in managers"
                            :key="index"
                            closable
                            @close="handleCloseTag(member)"
                            type="info">
                            {{ member.name }}
                        </el-tag>
                    </div>
                    <el-button slot="append" type="text" icon="el-icon-edit" @click="showChooseMemberDialog = true"></el-button>
                </el-input>
            </el-form-item>
            <el-form-item label="是否创建部门群">
                <el-checkbox v-model="createOrganizationGroup"></el-checkbox>
            </el-form-item>
        </el-form>
        <div class="action-container">
            <el-button @click="onAddDepartment(false)">取消</el-button>
            <el-button type="primary" :disabled="!confirmButtonEnable" @click="onConfirm">确定</el-button>
        </div>

        <!-- 集成 ChooseMember 对话框 -->
        <el-dialog :visible.sync="showChooseMemberDialog" append-to-body title="选择成员">
            <ChooseMember
                :initial-checked-members="initialCheckedMembers"
                :max-choose-count="1"
                :on-cancel="() => this.showChooseMemberDialog = false"
                :on-confirm="onCheckMember"/>
        </el-dialog>
    </div>
</template>

<script>
import { useOrgStore } from "@/store/stores/orgStore";
import ChooseMember from "@/components/page/organization/dialog/ChooseMember";

export default {
    name: "AddSubDepartment",
    components: {ChooseMember},
    props: {
        parentDepartment: {
            type: Object,
            required: true,
        },
        onAddDepartment: {
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
            organization: {},
            createOrganizationGroup: false,
            managers: [],
            showChooseMemberDialog: false
        }
    },
    computed: {
        confirmButtonEnable() {
            return this.organization.name && this.managers.length === 1
        },
        initialCheckedMembers() {
            return this.managers.map(m => m.employeeId);
        }
    },
    methods: {
        handleCloseTag(tag) {
            this.managers = this.managers.filter(m => m.employeeId !== tag.employeeId);
        },
        onCheckMember(members) {
            this.showChooseMemberDialog = false;
            this.managers = members;
        },
        onConfirm() {
            if (this.managers.length === 0) {
                this.$message.error('请选择部门负责人');
                return;
            }
            this.organization.managerId = this.managers[0].employeeId;
            this.orgStore.createOrganization({
                organization: this.organization,
                parentOrganization: this.parentDepartment,
                createGroup: this.createOrganizationGroup,
            })
                .then(res => {
                    console.log('create organization success', res)
                    this.onAddDepartment(true);
                })
                .catch(err => {
                    console.log('create organization error', err)
                    this.onAddDepartment(false);
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

/*>>> .el-form-item__content {*/
/*    width: 300px;*/
/*}*/

.action-container {
    display: flex;
    justify-content: flex-end;
    margin-right: 10px;
}

</style>