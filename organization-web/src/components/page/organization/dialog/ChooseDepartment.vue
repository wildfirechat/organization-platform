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
                             :expand-on-click-node="false"
                             show-checkbox
                             node-key="id"
                             check-on-click-node
                             @check-change="onCheckChange"
                             check-strictly
                             :render-after-expand="false"
                             lazy
                             :load="loadNode"
                             empty-text="暂无部门数据"
                             @node-click="handleNodeClick"/>
                </el-scrollbar>
            </div>

            <div class="pk-pane">
                <div class="pk-pane-head">
                    <span class="pk-pane-title">已选 {{ checkedDepartments.length }} 个部门</span>
                    <el-button link type="primary" :disabled="!checkedDepartments.length" @click="resetChecked">
                        清空
                    </el-button>
                </div>
                <el-scrollbar class="pk-pane-body">
                    <p v-if="!checkedDepartments.length" class="pk-empty">未选择任何部门</p>
                    <div v-for="depart in checkedDepartments" :key="depart.id" class="pk-row">
                        <span class="wf-ellipsis">{{ depart.name }}</span>
                        <el-icon class="pk-remove" @click="unCheckDepartment(depart)">
                            <Close/>
                        </el-icon>
                    </div>
                </el-scrollbar>
            </div>
        </div>

        <div class="wf-dialog-actions">
            <el-button @click="_onCancel">取消</el-button>
            <el-button type="primary" :disabled="!checkedDepartments.length" @click="_onConfirm">确定</el-button>
        </div>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";

export default {
    name: "ChooseDepartment",
    props: {
        checkedDepartment: {
            default: null,
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
            checkedDepartments: [],
        }
    },

    computed: {
        rootOrganizations() {
            return this.orgStore.rootOrganizations;
        }
    },

    mounted() {
        if (this.checkedDepartment) {
            this.$refs.tree.setCheckedNodes([this.checkedDepartment])
        }
    },

    methods: {
        _onCancel() {
            this.onCancel && this.onCancel();
            this.checkedDepartments = [];
        },
        _onConfirm() {
            this.onConfirm && this.onConfirm(this.checkedDepartments);
            this.checkedDepartments = [];
        },
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
            if (!data._orgWithChildren && data.id) {
                await this.orgStore.queryOrganizationWithChildren(data)
                this.currentOrg = data;
                resolve(data.children);
            } else {
                resolve(data.children ? data.children : data);
            }
        },

        resetChecked() {
            this.$refs.tree.setCheckedNodes([])
        },
        onCheckChange() {
            this.checkedDepartments = this.$refs.tree.getCheckedNodes();
        },
        unCheckDepartment(department) {
            this.checkedDepartments = this.checkedDepartments.filter(d => d.id !== department.id);
            this.$refs.tree.setCheckedNodes(this.checkedDepartments);
        }
    }
}
</script>

