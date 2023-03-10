<template>
    <el-container style="height: 100%">
        <el-aside style="border-right: 1px solid #e6e6e6; padding-right: 20px">
            <el-input v-model="input" placeholder="请输入姓名、邮箱或手机号"></el-input>
            <el-tree v-if="!input" :data="rootOrganizations"
                     ref="tree"
                     :expand-on-click-node="false"
                     :props="defaultProps"
                     :render-after-expand='false'
                     lazy
                     :load="loadNode"
                     @node-click="handleNodeClick"
                     @node-expand="handleNodeExpand">
                <span class="custom-tree-node" slot-scope="{ node}">
                    <span>{{ node.label }}</span>
                    <el-dropdown trigger="click" size="medium" @command="handleDepartmentCommand">
                         <span>
                            <el-icon class="el-icon-more"/>
                        </span>
                        <el-dropdown-menu slot="dropdown">
                            <el-dropdown-item v-if="!node.data.groupId" :command="{c:'create-org-group', node: node, depart:node.data}">创建组织官方群</el-dropdown-item>
                            <el-dropdown-item :command="{c:'edit', node: node, depart:node.data}">编辑部门</el-dropdown-item>
                            <el-dropdown-item :command="{c:'add-sub', node: node, depart:node.data}">添加子部门</el-dropdown-item>
                            <el-dropdown-item style="color: red" :command="{c:'remove', node: node, depart:node.data}">删除部门</el-dropdown-item>
                       </el-dropdown-menu>
                    </el-dropdown>
              </span>
            </el-tree>
            <div v-else>
                TODO search result
            </div>
        </el-aside>
        <el-container>
            <el-header>
                <div style="height: 100%; display: flex; flex-direction: row; align-items: center; justify-content: center">
                    <p style="flex: 1 1 auto"> {{ currentOrg && currentOrg.name }}</p>
                    <el-button type="primary" icon="el-icon-plus" @click="showAddDepartmentMemberDialog = true">添加成员</el-button>
                    <el-button v-if="rootOrganizations.length === 0" @click="importMember">批量导入</el-button>
                    <el-button>变更部门</el-button>
                    <el-button type="danger">操作离职</el-button>
                </div>
            </el-header>
            <el-table
                ref="multipleTable"
                :data="currentOrgEmployees"
                empty-text="当前公司或部门没有直属员工"
                tooltip-effect="dark"
                style="width: 100%"
                :cell-style="{padding: '0', height: '50px'}"
                @selection-change="handleSelectionChange">
                <el-table-column
                    type="selection"
                    width="55">
                </el-table-column>
                <el-table-column
                    prop="name"
                    label="姓名"
                    width="120">
                    <!--                    <template slot-scope="scope">{{ scope.row.date }}</template>-->
                </el-table-column>
                <el-table-column
                    prop="title"
                    label="职位"
                    width="120">
                </el-table-column>
                <el-table-column
                    prop="mobile"
                    label="手机号"
                    show-overflow-tooltip>
                </el-table-column>
                <el-table-column>
                    <template v-slot="scope">
                        <el-button class="f-btn" @click="handleClickEmployee(scope.row)" type="text" size="small">查看详情</el-button>
                        <el-button class="f-btn" @click="handleClickEmployee(scope.row)" type="text" size="small">变更部门</el-button>
                        <el-button class="f-btn" @click="handleClickEmployee(scope.row)" type="text" size="small">操作离职</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-container>
        <el-drawer
            title="操作离职"
            :visible.sync="showDeleteEmployeeDrawer"
            direction="rtl"
            :before-close="onDeleteEmployee">
            <DeleteEmployee
                :employee="currentEmployee"
                :on-delete-employee="onDeleteEmployee"/>
        </el-drawer>

        <el-dialog :visible.sync="showUpdateDepartmentDialog" :before-close="() => this.showUpdateDepartmentDialog= false">
            <UpdateDepartment
                :managers="checkedMembers"
                :current-department="targetDepartment"
                :parent-department="targetDepartment"
                :on-update-department="onUpdateDepartment"
                :on-choose-member="() => {this.checkedMembers = []; this.showChooseMemberDialog = true}"
                :on-uncheck-member="onUncheckMember"
            />
            <el-dialog ref="dialog" :visible.sync="showChooseMemberDialog" append-to-body @hook:mounted="$refs.dialog.rendered = true">>
                <ChooseMember :initial-checked-members="initialCheckedMembers" :max-choose-count="1" :on-cancel="()=> this.showChooseMemberDialog = false" :on-confirm="onCheckMember"/>
            </el-dialog>
        </el-dialog>

        <el-dialog :visible.sync="showAddSubDepartmentDialog" :before-close="() => this.showAddSubDepartmentDialog = false">
            <AddSubDepartment
                :managers="checkedMembers"
                :parent-department="currentOrg"
                :on-add-department="onAddDepartment"
                :on-choose-member="() => this.showChooseMemberDialog = true"
                :on-uncheck-member="onUncheckMember"
            />
            <el-dialog ref="dialog" :visible.sync="showChooseMemberDialog" append-to-body @hook:mounted="$refs.dialog.rendered = true">>
                <ChooseMember :initial-checked-members="initialCheckedMembers" :max-choose-count="1" :on-cancel="()=> this.showChooseMemberDialog = false" :on-confirm="onCheckMember"/>
            </el-dialog>
        </el-dialog>
        <el-dialog
            :visible.sync="showAddDepartmentMemberDialog"
            :close-on-click-modal="false"
            :before-close="() => {this.showAddDepartmentMemberDialog = false; this.checkedDepartments =[]}">
            <AddDepartmentMember
                :checked-departments="checkedDepartments"
                :on-cancel="()=> this.showAddDepartmentMemberDialog = false"
                :on-choose-department="() => this.showChooseDepartmentDialog = true"
                :on-uncheck-department="onUncheckDepartment"
            />
            <el-dialog :visible.sync="showChooseDepartmentDialog" append-to-body>
                <ChooseDepartment :target-department="currentOrg" :on-cancel="()=> this.showChooseDepartmentDialog = false" :on-confirm="onCheckDepartment"/>
            </el-dialog>
        </el-dialog>
    </el-container>
</template>

<script>

import {mapState} from "vuex";
import AddSubDepartment from "@/components/page/organization/dialog/AddSubDepartment";
import AddDepartmentMember from "@/components/page/organization/dialog/AddDepartmentMember";
import ChooseDepartment from "@/components/page/organization/dialog/ChooseDepartment";
import ChooseMember from "@/components/page/organization/dialog/ChooseMember";
import DeleteEmployee from "@/components/page/organization/drawer/DeleteEmployee";
import UpdateDepartment from "@/components/page/organization/dialog/UpdateDepartment.vue";
import fa from "element-ui/src/locale/lang/fa";
import api from "@/api/api";

export default {
    name: "Member",
    components: {UpdateDepartment, DeleteEmployee, AddDepartmentMember, AddSubDepartment, ChooseDepartment, ChooseMember},
    data() {
        return {
            defaultProps: {
                children: 'children',
                label: 'label'
            },
            input: '',
            currentOrg: null,
            currentEmployee: null,
            currentOrgEmployees: [],
            multipleSelection: [],

            showDeleteEmployeeDrawer: false,

            showUpdateDepartmentDialog: false,

            showAddSubDepartmentDialog: false,
            showAddDepartmentMemberDialog: false,

            targetParentDepartment: null,
            targetParentNode: null,
            targetNode: null,
            targetDepartment: null,

            showChooseDepartmentDialog: false,
            checkedDepartments: [],

            showChooseMemberDialog: false,
            checkedMembers: [],

        }
    },
    computed: {
        initialCheckedMembers() {
            return this.checkedMembers.map(m => m.employeeId);
        },
        ...mapState({
            rootOrganizations: state => state.org.rootOrganizations,
        })
    },
    watch: {
        'currentOrg': {
            handler() {
                this.currentOrgEmployees.length = 0;
                if (this.currentOrg) {
                    if (this.currentOrg._orgWithChildren) {
                        this.currentOrgEmployees.length = 0;
                        this.currentOrgEmployees.push(...this.currentOrg._orgWithChildren.employees);
                    }
                } else {
                    this.currentOrgEmployees.length = 0;
                }
            },
            deep: true,
        }
    },

    activated() {
        if (this.rootOrganizations.length === 0) {
            this.$store.dispatch('getRootOrganizationsWithChildren')
                .then(() => {
                    this.currentOrg = this.rootOrganizations[0];
                })
                .catch(e => {
                    console.error('getRootOrganizationsWithChildren error', e);
                })
        }
    },

    methods: {
        handleNodeClick(data) {
            console.log('node click', data)
            if (!data._orgWithChildren && data.id) {
                this.$store.dispatch('queryOrganizationWithChildren', data)
            }
            this.currentOrg = data;
        },
        async handleNodeExpand(data) {
            console.log('node expand', data);
            // if (!data._orgWithChildren && data.id) {
            //     await this.$store.dispatch('queryOrganizationWithChildren', data)
            // }
        },
        async loadNode(node, resolve) {
            console.log('to load data', node)
            let data = node.data;
            if ((!data._orgWithChildren && data.id) || data._force) {
                await this.$store.dispatch('queryOrganizationWithChildren', data)
                console.log('load data', data);
                this.currentOrg = data;
                resolve(data.children);
            } else {
                resolve(data.children ? data.children : data);
            }
        },
        handleSelectionChange(val) {
            this.multipleSelection = val;
        },
        handleClickEmployee(data) {
            // employee
            console.log('click employee', data)
            this.showDeleteEmployeeDrawer = true;
            this.currentEmployee = data;
            // this.$router.push('/organization/departmentanduser/department')
        },
        importMember() {
            this.$router.push('/organization/departmentanduser/import-member')
        },

        handleDepartmentCommand(command) {
            console.log('handleDepartmentCommand', command)
            switch (command.c) {
                case "add-sub":
                    this.showAddSubDepartmentDialog = true;
                    this.targetParentDepartment = command.depart;
                    this.targetParentNode = command.node;
                    break;
                case "edit":
                    this.showUpdateDepartmentDialog = true;
                    this.targetDepartment = command.depart;
                    this.targetNode = command.node;
                    break;
                case "remove":
                    this.$store.dispatch('removeOrganization', {organization: command.depart, dismissGroup: true})
                        .then(() => {
                            let parentNode = command.node.parent;
                            this.updateTreeNode(parentNode);
                        });
                    break;
                case 'create-org-group':
                    api.createOrganizationGroup(command.depart.id, '');
                    break;
                default:
                    break;
            }
        },
        onCheckDepartment(departments) {
            this.checkedDepartments = departments;
            this.showChooseDepartmentDialog = false;
        },
        onCheckMember(members) {
            this.showChooseMemberDialog = false;
            this.checkedMembers = members;
        },
        onUncheckMember(member) {
            this.checkedMembers = this.checkedMembers.filter(m => m.employeeId !== member.employeeId);
        },
        onUncheckDepartment(department) {
            this.checkedDepartments = this.checkedDepartments.filter(d => d.id !== department.id);
        },

        onUpdateDepartment(success) {
            if (success) {
                this.updateTreeNode(this.targetNode);
            }
            this.showUpdateDepartmentDialog = false;
        },

        onAddDepartment(success) {
            if (success) {
                this.updateTreeNode(this.targetParentNode);
                this.targetParentNode = null;
            }
            this.showAddSubDepartmentDialog = false;
        },

        onDeleteEmployee(success) {
            this.showDeleteEmployeeDrawer = false;
            if (success) {
                this.$store.dispatch('queryOrganizationWithChildren', this.currentOrg);
            }
        },

        // el-tree 绑定的数据更新之后，并不会自动更新，故采用这种方案
        // fyi: https://zhuanlan.zhihu.com/p/370597632
        updateTreeNode(node) {
            node.loaded = false;
            node.data._force = true;
            node.loadData();
        }
    }
}
</script>

<style scoped>

.custom-tree-node {
    flex: 1 1 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 14px;
    padding-right: 8px;
}

>>> .el-tree-node__content {
    height: 50px;
}

.f-btn {
    display: none;
}

.el-table__body tr:hover .f-btn {
    display: inline;
}
</style>