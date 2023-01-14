<template>
    <div>
        <p class="title">更新部门</p>
        <el-form
            label-position="right"
            :model="updatedOrganization"
            size="medium"
            class="demo-form-inline">
            <el-form-item label="部门名称">
                <el-input v-model.trim="updatedOrganization.name" placeholder="部门名称"></el-input>
            </el-form-item>
            <el-form-item label="上级部门">
                <el-input disabled :value="parentDepartment.name">
                </el-input>
            </el-form-item>
            <el-form-item label="部门负责人">
                <el-input disabled>
                    <div v-if="managers && managers.length > 0" slot="prepend">
                        <el-tag
                            v-for="(member, index) in managers"
                            :key="index"
                            closable
                            @close="handleCloseTag(member)"
                            type="info">
                            {{ member.name }}
                        </el-tag>
                    </div>
                    <el-button slot="append" type="text" icon="el-icon-edit" @click="onChooseMember"></el-button>
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
    </div>
</template>

<script>

import api from "@/api/api";
import fa from "element-ui/src/locale/lang/fa";

export default {
    name: "UpdateDepartment",
    props: {
        currentDepartment: {
            type: Object,
            required: true,
        },
        parentDepartment: {
            type: Object,
            required: true,
        },
        managers: {
            type: Array,
            required: true,
        },
        onUpdateDepartment: {
            type: Function,
            required: true,
        },
        onChooseMember: {
            type: Function,
            required: true,
        },
        onUncheckMember: {
            type: Function,
            required: true,
        }
    },
    data() {
        return {
            updatedOrganization: {
                name: this.currentDepartment.name,
            },
            createOrganizationGroup: !this.currentDepartment.groupId,
        }
    },
    computed: {
        fa() {
            return fa
        },
        confirmButtonEnable() {
            return this.updatedOrganization.name && this.managers.length === 1
        },
    },
    async mounted() {
    },

    methods: {
        handleCloseTag(tag) {
            this.onUncheckMember(tag);
        },
        onConfirm() {
            this.updatedOrganization.managerId = this.managers[0].employeeId;
            this.currentDepartment.name = this.updatedOrganization.name;
            this.currentDepartment.managerId = this.managers[0].employeeId;
            this.$store.dispatch('updateOrganization', {
                organization: this.currentDepartment
            })
                .then(res => {
                    console.log('create organization success', res)
                    this.onUpdateDepartment(true);
                })
                .catch(err => {
                    console.log('create organization error', err)
                    this.onUpdateDepartment(false);
                })
            if (this.createOrganizationGroup) {
                api.createOrganizationGroup(this.currentDepartment.id, '')
            }
        },
    },

    watch: {
        organization: {
            async handler() {
                let employee = await api.queryEmployee(this.currentDepartment.managerId)
                this.managers.push(employee);
            },
            immediate: true,
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