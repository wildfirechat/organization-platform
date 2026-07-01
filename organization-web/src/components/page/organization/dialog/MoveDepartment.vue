<template>
    <div>
        <p class="title">移动部门</p>
        <p style="margin: 10px 0; color: #646a73;">请选择 "{{ currentDepartment.name }}" 的新位置</p>
        <div class="tree-container">
            <el-tree
                ref="tree"
                :props="defaultProps"
                :expand-on-click-node="true"
                node-key="id"
                lazy
                :load="loadNode"
                :highlight-current="true"
                @node-click="handleNodeClick">
            </el-tree>
        </div>
        <div class="action-container">
            <el-button @click="onCancel">取消</el-button>
            <el-button type="primary" :disabled="!selectedParent" @click="onConfirmClick">确定</el-button>
        </div>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";

export default {
    name: "MoveDepartment",
    props: {
        currentDepartment: {
            type: Object,
            required: true,
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
            defaultProps: {
                label: 'name',
                children: 'children',
                isLeaf: 'leaf',
            },
            selectedParent: null,
            orgMap: new Map(),
        }
    },

    methods: {
        async loadNode(node, resolve) {
            console.log('[MoveDepartment] loadNode level=', node.level, 'data=', node.data);
            try {
                if (node.level === 0) {
                    let roots = this.orgStore.rootOrganizations || [];
                    console.log('[MoveDepartment] root orgs count=', roots.length);
                    return resolve(this.buildTreeNodes(roots));
                }

                let org = this.orgMap.get(node.data.id);
                console.log('[MoveDepartment] expand org id=', node.data.id, 'found=', !!org);

                if (org && !org._orgWithChildren && org.id) {
                    console.log('[MoveDepartment] queryOrganizationWithChildren id=', org.id);
                    await this.orgStore.queryOrganizationWithChildren(org);
                }

                let children = org && org.children ? org.children : [];
                console.log('[MoveDepartment] children count=', children.length);
                resolve(this.buildTreeNodes(children));
            } catch (e) {
                console.error('[MoveDepartment] loadNode error', e);
                resolve([]);
            }
        },

        buildTreeNodes(orgs) {
            console.log('[MoveDepartment] buildTreeNodes count=', (orgs || []).length);
            return (orgs || []).map(child => {
                this.orgMap.set(child.id, child);
                return {
                    id: child.id,
                    name: child.name,
                    leaf: child.leaf,
                    disabled: this.isSelfOrDescendant(child),
                }
            });
        },

        isSelfOrDescendant(org) {
            if (!org || !this.currentDepartment) {
                return false;
            }
            if (org.id === this.currentDepartment.id) {
                return true;
            }
            if (org.children && org.children.length > 0) {
                return org.children.some(child => this.isSelfOrDescendant(child));
            }
            return false;
        },

        handleNodeClick(data) {
            console.log('[MoveDepartment] node click id=', data.id, 'name=', data.name, 'disabled=', data.disabled);
            if (data.disabled || this.isSelfOrDescendant(data)) {
                this.selectedParent = null;
                return;
            }
            this.selectedParent = data;
        },

        onConfirmClick() {
            console.log('[MoveDepartment] confirm selectedParent=', this.selectedParent);
            if (!this.selectedParent) {
                this.$message.error('请选择新位置');
                return;
            }
            try {
                let result = {
                    id: this.selectedParent.id,
                    name: this.selectedParent.name,
                };
                console.log('[MoveDepartment] call onConfirm with', result);
                this.onConfirm(result);
            } catch (e) {
                console.error('[MoveDepartment] onConfirm error', e);
                throw e;
            }
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

.tree-container {
    height: 400px;
    overflow-y: auto;
    border: 1px solid #e6e6e6;
    padding: 10px;
    margin: 10px 0;
}

.action-container {
    display: flex;
    justify-content: flex-end;
    margin-right: 10px;
}
</style>
