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
                    <span>{{ node.label }}({{ node.data.memberCount || 0 }})</span>
                    <el-dropdown trigger="click" size="medium" @command="handleDepartmentCommand">
                        <span @click.stop>
                            <el-icon class="el-icon-more"/>
                        </span>
                        <el-dropdown-menu slot="dropdown">
                            <el-dropdown-item v-if="!node.data.groupId"
                                              :disabled="!node.data.managerId"
                                              :command="{ c: 'create-org-group', node: node, depart: node.data }">创建组织官方群</el-dropdown-item>
                            <el-dropdown-item v-if="node.data.groupId"
                                              :command="{ c: 'dismiss-org-group', node: node, depart: node.data }">解散组织官方群</el-dropdown-item>
                            <el-dropdown-item v-if="node.data.groupId"
                                              :command="{ c: 'repair-org-group', node: node, depart: node.data }">修复组织官方群</el-dropdown-item>
                            <el-dropdown-item
                                :command="{ c: 'set-manager', node: node, depart: node.data }">{{ node.data.managerId ? '修改组织领导' : '设置组织领导' }}</el-dropdown-item>
                            <el-dropdown-item
                                :command="{ c: 'edit', node: node, depart: node.data }">修改部门名称</el-dropdown-item>
                            <el-dropdown-item
                                :command="{ c: 'move-department', node: node, depart: node.data }">移动部门</el-dropdown-item>
                            <el-dropdown-item
                                :command="{ c: 'add-sub', node: node, depart: node.data }">添加子部门</el-dropdown-item>
                            <el-dropdown-item v-if="canMoveUp(node)"
                                :command="{ c: 'move-up', node: node, depart: node.data }">上移</el-dropdown-item>
                            <el-dropdown-item v-if="canMoveUp(node)"
                                :command="{ c: 'move-to-top', node: node, depart: node.data }">移动到顶部</el-dropdown-item>
                            <el-dropdown-item v-if="canMoveDown(node)"
                                :command="{ c: 'move-down', node: node, depart: node.data }">下移</el-dropdown-item>
                            <el-dropdown-item v-if="canMoveDown(node)"
                                :command="{ c: 'move-to-bottom', node: node, depart: node.data }">移动到底部</el-dropdown-item>
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
        <el-container v-if="currentOrg" style="flex-direction: column;">
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
            <el-main style="padding: 0; overflow-y: auto; flex: 1;">
                <el-table ref="multipleTable" :data="currentOrgEmployees" empty-text="当前公司或部门没有直属员工" tooltip-effect="dark"
                          type="default" style="width: 100%" :cell-style="{ padding: '0', height: '50px' }" @row-click="handleClickEmployee">
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
                            <el-button class="f-btn" @click.stop="handleTransferDepartment(scope.row)" type="text"
                                       size="small">变更部门
                            </el-button>
                            <el-button class="f-btn" @click.stop="handleDeleteEmployee(scope.row)" type="text"
                                       size="small">操作离职
                            </el-button>
                            <el-button class="f-btn" @click.stop="handleUpdatePassword(scope.row)" type="text"
                                       size="small">修改密码
                            </el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-main>
            <el-card v-if="currentEmployeeDetail" style="margin-top: 0; flex-shrink: 0;">
                <div slot="header" style="display: flex; justify-content: space-between; align-items: center;">
                    <span>用户信息</span>
                    <el-button type="primary" size="small" @click="showUpdateEmployeeDialog = true">更新用户信息</el-button>
                </div>
                <el-descriptions :column="3" border size="small">
                    <el-descriptions-item label="用户ID">{{ currentEmployeeDetail.employeeId }}</el-descriptions-item>
                    <el-descriptions-item label="姓名">{{ currentEmployeeDetail.name }}</el-descriptions-item>
                    <el-descriptions-item label="职位">{{ currentEmployeeDetail.title }}</el-descriptions-item>
                    <el-descriptions-item label="级别">{{ currentEmployeeDetail.level }}</el-descriptions-item>
                    <el-descriptions-item label="手机号">{{ currentEmployeeDetail.mobile }}</el-descriptions-item>
                    <el-descriptions-item label="邮箱">{{ currentEmployeeDetail.email }}</el-descriptions-item>
                    <el-descriptions-item label="分机号">{{ currentEmployeeDetail.ext }}</el-descriptions-item>
                    <el-descriptions-item label="办公地点">{{ currentEmployeeDetail.office }}</el-descriptions-item>
                    <el-descriptions-item label="城市">{{ currentEmployeeDetail.city }}</el-descriptions-item>
                    <el-descriptions-item label="工号">{{ currentEmployeeDetail.jobNumber }}</el-descriptions-item>
                    <el-descriptions-item label="入职时间">{{ currentEmployeeDetail.joinTime }}</el-descriptions-item>
                    <el-descriptions-item label="排序">{{ currentEmployeeDetail.sort }}</el-descriptions-item>
                    <el-descriptions-item label="类型">{{ currentEmployeeDetail.type }}</el-descriptions-item>
                    <el-descriptions-item label="性别">{{ currentEmployeeDetail.gender === 1 ? '男' : currentEmployeeDetail.gender === 2 ? '女' : '未知' }}</el-descriptions-item>
                    <el-descriptions-item label="头像URL" :span="2">{{ currentEmployeeDetail.portraitUrl }}</el-descriptions-item>
                </el-descriptions>
            </el-card>
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

        <el-dialog :visible.sync="showMoveDepartmentDialog"
                   :before-close="() => this.showMoveDepartmentDialog = false">
            <MoveDepartment v-if="showMoveDepartmentDialog"
                            :current-department="targetDepartment"
                            :on-cancel="() => this.showMoveDepartmentDialog = false"
                            :on-confirm="onMoveDepartmentConfirm"/>
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

        <el-dialog title="修改密码" :visible.sync="showUpdatePasswordDialog" :close-on-click-modal="false"
                   :before-close="() => { this.showUpdatePasswordDialog = false }">
            <UpdateEmployeePwd
                v-if="showUpdatePasswordDialog"
                :employee="employeeToUpdatePassword"
                :on-cancel="() => this.showUpdatePasswordDialog = false"
                :on-success="onUpdatePasswordSuccess"/>
        </el-dialog>

        <el-dialog title="更新用户信息" :visible.sync="showUpdateEmployeeDialog" :close-on-click-modal="false"
                   :before-close="() => { this.showUpdateEmployeeDialog = false }">
            <UpdateEmployee
                v-if="showUpdateEmployeeDialog"
                :employee="currentEmployeeDetail"
                :on-cancel="() => this.showUpdateEmployeeDialog = false"
                :on-success="onUpdateEmployeeSuccess"/>
        </el-dialog>

        <el-dialog :title="managerTargetDepartment && managerTargetDepartment.managerId ? '修改组织领导' : '设置组织领导'" :visible.sync="showSetManagerDialog" :close-on-click-modal="false"
                   :before-close="() => { this.showSetManagerDialog = false }">
            <el-form label-position="right" size="medium">
                <el-form-item label="部门领导">
                    <el-select v-model="selectedManagerId" filterable placeholder="请选择部门领导" style="width: 100%">
                        <el-option
                            v-for="emp in managerCandidates"
                            :key="emp.employeeId"
                            :label="emp.name"
                            :value="emp.employeeId">
                        </el-option>
                    </el-select>
                </el-form-item>
            </el-form>
            <div slot="footer" style="display: flex; justify-content: flex-end">
                <el-button @click="showSetManagerDialog = false">取消</el-button>
                <el-button type="primary" :disabled="!selectedManagerId" @click="confirmSetManager">确定</el-button>
            </div>
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
import UpdateEmployeePwd from "@/components/page/organization/UpdateEmployeePwd.vue";
import UpdateEmployee from "@/components/page/organization/dialog/UpdateEmployee.vue";
import MoveDepartment from "@/components/page/organization/dialog/MoveDepartment.vue";
import OrganizationWithChildren from "@/model/organizationWithChildren";
import api from "@/api/api";

export default {
    name: "Member",
    components: {
        UpdateDepartment,
        MoveDepartment,
        DeleteEmployee,
        AddDepartmentMember,
        AddSubDepartment,
        TransferMember,
        UpdateEmployeePwd,
        UpdateEmployee
    },
    data() {
        return {
            defaultProps: {
                children: 'children',
                label: 'label',
                id: 'id',
                isLeaf: 'leaf',
            },
            input: '',
            currentOrg: null,
            currentEmployee: null,
            currentOrgEmployees: [],
            multipleSelection: [],

            showDeleteEmployeeDrawer: false,
            showUpdateDepartmentDialog: false,
            showMoveDepartmentDialog: false,
            showAddSubDepartmentDialog: false,
            showAddDepartmentMemberDialog: false,

            targetParentDepartment: null,
            targetParentNode: null,
            targetNode: null,
            targetDepartment: null,

            showTransferDepartmentDialog: false,
            employeeToTransfer: null,
            showUpdatePasswordDialog: false,
            employeeToUpdatePassword: null,

            currentEmployeeDetail: null,
            showUpdateEmployeeDialog: false,

            showSetManagerDialog: false,
            managerTargetDepartment: null,
            managerTargetNode: null,
            selectedManagerId: null,
            managerCandidates: [],
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
                this.currentEmployeeDetail = null;
            },
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
        async handleNodeClick(data) {
            console.log('node click', data)
            this.currentOrg = data;
            if (data.id) {
                let result = await api.queryOrganizationWithChildren(data.id);
                let orgWC = Object.assign(new OrganizationWithChildren(), result);
                data._orgWithChildren = orgWC;
                this.currentOrgEmployees.length = 0;
                if (orgWC.employees) {
                    this.currentOrgEmployees.push(...orgWC.employees);
                }
                if (data.managerId && !data.managerName) {
                    api.queryEmployee(data.managerId).then(emp => {
                        if (emp && emp.name) {
                            this.$set(data, 'managerName', emp.name);
                        }
                    });
                }
            }
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
            this.currentEmployeeDetail = data;
        },
        onUpdateEmployeeSuccess() {
            this.showUpdateEmployeeDialog = false;
            if (this.currentOrg) {
                this.handleNodeClick(this.currentOrg);
            }
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
        handleUpdatePassword(data) {
            this.employeeToUpdatePassword = data;
            this.showUpdatePasswordDialog = true;
        },
        onUpdatePasswordSuccess() {
            this.showUpdatePasswordDialog = false;
            this.employeeToUpdatePassword = null;
        },
        importMember() {
            this.$router.push('/organization/departmentanduser/import-member')
        },
        canMoveUp(node) {
            if (!node.parent || node.parent.childNodes.length <= 1) return false;
            return node.parent.childNodes.indexOf(node) > 0;
        },
        canMoveDown(node) {
            if (!node.parent || node.parent.childNodes.length <= 1) return false;
            return node.parent.childNodes.indexOf(node) < node.parent.childNodes.length - 1;
        },
        buildSortUpdateOrg(depart, newSort) {
            const org = Object.assign({}, depart);
            org.sort = newSort;
            delete org.label;
            delete org.type;
            delete org.managerName;
            delete org.children;
            delete org.hasChildren;
            delete org.employees;
            delete org._orgWithChildren;
            return org;
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
                case "move-department":
                    this.showMoveDepartmentDialog = true;
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
                    api.createOrganizationGroup(command.depart.id, '').then(() => {
                        this.updateTreeNode(command.node);
                    });
                    break;
                case 'dismiss-org-group':
                    api.dismissOrganizationGroup(command.depart).then(() => {
                        this.updateTreeNode(command.node);
                    });
                    break;
                case 'repair-org-group':
                    api.repairOrganizationGroup(command.depart.id).then(() => {
                        this.updateTreeNode(command.node);
                    });
                    break;
                case 'set-manager': {
                    const depart = command.depart;
                    this.managerTargetDepartment = depart;
                    this.managerTargetNode = command.node;
                    this.selectedManagerId = depart.managerId || null;
                    api.searchEmployee(depart.id, '', 0, 100, false).then(result => {
                        this.managerCandidates = result.contents || [];
                        this.showSetManagerDialog = true;
                    });
                    break;
                }
                case 'move-up': {
                    const siblings = command.node.parent.childNodes;
                    const idx = siblings.indexOf(command.node);
                    if (idx > 0) {
                        const prevSort = siblings[idx - 1].data.sort;
                        const newSort = prevSort - 1;
                        const org = this.buildSortUpdateOrg(command.depart, newSort);
                        this.orgStore.updateOrganization(org)
                            .then(() => {
                                let parentNode = command.node.parent;
                                this.updateTreeNode(parentNode);
                            });
                    }
                    break;
                }
                case 'move-down': {
                    const siblings = command.node.parent.childNodes;
                    const idx = siblings.indexOf(command.node);
                    if (idx < siblings.length - 1) {
                        const nextSort = siblings[idx + 1].data.sort;
                        const newSort = nextSort + 1;
                        const org = this.buildSortUpdateOrg(command.depart, newSort);
                        this.orgStore.updateOrganization(org)
                            .then(() => {
                                let parentNode = command.node.parent;
                                this.updateTreeNode(parentNode);
                            });
                    }
                    break;
                }
                case 'move-to-top': {
                    const siblings = command.node.parent.childNodes;
                    const firstSort = siblings[0].data.sort;
                    const newSort = firstSort - 1;
                    const org = this.buildSortUpdateOrg(command.depart, newSort);
                    this.orgStore.updateOrganization(org)
                        .then(() => {
                            let parentNode = command.node.parent;
                            this.updateTreeNode(parentNode);
                        });
                    break;
                }
                case 'move-to-bottom': {
                    const siblings = command.node.parent.childNodes;
                    const lastSort = siblings[siblings.length - 1].data.sort;
                    const newSort = lastSort + 1;
                    const org = this.buildSortUpdateOrg(command.depart, newSort);
                    this.orgStore.updateOrganization(org)
                        .then(() => {
                            let parentNode = command.node.parent;
                            this.updateTreeNode(parentNode);
                        });
                    break;
                }
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
        async onMoveDepartmentConfirm(newParent) {
            if (!newParent || !this.targetDepartment) {
                return;
            }
            try {
                await api.moveOrganization(this.targetDepartment.id, newParent.id);
                this.$message.success('移动成功');
                this.showMoveDepartmentDialog = false;
                let parentNode = this.targetNode ? this.targetNode.parent : null;
                if (parentNode) {
                    this.updateTreeNode(parentNode);
                }
                this.orgStore.getRootOrganizationsWithChildren();
                this.targetDepartment = null;
                this.targetNode = null;
            } catch (e) {
                console.error('移动部门失败', e);
                this.$message.error('移动部门失败');
            }
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
        async confirmSetManager() {
            if (!this.selectedManagerId || !this.managerTargetDepartment) {
                return;
            }
            try {
                await api.setOrganizationManager(this.managerTargetDepartment.id, this.selectedManagerId);
                this.managerTargetDepartment.managerId = this.selectedManagerId;
                if (this.managerTargetDepartment._orgWithChildren) {
                    const manager = this.managerCandidates.find(e => e.employeeId === this.selectedManagerId);
                    this.managerTargetDepartment.managerName = manager ? manager.name : null;
                }
                this.showSetManagerDialog = false;
                this.updateTreeNode(this.managerTargetNode);
                if (this.currentOrg && this.currentOrg.id === this.managerTargetDepartment.id) {
                    this.handleNodeClick(this.currentOrg);
                }
                this.managerTargetDepartment = null;
                this.managerTargetNode = null;
                this.selectedManagerId = null;
                this.managerCandidates = [];
            } catch (e) {
                console.error('设置部门领导失败', e);
                this.$message.error('设置部门领导失败');
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