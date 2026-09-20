<template>
    <div class="member-page">
        <PageHeader v-if="showHeader" title="成员与部门"
                    description="维护部门树与部门下的直属成员，支持组织官方群的创建与修复">
            <template #extra>
                <el-button :icon="Upload" @click="importMember">批量导入</el-button>
            </template>
        </PageHeader>

        <div class="workspace">
            <!-- 左侧：部门树 -->
            <aside class="org-tree">
                <div class="pane-head">
                    <span class="pane-title">组织架构</span>
                </div>
                <el-scrollbar class="tree-scroll">
                    <el-tree :data="rootOrganizations"
                             ref="tree"
                             :expand-on-click-node="true"
                             :props="defaultProps"
                             :render-after-expand="false"
                             lazy
                             :load="loadNode"
                             node-key="id"
                             highlight-current
                             empty-text="暂无部门数据"
                             @node-click="handleNodeClick"
                             @node-expand="handleNodeExpand">
                        <template #default="{ node }">
                            <span class="custom-tree-node">
                                <span class="node-label wf-ellipsis">{{ node.label }}</span>
                                <span class="node-count wf-num">{{ node.data.memberCount || 0 }}</span>
                                <el-dropdown class="node-actions" trigger="click"
                                             @command="handleDepartmentCommand">
                                    <span class="node-more" @click.stop>
                                        <el-icon><MoreFilled/></el-icon>
                                    </span>
                                    <template #dropdown>
                                        <el-dropdown-menu>
                                            <el-dropdown-item v-if="!node.data.groupId"
                                                              :disabled="!node.data.managerId"
                                                              :command="{ c: 'create-org-group', node: node, depart: node.data }">
                                                创建组织官方群
                                            </el-dropdown-item>
                                            <el-dropdown-item v-if="node.data.groupId"
                                                              :command="{ c: 'dismiss-org-group', node: node, depart: node.data }">
                                                解散组织官方群
                                            </el-dropdown-item>
                                            <el-dropdown-item v-if="node.data.groupId"
                                                              :command="{ c: 'repair-org-group', node: node, depart: node.data }">
                                                修复组织官方群
                                            </el-dropdown-item>
                                            <el-dropdown-item divided
                                                              :command="{ c: 'set-manager', node: node, depart: node.data }">
                                                {{ node.data.managerId ? '修改组织领导' : '设置组织领导' }}
                                            </el-dropdown-item>
                                            <el-dropdown-item :command="{ c: 'edit', node: node, depart: node.data }">
                                                编辑部门
                                            </el-dropdown-item>
                                            <el-dropdown-item :command="{ c: 'add-sub', node: node, depart: node.data }">
                                                添加子部门
                                            </el-dropdown-item>
                                            <el-dropdown-item v-if="canMoveUp(node)" divided
                                                              :command="{ c: 'move-up', node: node, depart: node.data }">
                                                上移
                                            </el-dropdown-item>
                                            <el-dropdown-item v-if="canMoveUp(node)"
                                                              :command="{ c: 'move-to-top', node: node, depart: node.data }">
                                                移动到顶部
                                            </el-dropdown-item>
                                            <el-dropdown-item v-if="canMoveDown(node)"
                                                              :command="{ c: 'move-down', node: node, depart: node.data }">
                                                下移
                                            </el-dropdown-item>
                                            <el-dropdown-item v-if="canMoveDown(node)"
                                                              :command="{ c: 'move-to-bottom', node: node, depart: node.data }">
                                                移动到底部
                                            </el-dropdown-item>
                                            <el-dropdown-item divided class="danger-item"
                                                              :command="{ c: 'remove', node: node, depart: node.data }">
                                                删除部门
                                            </el-dropdown-item>
                                        </el-dropdown-menu>
                                    </template>
                                </el-dropdown>
                            </span>
                        </template>
                    </el-tree>
                </el-scrollbar>
            </aside>

            <!-- 右侧：部门成员 -->
            <section class="org-detail">
                <template v-if="currentOrg">
                    <div class="pane-head detail-head">
                        <div class="detail-info">
                            <div class="detail-title">
                                <span class="detail-name wf-ellipsis">{{ currentOrg.name }}</span>
                                <el-tag size="small" type="info" effect="plain" round>
                                    {{ (currentOrg.memberCount || 0) + ' 人' }}
                                </el-tag>
                                <el-tag v-if="currentOrg.groupId" size="small" type="success" effect="light" round>
                                    已建官方群
                                </el-tag>
                            </div>
                            <div v-if="currentOrg.managerName" class="detail-sub">
                                部门负责人：{{ currentOrg.managerName }}
                            </div>
                        </div>
                        <el-button type="primary" :icon="Plus" @click="showAddDepartmentMemberDialog = true">
                            添加成员
                        </el-button>
                    </div>

                    <el-table ref="multipleTable" v-loading="loadingEmployees" :data="currentOrgEmployees"
                              empty-text="当前公司或部门没有直属员工" class="member-table" height="100%"
                              @row-click="handleClickEmployee">
                        <el-table-column label="成员" min-width="200">
                            <template v-slot="scope">
                                <div class="member-cell">
                                    <el-avatar :size="30" shape="circle" :src="scope.row.portraitUrl">
                                        {{ (scope.row.name || '').slice(0, 1) }}
                                    </el-avatar>
                                    <div class="member-meta">
                                        <span class="member-name-line">
                                            <span class="member-name wf-ellipsis">{{ scope.row.name }}</span>
                                            <el-tag v-if="scope.row.executiveMode" size="small" type="warning"
                                                    effect="light" round>高管
                                            </el-tag>
                                        </span>
                                        <span class="member-id wf-ellipsis wf-muted">{{ scope.row.employeeId }}</span>
                                    </div>
                                </div>
                            </template>
                        </el-table-column>
                        <el-table-column prop="title" label="职位" min-width="120">
                            <template v-slot="scope">
                                <span v-if="scope.row.title">{{ scope.row.title }}</span>
                                <span v-else class="wf-muted">—</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="mobile" label="手机号" min-width="130" show-overflow-tooltip>
                            <template v-slot="scope">
                                <span v-if="scope.row.mobile" class="wf-num">{{ scope.row.mobile }}</span>
                                <span v-else class="wf-muted">—</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
                            <template v-slot="scope">
                                <span v-if="scope.row.email">{{ scope.row.email }}</span>
                                <span v-else class="wf-muted">—</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" width="240" align="right" fixed="right">
                            <template v-slot="scope">
                                <el-button @click.stop="handleTransferDepartment(scope.row)" link type="primary"
                                           size="small">变更部门
                                </el-button>
                                <el-button @click.stop="handleUpdatePassword(scope.row)" link type="primary"
                                           size="small">修改密码
                                </el-button>
                                <el-button @click.stop="handleDeleteEmployee(scope.row)" link type="danger"
                                           size="small">操作离职
                                </el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </template>

                <el-empty v-else-if="rootOrganizations.length > 0" description="请在左侧选择一个部门" :image-size="90"/>

                <el-empty v-else description="还没有任何部门数据，先批量导入组织架构吧" :image-size="90">
                    <el-button type="primary" :icon="Upload" @click="importMember">批量导入</el-button>
                </el-empty>
            </section>
        </div>

        <!-- 成员详情 -->
        <el-dialog title="成员信息" v-model="showEmployeeDetailDialog" width="560px" class="profile-dialog">
            <template v-if="currentEmployeeDetail">
                <div class="profile">
                    <el-avatar :size="56" :src="currentEmployeeDetail.portraitUrl">
                        {{ (currentEmployeeDetail.name || '').slice(0, 1) }}
                    </el-avatar>
                    <div class="profile-meta">
                        <p class="profile-name">{{ currentEmployeeDetail.name }}</p>
                        <p class="profile-sub">{{ currentEmployeeDetail.title || '未填写职位' }}</p>
                    </div>
                </div>
                <el-scrollbar max-height="46vh">
                    <el-descriptions :column="1" border size="small" class="profile-desc">
                        <el-descriptions-item label="用户ID">{{ text(currentEmployeeDetail.employeeId) }}</el-descriptions-item>
                        <el-descriptions-item label="工号">{{ text(currentEmployeeDetail.jobNumber) }}</el-descriptions-item>
                        <el-descriptions-item label="级别">{{ text(currentEmployeeDetail.level) }}</el-descriptions-item>
                        <el-descriptions-item label="手机号">{{ text(currentEmployeeDetail.mobile) }}</el-descriptions-item>
                        <el-descriptions-item label="邮箱">{{ text(currentEmployeeDetail.email) }}</el-descriptions-item>
                        <el-descriptions-item label="分机号">{{ text(currentEmployeeDetail.ext) }}</el-descriptions-item>
                        <el-descriptions-item label="办公地点">{{ text(currentEmployeeDetail.office) }}</el-descriptions-item>
                        <el-descriptions-item label="城市">{{ text(currentEmployeeDetail.city) }}</el-descriptions-item>
                        <el-descriptions-item label="性别">{{ genderText(currentEmployeeDetail.gender) }}</el-descriptions-item>
                        <el-descriptions-item label="入职时间">{{ text(currentEmployeeDetail.joinTime) }}</el-descriptions-item>
                        <el-descriptions-item label="排序">{{ text(currentEmployeeDetail.sort) }}</el-descriptions-item>
                        <el-descriptions-item label="类型">{{ text(currentEmployeeDetail.type) }}</el-descriptions-item>
                        <el-descriptions-item label="高管模式">
                            <el-tag v-if="currentEmployeeDetail.executiveMode" size="small" type="warning"
                                    effect="light" round>已开启
                            </el-tag>
                            <span v-else class="wf-muted">未开启</span>
                        </el-descriptions-item>
                        <el-descriptions-item label="头像URL">
                            <span class="wf-ellipsis profile-url">{{ text(currentEmployeeDetail.portraitUrl) }}</span>
                        </el-descriptions-item>
                    </el-descriptions>
                </el-scrollbar>
            </template>
            <template #footer>
                <div class="wf-actions">
                    <el-button @click="showEmployeeDetailDialog = false">关闭</el-button>
                    <el-button type="primary" @click="showUpdateEmployeeDialog = true">更新用户信息</el-button>
                </div>
            </template>
        </el-dialog>

        <el-dialog title="操作离职" v-model="showDeleteEmployeeDialog" width="440px" :close-on-click-modal="false">
            <DeleteEmployee v-if="showDeleteEmployeeDialog" :employee="currentEmployee"
                            :on-delete-employee="onDeleteEmployee"/>
        </el-dialog>

        <el-dialog title="编辑部门" v-model="showUpdateDepartmentDialog" width="520px"
                   :before-close="() => showUpdateDepartmentDialog = false">
            <UpdateDepartment v-if="showUpdateDepartmentDialog" :current-department="targetDepartment"
                              :on-update-department="onUpdateDepartment"/>
        </el-dialog>

        <el-dialog title="新建部门" v-model="showAddSubDepartmentDialog" width="520px"
                   :before-close="() => showAddSubDepartmentDialog = false">
            <AddSubDepartment v-if="showAddSubDepartmentDialog" :parent-department="currentOrg"
                              :on-add-department="onAddDepartment"/>
        </el-dialog>

        <el-dialog title="添加成员" v-model="showAddDepartmentMemberDialog" width="720px" :close-on-click-modal="false"
                   :before-close="() => { showAddDepartmentMemberDialog = false }">
            <AddDepartmentMember v-if="showAddDepartmentMemberDialog"
                                 :on-cancel="() => showAddDepartmentMemberDialog = false"
                                 :on-success="onAddMemberSuccess"/>
        </el-dialog>

        <el-dialog title="变更部门" v-model="showTransferDepartmentDialog" width="520px" :close-on-click-modal="false"
                   :before-close="() => { showTransferDepartmentDialog = false }">
            <TransferMember v-if="showTransferDepartmentDialog" :employee="employeeToTransfer"
                            :on-cancel="() => showTransferDepartmentDialog = false"
                            :on-success="onTransferSuccess"/>
        </el-dialog>

        <el-dialog title="修改密码" v-model="showUpdatePasswordDialog" width="460px" :close-on-click-modal="false"
                   :before-close="() => { showUpdatePasswordDialog = false }">
            <UpdateEmployeePwd v-if="showUpdatePasswordDialog" :employee="employeeToUpdatePassword"
                               :on-cancel="() => showUpdatePasswordDialog = false"
                               :on-success="onUpdatePasswordSuccess"/>
        </el-dialog>

        <el-dialog title="更新用户信息" v-model="showUpdateEmployeeDialog" width="720px" :close-on-click-modal="false"
                   :before-close="() => { showUpdateEmployeeDialog = false }">
            <UpdateEmployee v-if="showUpdateEmployeeDialog" :employee="currentEmployeeDetail"
                            :on-cancel="() => showUpdateEmployeeDialog = false"
                            :on-success="onUpdateEmployeeSuccess"/>
        </el-dialog>

        <el-dialog
            :title="managerTargetDepartment && managerTargetDepartment.managerId ? '修改组织领导' : '设置组织领导'"
            v-model="showSetManagerDialog" width="460px" :close-on-click-modal="false"
            :before-close="() => { showSetManagerDialog = false }">
            <el-form label-position="top">
                <el-form-item label="部门领导">
                    <el-select v-model="selectedManagerId" filterable placeholder="请选择部门领导" style="width: 100%">
                        <el-option v-for="emp in managerCandidates" :key="emp.employeeId" :label="emp.name"
                                   :value="emp.employeeId"/>
                    </el-select>
                </el-form-item>
            </el-form>
            <template #footer>
                <div class="wf-actions">
                    <el-button @click="showSetManagerDialog = false">取消</el-button>
                    <el-button type="primary" :disabled="!selectedManagerId" @click="confirmSetManager">确定</el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";
import {Plus, Upload} from '@element-plus/icons-vue';
import PageHeader from "@/components/common/PageHeader.vue";
import AddSubDepartment from "@/components/page/organization/dialog/AddSubDepartment.vue";
import AddDepartmentMember from "@/components/page/organization/dialog/AddDepartmentMember.vue";
import DeleteEmployee from "@/components/page/organization/dialog/DeleteEmployee.vue";
import UpdateDepartment from "@/components/page/organization/dialog/UpdateDepartment.vue";
import TransferMember from "@/components/page/organization/TransferMember.vue";
import UpdateEmployeePwd from "@/components/page/organization/UpdateEmployeePwd.vue";
import UpdateEmployee from "@/components/page/organization/dialog/UpdateEmployee.vue";
import OrganizationWithChildren from "@/model/organizationWithChildren";
import api from "@/api/api";

export default {
    name: "Member",
    props: {
        // 嵌在 DepartmentAndMember 的标签页里时，由外层统一渲染页面标题
        showHeader: {
            type: Boolean,
            default: true
        }
    },
    components: {
        PageHeader,
        UpdateDepartment,
        DeleteEmployee,
        AddDepartmentMember,
        AddSubDepartment,
        TransferMember,
        UpdateEmployeePwd,
        UpdateEmployee
    },
    data() {
        return {
            Plus,
            Upload,
            defaultProps: {
                children: 'children',
                label: 'label',
                id: 'id',
                isLeaf: 'leaf',
            },
            currentOrg: null,
            currentEmployee: null,
            currentOrgEmployees: [],
            loadingEmployees: false,

            showDeleteEmployeeDialog: false,
            showUpdateDepartmentDialog: false,
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
            showEmployeeDetailDialog: false,
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
                this.showEmployeeDetailDialog = false;
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
                .then(() => this.selectDefaultOrg())
                .catch(e => {
                    console.error('getRootOrganizationsWithChildren error', e);
                })
        } else if (!this.currentOrg) {
            this.selectDefaultOrg();
        }
    },

    methods: {
        // 进入页面时默认选中第一个根部门并加载其成员，避免右侧一片空白
        selectDefaultOrg() {
            const first = this.rootOrganizations[0];
            if (!first) {
                return;
            }
            this.handleNodeClick(first);
            this.$nextTick(() => {
                this.$refs.tree && this.$refs.tree.setCurrentKey(first.id);
            });
        },
        text(value) {
            return (value === null || value === undefined || value === '') ? '—' : value;
        },
        genderText(gender) {
            return gender === 1 ? '男' : gender === 2 ? '女' : '未知';
        },
        async handleNodeClick(data) {
            console.log('node click', data)
            this.currentOrg = data;
            if (data.id) {
                this.loadingEmployees = true;
                try {
                    let result = await api.queryOrganizationWithChildren(data.id);
                    let orgWC = Object.assign(new OrganizationWithChildren(), result);
                    data._orgWithChildren = orgWC;
                    // 同步部门自身字段（如 memberCount），让树上的人数徽标随成员增删实时更新
                    if (orgWC.organization) {
                        Object.assign(data, orgWC.organization);
                    }
                    this.currentOrgEmployees.length = 0;
                    if (orgWC.employees) {
                        this.currentOrgEmployees.push(...orgWC.employees);
                    }
                } finally {
                    this.loadingEmployees = false;
                }
                if (data.managerId && !data.managerName) {
                    api.queryEmployee(data.managerId).then(emp => {
                        if (emp && emp.name) {
                            data.managerName = emp.name;
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
            if ((!data._orgWithChildren && data.id) || data._force) {
                await this.orgStore.queryOrganizationWithChildren(data)
                resolve(data.children);
            } else {
                resolve(data.children ? data.children : data);
            }
        },
        handleClickEmployee(data) {
            console.log('click employee', data)
            this.currentEmployeeDetail = data;
            this.showEmployeeDetailDialog = true;
        },
        // 增删成员后统一走这里：重新拉取当前部门，刷新右侧成员列表和树上的人数
        reloadCurrentOrg() {
            if (this.currentOrg) {
                this.handleNodeClick(this.currentOrg);
            }
        },
        onAddMemberSuccess() {
            this.showAddDepartmentMemberDialog = false;
            this.reloadCurrentOrg();
        },
        onUpdateEmployeeSuccess() {
            this.showUpdateEmployeeDialog = false;
            const employeeId = this.currentEmployeeDetail && this.currentEmployeeDetail.employeeId;
            if (this.currentOrg) {
                this.handleNodeClick(this.currentOrg).then(() => {
                    // 列表数据被整体替换了，详情抽屉里要换成新对象，否则显示的还是旧值
                    const updated = this.currentOrgEmployees.find(e => e.employeeId === employeeId);
                    if (updated) {
                        this.currentEmployeeDetail = updated;
                    }
                });
            }
        },
        handleDeleteEmployee(data) {
            this.showDeleteEmployeeDialog = true;
            this.currentEmployee = data;
        },
        handleTransferDepartment(data) {
            this.employeeToTransfer = data;
            this.showTransferDepartmentDialog = true;
        },
        onTransferSuccess() {
            this.showTransferDepartmentDialog = false;
            this.reloadCurrentOrg();
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
        onAddDepartment(success) {
            if (success) {
                this.updateTreeNode(this.targetParentNode);
                this.targetParentNode = null;
            }
            this.showAddSubDepartmentDialog = false;
        },
        onDeleteEmployee(success) {
            this.showDeleteEmployeeDialog = false;
            if (success) {
                this.reloadCurrentOrg();
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
.member-page {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
}

/* 树 + 成员表放在同一张卡片里，用一条分隔线区分，而不是各自加边框 */
.workspace {
    display: flex;
    flex: 1;
    min-height: 480px;
    overflow: hidden;
    background: var(--wf-surface);
    border-radius: var(--wf-radius-lg);
    box-shadow: var(--wf-shadow-sm);
}

.pane-head {
    display: flex;
    flex-direction: column;
    justify-content: center;
    flex-shrink: 0;
    height: 56px;
    padding: 0 16px;
    border-bottom: 1px solid var(--wf-border-light);
}

.pane-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--wf-text-1);
}

/* ---------- 左侧部门树 ---------- */
.org-tree {
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    width: 280px;
    border-right: 1px solid var(--wf-border-light);
}

.tree-scroll {
    flex: 1;
    min-height: 0;
    padding: 8px;
}

.custom-tree-node {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1 1 auto;
    min-width: 0;
    padding-right: 4px;
    font-size: 14px;
}

.node-label {
    flex: 1 1 auto;
    min-width: 0;
}

.node-count {
    flex-shrink: 0;
    padding: 0 6px;
    border-radius: 9px;
    background: var(--wf-fill);
    color: var(--wf-text-3);
    font-size: 12px;
    line-height: 18px;
}

/* 操作入口默认隐藏，悬停或选中时才出现，避免整棵树被“⋯”铺满 */
.node-actions {
    flex-shrink: 0;
    opacity: 0;
    transition: opacity 0.15s ease;
}

.org-tree :deep(.el-tree-node__content:hover) .node-actions,
.org-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .node-actions {
    opacity: 1;
}

.node-more {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border-radius: var(--wf-radius-sm);
    color: var(--wf-text-3);
    outline: none;
}

.node-more:hover {
    background: rgba(29, 33, 41, 0.06);
    color: var(--wf-text-1);
}

:deep(.danger-item) {
    color: var(--wf-danger);
}

/* ---------- 右侧成员列表 ---------- */
.org-detail {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-width: 0;
}

.org-detail > .el-empty {
    margin: auto;
}

.detail-head {
    flex-direction: row;
    align-items: center;
    gap: 12px;
}

.detail-info {
    flex: 1;
    min-width: 0;
}

.detail-title {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
}

.detail-name {
    font-size: 15px;
    font-weight: 600;
    color: var(--wf-text-1);
}

.detail-sub {
    font-size: 12px;
    color: var(--wf-text-3);
}

.member-table {
    flex: 1;
    min-height: 0;
}

.member-table :deep(.el-table__row) {
    cursor: pointer;
}

.member-cell {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
}

.member-cell :deep(.el-avatar) {
    flex-shrink: 0;
    background: var(--wf-brand-bg);
    color: var(--wf-brand);
    font-size: 13px;
}

.member-meta {
    display: flex;
    flex-direction: column;
    min-width: 0;
    line-height: 18px;
}

.member-name-line {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
}

.member-name {
    font-weight: 500;
}

.member-id {
    font-size: 12px;
}

/* ---------- 成员详情弹窗 ---------- */
.profile {
    display: flex;
    align-items: center;
    gap: 14px;
    padding-bottom: 16px;
    margin-bottom: 16px;
    border-bottom: 1px solid var(--wf-border-light);
}

.profile :deep(.el-avatar) {
    background: var(--wf-brand-bg);
    color: var(--wf-brand);
    font-size: 20px;
}

.profile-name {
    font-size: 17px;
    font-weight: 600;
    color: var(--wf-text-1);
}

.profile-sub {
    margin-top: 2px;
    font-size: 13px;
    color: var(--wf-text-3);
}

.profile-desc :deep(.el-descriptions__label) {
    width: 96px;
    color: var(--wf-text-3);
}

.profile-url {
    display: inline-block;
    max-width: 280px;
    vertical-align: bottom;
}
</style>
