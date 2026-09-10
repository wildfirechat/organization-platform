<template>
    <div class="department-page">
        <PageHeader v-if="showHeader" title="部门" description="以表格形式查看部门层级、人数与部门群信息"/>

        <div class="wf-panel department-panel">
            <el-table
                :data="rootOrganizations"
                row-key="id"
                lazy
                :load="loadNode"
                empty-text="暂无部门数据"
                :tree-props="{children: 'children', hasChildren: 'hasChildren'}"
                @selection-change="handleSelectionChange">
                <el-table-column prop="name" label="部门名称" min-width="200"/>
                <el-table-column prop="memberCount" label="部门人数" width="110" align="right">
                    <template v-slot="scope">
                        <span class="wf-num">{{ scope.row.memberCount || 0 }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="managerName" label="负责人" min-width="140">
                    <template v-slot="scope">
                        <span v-if="scope.row.managerName">{{ scope.row.managerName }}</span>
                        <span v-else class="wf-muted">未设置</span>
                    </template>
                </el-table-column>
                <el-table-column label="部门群" min-width="160">
                    <template v-slot="scope">
                        <el-tag v-if="scope.row.groupId" size="small" type="success" effect="light">已创建</el-tag>
                        <span v-else class="wf-muted">未创建</span>
                    </template>
                </el-table-column>
                <el-table-column prop="office" label="办公地点" min-width="140">
                    <template v-slot="scope">
                        <span v-if="scope.row.office">{{ scope.row.office }}</span>
                        <span v-else class="wf-muted">—</span>
                    </template>
                </el-table-column>
            </el-table>
        </div>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";
import PageHeader from "@/components/common/PageHeader.vue";

export default {
    name: "Department",
    components: {PageHeader},
    props: {
        showHeader: {
            type: Boolean,
            default: true
        }
    },
    data() {
        return {
            multipleSelection: [],
        }
    },

    setup() {
        const orgStore = useOrgStore();
        return {orgStore};
    },

    computed: {
        rootOrganizations() {
            return this.orgStore.rootOrganizations;
        }
    },

    methods: {
        handleSelectionChange(val) {
            this.multipleSelection = val;
        },
        async loadNode(tree, treeNode, resolve) {
            console.log('to load data', tree, treeNode)
            let data = tree;
            if (!data._orgWithChildren && data.id) {
                await this.orgStore.queryOrganizationWithChildren(data)
                this.currentOrg = data;
                resolve(data.children);
            } else {
                resolve(data.children ? data.children : data);
            }
        }
    }
}
</script>

<style scoped>
.department-page {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
}

.department-panel {
    padding-bottom: 16px;
}
</style>
