<template>
    <div>
        <p class="title">请选择部门</p>
        <div class="container">
            <div class="org-container">
                <el-input class="input" v-model="input" placeholder="请输入部门名称"></el-input>
                <Eltree2 v-if="!input" :data="rootOrganizations"
                         ref="tree"
                         :expand-on-click-node="false"
                         show-checkbox
                         node-key="id"
                         check-on-click-node
                         @check-change="onCheckChange"
                         check-strictly
                         :render-after-expand='false'
                         lazy
                         :load="loadNode"
                         @node-click="handleNodeClick">
                </Eltree2>
            </div>
            <div class="checked-org-container">
                <div style="width: 100%; display: flex; justify-content: space-between; align-items: center">
                    <span>{{ '已选择： ' + checkedDepartments.length + '个部门' }}</span>
                    <el-button type="text" @click="resetChecked">清空</el-button>
                </div>
                <el-table
                    ref="multipleTable"
                    :data="checkedDepartments"
                    empty-text="未选择任何部门"
                    tooltip-effect="dark"
                    style="width: 100%"
                    :show-header="false"
                    :cell-style="{padding: '0', height: '50px'}">
                    <el-table-column
                        prop="name"
                        label="部门名"
                        width="240">
                    </el-table-column>
                    <el-table-column align="right">
                        <template v-slot="scope">
                            <el-button class="f-btn" @click="unCheckDepartment(scope.row)" type="text" icon="el-icon-close" size="small"></el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </div>
        </div>
        <div class="action-container">
            <el-button @click="onCancel">取消</el-button>
            <el-button type="primary" @click="onConfirm(checkedDepartments)">确定</el-button>
        </div>
    </div>
</template>

<script>
import {mapState} from "vuex";
import Eltree2 from '../../../../../vendor/tree/src/tree'

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

    data() {
        return {
            input: '',
            checkedDepartments: [],
        }
    },

    computed: {
        ...mapState({
            rootOrganizations: state => state.org.rootOrganizations,
        })
    },
    mounted() {
        this.$refs.tree.setCheckedNodes([this.checkedDepartment])
    },
    methods: {
        handleNodeClick(data) {
            console.log('node click', data)
            if (!data._orgWithChildren && data.id) {
                this.$store.dispatch('queryOrganizationWithChildren', data)
            }
            this.currentOrg = data;
        },
        async loadNode(node, resolve) {
            console.log('to load data', node)
            let data = node.data;
            if (!data._orgWithChildren && data.id) {
                await this.$store.dispatch('queryOrganizationWithChildren', data)
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
    },
    components: {
        Eltree2
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

.container {
    display: flex;
    margin: 20px;
}

.org-container {
    width: 50%;
    padding: 20px 20px 20px 10px;
    border-right: 1px solid lightgrey;
    height: 500px;
    overflow-y: auto;
}

.org-container .input {
    margin-bottom: 10px;
    padding-left: 10px;
}

.checked-org-container {
    width: 50%;
    padding: 20px;
}

.action-container {
    display: flex;
    justify-content: flex-end;
    margin-right: 20px;
}


</style>