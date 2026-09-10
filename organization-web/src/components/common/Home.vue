<template>
    <div class="layout">
        <AppSidebar :collapsed="collapsed" @brand-click="go2home"/>

        <div class="main">
            <AppHeader
                :collapsed="collapsed"
                :account="account"
                @toggle-collapse="collapsed = !collapsed"
                @update-pwd="openUpdatePwd"
                @logout="logout"/>

            <div class="content-wrap">
                <div class="content">
                    <router-view v-slot="{ Component }">
                        <transition name="fade-slide" mode="out-in">
                            <keep-alive>
                                <component :is="Component"/>
                            </keep-alive>
                        </transition>
                    </router-view>
                </div>
            </div>
        </div>

        <el-dialog title="修改密码" v-model="modifyPwdDialogVisible" width="460px" @closed="resetPwdForm">
            <el-form :model="updatePwdRequest" ref="updatePwdForm" :rules="rules" label-width="90px">
                <el-form-item label="旧密码" prop="oldPwd">
                    <el-input v-model="updatePwdRequest.oldPwd" type="password" show-password
                              autocomplete="current-password" placeholder="请输入旧密码"/>
                </el-form-item>
                <el-form-item label="新密码" prop="newPwd">
                    <el-input v-model="updatePwdRequest.newPwd" type="password" show-password
                              autocomplete="new-password" placeholder="请输入新密码"/>
                </el-form-item>
                <el-form-item label="确认新密码" prop="confirmNewPwd">
                    <el-input v-model="updatePwdRequest.confirmNewPwd" type="password" show-password
                              autocomplete="new-password" placeholder="请再次输入新密码"
                              @keyup.enter="updatePwd('updatePwdForm')"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <div class="wf-actions">
                    <el-button @click="modifyPwdDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="updatePwd('updatePwdForm')">确定修改</el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script>
import {useUserStore} from "@/store/stores/userStore";
import {useOrgStore} from "@/store/stores/orgStore";
import AppSidebar from "@/components/common/AppSidebar.vue";
import AppHeader from "@/components/common/AppHeader.vue";

export default {
    name: 'Home',
    components: {AppHeader, AppSidebar},
    data() {
        return {
            collapsed: false,
            modifyPwdDialogVisible: false,
            updatePwdRequest: {},
            rules: {
                oldPwd: [
                    {required: true, message: '旧密码不能为空', trigger: 'blur'}
                ],
                newPwd: [
                    {required: true, message: '新密码不能为空', trigger: 'blur'}
                ],
                confirmNewPwd: [
                    {required: true, message: '请再次输入新密码', trigger: 'blur'}
                ]
            }
        }
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

    mounted() {
        this.syncCollapseWithViewport();
        window.addEventListener('resize', this.syncCollapseWithViewport);
    },

    beforeUnmount() {
        window.removeEventListener('resize', this.syncCollapseWithViewport);
    },

    computed: {
        account() {
            return this.userStore.account;
        },
        rootOrganizations() {
            return this.orgStore.rootOrganizations;
        },
        rootOrganizationsLoaded() {
            return this.orgStore.rootOrganizationsLoaded;
        }
    },

    methods: {
        // 窄屏自动折叠侧边栏，给内容区留出空间
        syncCollapseWithViewport() {
            if (window.innerWidth < 1200) {
                this.collapsed = true;
            }
        },
        go2home() {
            if (!this.rootOrganizationsLoaded) {
                return;
            }
            const defaultPath = this.rootOrganizations.length > 0 ? '/organization/departmentanduser' : '/organization/departmentanduser/import-member';
            if (this.$route.path !== defaultPath) {
                this.$router.replace(defaultPath);
            }
        },
        logout() {
            localStorage.clear();
            this.$router.replace('/login')
        },
        openUpdatePwd() {
            this.modifyPwdDialogVisible = true;
        },
        resetPwdForm() {
            this.updatePwdRequest = {};
            this.$refs.updatePwdForm && this.$refs.updatePwdForm.clearValidate();
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
        rootOrganizationsLoaded: {
            handler(loaded) {
                if (!loaded) {
                    return;
                }
                // 组织数据加载完成后，根据是否有根组织决定跳转
                if (this.rootOrganizations.length === 0 && this.$route.path !== '/organization/departmentanduser/import-member') {
                    this.$router.push('/organization/departmentanduser/import-member')
                } else if (this.rootOrganizations.length > 0 && this.$route.path !== '/organization/departmentanduser') {
                    this.$router.push('/organization/departmentanduser')
                }
            },
            immediate: true
        }
    }
}
</script>

<style scoped>
.layout {
    display: flex;
    height: 100%;
    overflow: hidden;
}

.main {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-width: 0;
}

.content-wrap {
    flex: 1;
    min-height: 0;
    overflow: auto;
    background: var(--wf-bg);
}

/* 内容区用 flex 列布局，页面根节点可以用 flex:1 撑满剩余高度 */
.content {
    display: flex;
    flex-direction: column;
    min-height: 100%;
    padding: 20px;
}
</style>
