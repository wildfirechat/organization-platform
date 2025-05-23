<template>
    <el-container style="height: 100%">
        <el-aside style="border-right: 1px solid #e6e6e6; padding-right: 20px">
            <el-input v-if="false" v-model="input" placeholder="请输入姓名、邮箱或手机号"></el-input>
            <el-tree v-if="!input"
                     :data="rootOrganizations"
                     ref="tree"
                     :expand-on-click-node="true"
                     :props="defaultProps"
                     :render-after-expand='false'
                     lazy
                     :load="loadNode"
                     node-key="id"
                     @node-click="handleNodeClick"
                     @node-expand="handleNodeExpand">
                <span class="custom-tree-node" slot-scope="{ node}">
                    <span>{{ node.label }}</span>
                    <el-dropdown trigger="click" size="medium" @command="handleDepartmentCommand">
                        <span>
                            <el-icon class="el-icon-more"/>
                        </span>
                        <el-dropdown-menu slot="dropdown">
                            <el-dropdown-item v-if="!node.data.groupId"
                                              :command="{ c: 'create-org-group', node: node, depart: node.data }">创建组织官方群</el-dropdown-item>
                            <el-dropdown-item
                                :command="{ c: 'edit', node: node, depart: node.data }">编辑部门</el-dropdown-item>
                            <el-dropdown-item
                                :command="{ c: 'add-sub', node: node, depart: node.data }">添加子部门</el-dropdown-item>
                            <el-dropdown-item style="color: red"
                                              :command="{ c: 'remove', node: node, depart: node.data }">删除部门</el-dropdown-item>
                        </el-dropdown-menu>
                    </el-dropdown>
                </span>
            </el-tree>
            <div v-else>
                TODO search result
            </div>
        </el-aside>
        <el-container v-if="currentOrg">
            <el-header>
                <div
                    style="display: flex; flex-direction: row; align-items: center; justify-content: center; height: 100%">
                    <div v-if="currentOrg" style="flex: 1 1 auto">
                        <p> {{ currentOrg.name }}</p>
                        <p v-if="currentOrg.managerName" style="font-size: 12px"> {{
                                "部门负责人: " + currentOrg.managerName
                            }}</p>
                    </div>
                    <el-button type="primary" icon="el-icon-plus" v-if="rootOrganizations.length > 0"
                               @click="showAddDepartmentMemberDialog = true">添加成员
                    </el-button>
                    <el-button type="primary" v-if="rootOrganizations.length === 0"
                               @click="importMember">批量导入
                    </el-button>
                </div>
            </el-header>
            <el-table ref="multipleTable" :data="currentOrgEmployees" empty-text="当前公司或部门没有直属员工" tooltip-effect="dark"
                      type="default" style="width: 100%" :cell-style="{ padding: '0', height: '50px' }">
                <el-table-column type="default" width="55">
                </el-table-column>
                <el-table-column prop="name" label="姓名" width="120">
                </el-table-column>
                <el-table-column prop="title" label="职位" width="120">
                </el-table-column>
                <el-table-column prop="mobile" label="手机号" show-overflow-tooltip>
                </el-table-column>
                <el-table-column>
                    <template v-slot="scope">
                        <el-button v-if="false" class="f-btn" @click="handleClickEmployee(scope.row)" type="text"
                                   size="small">查看详情
                        </el-button>
                        <el-button class="f-btn" @click="handleTransferDepartment(scope.row)" type="text"
                                   size="small">变更部门
                        </el-button>
                        <el-button class="f-btn" @click="handleDeleteEmployee(scope.row)" type="text"
                                   size="small">操作离职
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-container>
        <el-container v-else>
            <el-header>
                <div
                    style="display: flex; flex-direction: row; align-items: center; justify-content: center; height: 100%">
                    请选择部门
                </div>
            </el-header>

        </el-container>
        <el-drawer title="操作离职" :visible.sync="showDeleteEmployeeDrawer" direction="rtl"
                   :before-close="onDeleteEmployee">
            <DeleteEmployee :employee="currentEmployee" :on-delete-employee="onDeleteEmployee"/>
        </el-drawer>

        <el-dialog :visible.sync="showUpdateDepartmentDialog"
                   :before-close="() => this.showUpdateDepartmentDialog = false">
            <UpdateDepartment v-if="showUpdateDepartmentDialog" :current-department="targetDepartment" :on-update-department="onUpdateDepartment"/>
        </el-dialog>

        <el-dialog :visible.sync="showAddSubDepartmentDialog"
                   :before-close="() => this.showAddSubDepartmentDialog = false">
            <AddSubDepartment
                v-if="showAddSubDepartmentDialog"
                :parent-department="currentOrg" :on-add-department="onAddDepartment"/>
        </el-dialog>

        <el-dialog :visible.sync="showAddDepartmentMemberDialog" :close-on-click-modal="false"
                   :before-close="() => { this.showAddDepartmentMemberDialog = false }">
            <AddDepartmentMember v-if="showAddDepartmentMemberDialog"
                                 :on-cancel="() => this.showAddDepartmentMemberDialog = false"/>
        </el-dialog>

        <el-dialog title="变更部门" :visible.sync="showTransferDepartmentDialog" :close-on-click-modal="false"
                   :before-close="() => { this.showTransferDepartmentDialog = false }">
            <TransferMember
                v-if="showTransferDepartmentDialog"
                :employee="employeeToTransfer"
                :on-cancel="() => this.showTransferDepartmentDialog = false"
                :on-success="onTransferSuccess"/>
        </el-dialog>
    </el-container>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";
import AddSubDepartment from "@/components/page/organization/dialog/AddSubDepartment";
import AddDepartmentMember from "@/components/page/organization/dialog/AddDepartmentMember";
import DeleteEmployee from "@/components/page/organization/drawer/DeleteEmployee";
import UpdateDepartment from "@/components/page/organization/dialog/UpdateDepartment.vue";
import TransferMember from "@/components/page/organization/TransferMember.vue";
import api from "@/api/api";

export default {
    name: "Member",
    components: {
        UpdateDepartment,
        DeleteEmployee,
        AddDepartmentMember,
        AddSubDepartment,
        TransferMember
    },
    data() {
        return {
            defaultProps: {
                children: 'children',
                label: 'label',
                id: 'id',
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

            showTransferDepartmentDialog: false,
            employeeToTransfer: null,
        }
    },
    computed: {
        rootOrganizations() {
            return this.orgStore.rootOrganizations;
        }
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

    setup() {
        const orgStore = useOrgStore();
        return {orgStore};
    },

    activated() {
        if (this.rootOrganizations.length === 0) {
            this.orgStore.getRootOrganizationsWithChildren()
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
            this.currentOrg = data;
        },
        async handleNodeExpand(data) {
            console.log('node expand', data);
        },
        async loadNode(node, resolve) {
            console.log('to load data', node)
            let data = node.data;
            // node.childNodes = [];
            if ((!data._orgWithChildren && data.id) || data._force) {
                await this.orgStore.queryOrganizationWithChildren(data)
                resolve(data.children);
            } else {
                resolve(data.children ? data.children : data);
            }
        },
        handleSelectionChange(val) {
            this.multipleSelection = val;
        },
        handleClickEmployee(data) {
            console.log('click employee', data)
        },
        handleDeleteEmployee(data) {
            this.showDeleteEmployeeDrawer = true;
            this.currentEmployee = data;
        },
        handleTransferDepartment(data) {
            this.employeeToTransfer = data;
            this.showTransferDepartmentDialog = true;
        },
        onTransferSuccess() {
            this.showTransferDepartmentDialog = false;
            if (this.currentOrg) {
                this.orgStore.queryOrganizationWithChildren(this.currentOrg);
            }
        },
        importMember() {
            this.$router.push('/organization/departmentanduser/import-member')
        },
        handleDepartmentCommand(command) {
            console.log('handleDepartmentCommand', command)
            switch (command.c) {
                case "add-sub":
                    this.showAddSubDepartmentDialog = true;
                    this.targetParentNode = command.node;
                    break;
                case "edit":
                    this.showUpdateDepartmentDialog = true;
                    this.targetDepartment = command.depart;
                    this.targetNode = command.node;
                    break;
                case "remove":
                    this.orgStore.removeOrganization({organization: command.depart, dismissGroup: true})
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
                this.orgStore.queryOrganizationWithChildren(this.currentOrg);
            }
        },
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