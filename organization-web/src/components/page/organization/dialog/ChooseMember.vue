<template>
    <div class="pk-dialog">
        <div class="pk-panes">
            <div class="pk-pane">
                <div class="pk-pane-head">
                    <span class="pk-pane-title">组织架构</span>
                </div>
                <el-scrollbar class="pk-pane-body">
                    <el-tree :data="rootOrganizations"
                             ref="tree"
                             :props="treeDefaultProps"
                             :expand-on-click-node="true"
                             show-checkbox
                             node-key="employeeId"
                             check-on-click-node
                             :default-checked-keys="initialCheckedMembers"
                             @check-change="onCheckChange"
                             check-strictly
                             :render-after-expand="false"
                             lazy
                             :load="loadNode"
                             empty-text="暂无成员数据"
                             @node-click="handleNodeClick"/>
                </el-scrollbar>
            </div>

            <div class="pk-pane">
                <div class="pk-pane-head">
                    <span class="pk-pane-title">已选 {{ checkedMembers.length }} 个成员</span>
                    <el-button link type="primary" :disabled="!checkedMembers.length" @click="resetChecked">
                        清空
                    </el-button>
                </div>
                <el-scrollbar class="pk-pane-body">
                    <p v-if="!checkedMembers.length" class="pk-empty">未选择任何成员</p>
                    <div v-for="member in checkedMembers" :key="member.employeeId" class="pk-row">
                        <span class="wf-ellipsis">{{ member.name }}</span>
                        <el-icon class="pk-remove" @click="unCheckDepartment(member)">
                            <Close/>
                        </el-icon>
                    </div>
                </el-scrollbar>
            </div>
        </div>

        <div class="wf-dialog-actions">
            <span v-if="checkedMembers.length > maxChooseCount" class="over-tip">
                {{ '最多允许选择 ' + maxChooseCount + ' 个成员' }}
            </span>
            <el-button @click="onCancel">取消</el-button>
            <el-button type="primary"
                       :disabled="checkedMembers.length === 0 || checkedMembers.length > maxChooseCount"
                       @click="() => {onConfirm(checkedMembers) ; resetChecked()}">确定
            </el-button>
        </div>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";

export default {
    name: "ChooseMember",
    props: {
        initialCheckedMembers: {
            type: Array,
            default: () => [],
            required: false,
        },
        maxChooseCount: {
            type: Number,
            default: Number.MAX_VALUE,
            required: false,
        },
        onCancel: {
            type: Function,
            required: true,
        },
        onConfirm: {
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
            treeDefaultProps: {
                label: 'name',
                children: 'children',
                isLeaf: 'isLeaf',// el-tree 默认不会找这个属性
                // 只有成员节点才允许勾选，部门节点的复选框用 css 隐藏掉
                class: (data) => (data.showCheckbox ? '' : 'hide-node-checkbox'),
                disabled: (data) => !data.showCheckbox,
            },
            checkedMembers: [],
        }
    },

    computed: {
        rootOrganizations() {
            return this.orgStore.rootOrganizations;
        }
    },

    mounted() {
        this.$refs.tree.setCheckedNodes(this.initialCheckedMembers);
    },

    methods: {
        handleNodeClick(data) {
            console.log('node click', data)
            if (!data._orgWithChildren && data.id) {
                this.orgStore.queryOrganizationWithChildren(data)
            }
            this.currentOrg = data;
        },
        async loadNode(node, resolve) {
            console.log('to load data', node)
            let data = node.data;
            if (!data.employees) {
                data.employees = [];
            }
            if (!data._orgWithChildren && data.id) {
                await this.orgStore.queryOrganizationWithChildren(data)
                this.currentOrg = data;
                let employees = data.employees.map(e => {
                    return {
                        showCheckbox: true,
                        isLeaf: true,
                        ...e
                    }
                })
                resolve([...data.children, ...employees]);
            } else {
                let employees = data.employees.map(e => {
                    return {
                        showCheckbox: true,
                        isLeaf: true,
                        ...e
                    }
                })
                resolve(data.children ? [...data.children, ...employees] : data);
            }
        },

        resetChecked() {
            this.$refs.tree.setCheckedNodes([]);
        },
        onCheckChange() {
            this.checkedMembers = this.$refs.tree.getCheckedNodes(true).filter(o => o.employeeId);
        },
        unCheckDepartment(department) {
            this.checkedMembers = this.checkedMembers.filter(o => o.employeeId && o.employeeId !== department.employeeId);
            this.$refs.tree.setCheckedNodes(this.checkedMembers);
        },
    }
}
</script>

<style scoped>
/* 部门节点不显示复选框，只有成员可以勾选 */
:deep(.hide-node-checkbox > .el-tree-node__content > .el-checkbox) {
    display: none;
}

.over-tip {
    margin-right: auto;
    font-size: 13px;
    color: var(--wf-danger);
}
</style>
