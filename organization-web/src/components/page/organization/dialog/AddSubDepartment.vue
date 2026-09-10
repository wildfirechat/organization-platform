<template>
    <div>
        <el-form label-position="right" label-width="96px" :model="organization">
            <el-form-item label="部门名称" required>
                <el-input v-model.trim="organization.name" placeholder="请输入部门名称"/>
            </el-form-item>
            <el-form-item label="上级部门">
                <el-input disabled :model-value="parentDepartment.name"/>
            </el-form-item>
            <el-form-item label="部门负责人">
                <PickerField :items="managers" placeholder="点击选择部门负责人"
                             @open="showChooseMemberDialog = true" @remove="handleCloseTag"/>
            </el-form-item>
        </el-form>

        <div class="wf-dialog-actions">
            <el-button @click="onAddDepartment(false)">取消</el-button>
            <el-button type="primary" :disabled="!confirmButtonEnable" @click="onConfirm">确定</el-button>
        </div>

        <el-dialog v-model="showChooseMemberDialog" append-to-body title="选择成员" width="760px">
            <ChooseMember
                :initial-checked-members="initialCheckedMembers"
                :max-choose-count="1"
                :on-cancel="() => showChooseMemberDialog = false"
                :on-confirm="onCheckMember"/>
        </el-dialog>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";
import ChooseMember from "@/components/page/organization/dialog/ChooseMember.vue";
import PickerField from "@/components/common/PickerField.vue";

export default {
    name: "AddSubDepartment",
    components: {PickerField, ChooseMember},
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
        return {orgStore};
    },

    data() {
        return {
            organization: {},
            managers: [],
            showChooseMemberDialog: false
        }
    },
    computed: {
        confirmButtonEnable() {
            return this.organization.name
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
            if (this.managers.length > 0) {
                this.organization.managerId = this.managers[0].employeeId;
            }
            this.orgStore.createOrganization({
                organization: this.organization,
                parentOrganization: this.parentDepartment,
                createGroup: false,
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

