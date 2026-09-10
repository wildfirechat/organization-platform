<template>
    <div>
        <el-form label-position="right" label-width="110px">
            <el-form-item label="部门名称" required>
                <el-input v-model.trim="orgName" placeholder="请输入部门名称"/>
            </el-form-item>
            <el-form-item label="部门负责人" required>
                <PickerField :items="computedManager ? [computedManager] : []" placeholder="点击选择部门负责人"
                             @open="showChooseMemberDialog = true" @remove="handleCloseTag"/>
            </el-form-item>
            <el-form-item v-if="!currentDepartment.groupId" label="创建部门群">
                <el-switch v-model="createOrganizationGroup"/>
                <span class="field-tip">开启后会为该部门创建一个组织官方群</span>
            </el-form-item>
        </el-form>

        <div class="wf-dialog-actions">
            <el-button @click="onUpdateDepartment(false)">取消</el-button>
            <el-button type="primary" :disabled="!confirmButtonEnable" @click="onConfirm">确定</el-button>
        </div>

        <el-dialog v-model="showChooseMemberDialog" append-to-body title="选择成员" width="760px">
            <ChooseMember v-if="showChooseMemberDialog"
                          :initial-checked-members="initialCheckedMembers"
                          :max-choose-count="1"
                          :on-cancel="() => showChooseMemberDialog = false" :on-confirm="onCheckMember"/>
        </el-dialog>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";
import api from "@/api/api";
import ChooseMember from "@/components/page/organization/dialog/ChooseMember.vue";
import PickerField from "@/components/common/PickerField.vue";

export default {
    name: "UpdateDepartment",
    components: {PickerField, ChooseMember},
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
        return {orgStore};
    },

    data() {
        return {
            createOrganizationGroup: !this.currentDepartment.groupId,
            orgName: this.currentDepartment.name,
            manager: null,
            showChooseMemberDialog: false,
        }
    },
    computed: {
        confirmButtonEnable() {
            return this.orgName && this.computedManager;
        },
        initialCheckedMembers() {
            return this.manager ? [this.manager.employeeId] : [];
        },
        computedManager() {
            return this.manager ? this.manager : (this.currentDepartment.employees || []).filter(m => m.employeeId === this.currentDepartment.managerId)[0];
        }
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
            }
        },
        onConfirm() {
            if (!this.computedManager) {
                this.$message.error('请选择部门负责人');
                return;
            }

            this.currentDepartment.name = this.orgName;
            this.currentDepartment.managerId = this.computedManager.employeeId;

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
.field-tip {
    margin-left: 12px;
    font-size: 12px;
    color: var(--wf-text-3);
}
</style>
