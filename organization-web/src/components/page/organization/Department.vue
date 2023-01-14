<template>
    <el-main style="padding: 0">
        <div style="display: none; flex-direction: row; justify-content: space-between; align-items: center">
            <el-button type="primary">新建部门</el-button>
        </div>
        <el-table
            :data="rootOrganizations"
            style="width: 100%;margin-bottom: 20px;"
            row-key="id"
            lazy
            :load="loadNode"
            :tree-props="{children: 'children', hasChildren: 'hasChildren'}"
            @selection-change="handleSelectionChange">
            <el-table-column
                type="selection"
                width="55">
            </el-table-column>
            <el-table-column
                prop="name"
                label="部门名称"
                width="180">
            </el-table-column>
            <el-table-column
                prop="memberCount"
                label="部门人数"
                width="180">
            </el-table-column>
            <el-table-column
                prop="managerId"
                label=" 负责人">
            </el-table-column>
            <el-table-column
                prop="groupId"
                label="部门群名称">
            </el-table-column>
            <el-table-column
                prop="office"
                label="部门群群主">
            </el-table-column>
            <el-table-column>
                <template v-slot="scope">
                    <el-button class="f-btn" @click="handleClickEmployee(scope.row)" type="text" size="small">编辑部门</el-button>
                    <el-button class="f-btn" @click="handleClickEmployee(scope.row)" type="text" size="small">添加子部门</el-button>
                    <el-button class="f-btn" @click="handleClickEmployee(scope.row)" type="text" size="small" style="color: red">删除</el-button>
                </template>
            </el-table-column>
        </el-table>
    </el-main>
</template>

<script>
import {mapState} from "vuex";

export default {
    name: "createDepartment",
    data() {
        return {
            multipleSelection: [],
            input: '',
        }
    },
    computed: {
        ...mapState({
            rootOrganizations: state => state.org.rootOrganizations,
        })
    },
    methods: {
        toggleSelection(rows) {
            if (rows) {
                rows.forEach(row => {
                    this.$refs.multipleTable.toggleRowSelection(row);
                });
            } else {
                this.$refs.multipleTable.clearSelection();
            }
        },
        handleSelectionChange(val) {
            this.multipleSelection = val;
        },
        async loadNode(tree, treeNode, resolve) {
            console.log('to load data', tree, treeNode)
            let data = tree;
            if (!data._orgWithChildren && data.id) {
                await this.$store.dispatch('queryOrganizationWithChildren', data)
                this.currentOrg = data;
                resolve(data.children);
            } else {
                resolve(data.children ? data.children : data);
            }
        },

        handleClickEmployee(){

        }
    }
}
</script>

<style scoped>
.f-btn {
    display: none;
}

.el-table__body tr:hover .f-btn {
    display: inline;
}
</style>