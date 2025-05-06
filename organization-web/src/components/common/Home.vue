<template>
    <el-container style="height: 100%">
        <el-aside width="200px" style="background: #f2f3f5">
            <div style="height: 60px; display: flex; justify-content: center;align-items: center" @click="go2home">
                <p>野火组织架构管理后台</p>
            </div>
            <el-menu router>
                <el-menu-item index="/organization/departmentanduser">成员与部门</el-menu-item>
            </el-menu>
        </el-aside>
        <el-container :class="{'content-collapse':collapse}">
            <el-header style="text-align: left; font-size: 14px; display: flex; padding-right: 40px; background: #f2f3f5">
                <Breadcrumb/>
                <span style="flex: 1"> </span>
                <el-dropdown>
                    <i class="el-icon-setting" style="margin-right: 15px"></i>
                    <el-dropdown-menu slot="dropdown">
                        <el-dropdown-item @click.native="logout">退出</el-dropdown-item>
                        <el-dropdown-item @click.native="modifyPwdDialogVisible = true">修改密码</el-dropdown-item>
                    </el-dropdown-menu>
                </el-dropdown>
                <span>{{ account.displayName }}</span>
            </el-header>
            <el-main style="padding: 0">
                <transition name="move" mode="out-in">
                    <keep-alive>
                        <router-view></router-view>
                    </keep-alive>
                </transition>
            </el-main>


            <el-dialog title="修改密码" :visible.sync="modifyPwdDialogVisible">
                <el-form :model="updatePwdRequest" ref="updatePwdForm" :rules="rules">
                    <el-form-item label="旧密码" :label-width="formLabelWidth" prop="oldPwd">
                        <el-input v-model="updatePwdRequest.oldPwd" autocomplete="off" placeholder="请输入旧密码"></el-input>
                    </el-form-item>
                    <el-form-item label="新密码" :label-width="formLabelWidth" prop="newPwd">
                        <el-input v-model="updatePwdRequest.newPwd" autocomplete="off" placeholder="请输入新密码"></el-input>
                    </el-form-item>
                    <el-form-item label="确认新密码" :label-width="formLabelWidth" prop="confirmNewPwd">
                        <el-input v-model="updatePwdRequest.confirmNewPwd" autocomplete="off" placeholder="请确认新密码"></el-input>
                    </el-form-item>
                </el-form>
                <div slot="footer" class="dialog-footer">
                    <el-button @click="modifyPwdDialogVisible = false">取 消</el-button>
                    <el-button type="primary" @click="updatePwd('updatePwdForm')">修 改</el-button>
                </div>
            </el-dialog>

        </el-container>
    </el-container>
</template>

<script>
import {useUserStore} from "@/store/stores/userStore";
import {useOrgStore} from "@/store/stores/orgStore";
import Breadcrumb from "@/components/common/Breadcrumb";

export default {
    data() {
        return {
            tagsList: [],
            collapse: false,
            modifyPwdDialogVisible: false,
            formLabelWidth: '120px',
            updatePwdRequest: {},
            rules: {
                oldPwd: [
                    {required: true, message: '旧密码不能为空', trigger: 'blur'}
                ],
                newPwd: [
                    {required: true, message: '新密码不能为空', trigger: 'blur'}
                ],
                confirmNewPwd: [
                    {required: true, message: '新密码不能为空', trigger: 'blur'}
                ]
            },
        }
    },
    components: {
        Breadcrumb
    },

    setup() {
        const userStore = useUserStore();
        const orgStore = useOrgStore();
        return {userStore, orgStore};
    },

    created() {
        this.userStore.getAccount();
        this.orgStore.getRootOrganizationsWithChildren();
    },

    computed: {
        account() {
            return this.userStore.account;
        },
        rootOrganizations() {
            return this.orgStore.rootOrganizations;
        },
    },

    methods: {
        go2home() {
            const defaultPath = this.rootOrganizations.length > 0 ? '/organization/departmentanduser' : '/organization/departmentanduser/import-member';
            if (this.$router.history.current.path !== defaultPath) {
                this.$router.replace(defaultPath);
            }
        },
        logout() {
            localStorage.clear();
            this.$router.replace('/login')
        },
        updatePwd(formName) {
            this.$refs[formName].validate((valid) => {
                if (valid) {

                    if (this.updatePwdRequest.newPwd !== this.updatePwdRequest.confirmNewPwd) {
                        this.$message.error('两次输入的密码不一致');
                    } else {
                        this.userStore.updatePwd({
                            oldPassword: this.updatePwdRequest.oldPwd,
                            newPassword: this.updatePwdRequest.newPwd
                        });
                        this.modifyPwdDialogVisible = false;
                    }
                }
            });
        }
    },
    watch: {
        rootOrganizations: {
            handler(newVal) {
                // 当根组织数据变化时，检查是否需要跳转到批量导入页面
                if (newVal.length === 0 && this.$router.history.current.path !== '/organization/departmentanduser/import-member') {
                    this.$router.push('/organization/departmentanduser/import-member')
                } else if (newVal.length > 0 && this.$router.history.current.path !== '/organization/departmentanduser') {
                    this.$router.push('/organization/departmentanduser')
                }
            },
            deep: true,
            immediate: true
        }
    }
}
</script>

<style lang="css" scoped>
.el-header {
    color: #333;
    line-height: 60px;
}

.el-aside {
    color: #333;
}

.el-menu {
    background: #ecf5ff;
}

</style>
