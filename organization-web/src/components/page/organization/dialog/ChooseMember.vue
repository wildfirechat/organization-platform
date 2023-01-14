<template>
    <div>
        <p class="title">请选择成员</p>
        <div class="container">
            <div class="org-container">
                <el-input class="input" v-model="input" placeholder="请输入成员名称"></el-input>
                <Eltree2 v-if="!input" :data="rootOrganizations"
                         ref="tree"
                         :props="treeDefaultProps"
                         :expand-on-click-node="true"
                         node-key="employeeId"
                         check-on-click-node
                         :default-checked-keys="initialCheckedMembers"
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
                    <span>{{ '已选择： ' + checkedMembers.length + '个成员' }}</span>
                    <el-button type="text" @click="resetChecked">清空</el-button>
                </div>
                <el-table
                    ref="multipleTable"
                    :data="checkedMembers"
                    empty-text="未选择任何成员"
                    tooltip-effect="dark"
                    style="width: 100%"
                    :show-header="false"
                    :cell-style="{padding: '0', height: '50px'}">
                    <el-table-column
                        prop="name"
                        label="成员名"
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
            <p class="tip" v-if="checkedMembers.length > maxChooseCount">{{ '最多允许选择' + maxChooseCount + '个成员' }}</p>
            <el-button @click="onCancel">取消</el-button>
            <el-button type="primary" :disabled="checkedMembers.length === 0 || checkedMembers.length > maxChooseCount" @click="() => {onConfirm(checkedMembers) ; resetChecked()}">确定</el-button>
        </div>
    </div>
</template>

<script>
import {mapState} from "vuex";
import Eltree2 from '../../../../../vendor/tree/src/tree'

export default {
    name: "ChooseDepartment",
    components: {Eltree2},
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

    data() {
        return {
            treeDefaultProps: {
                label: 'name',
                children: 'children',
                isLeaf: 'isLeaf',// el-tree 默认不会找这个属性
            },
            input: '',
            checkedMembers: [],
        }
    },

    computed: {
        ...mapState({
            rootOrganizations: state => state.org.rootOrganizations,
        })
    },
    mounted() {
        this.$refs.tree.setCheckedNodes(this.initialCheckedMembers);
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
            if (!data.employees) {
                data.employees = [];
            }
            if (!data._orgWithChildren && data.id) {
                await this.$store.dispatch('queryOrganizationWithChildren', data)
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
    align-items: center;
    margin-right: 20px;
}

.action-container .tip {
    color: red;
    margin-right: 20px;
}


</style>