<template>
    <div class="pwd-page">
        <PageHeader :title="$t('home.update_password')" description="修改当前管理员账号的登录密码"/>

        <div class="wf-panel form-panel">
            <el-form ref="pwdForm" :model="user" :rules="rules" label-width="110px">
                <el-form-item :label="$t('setting.original_password')" prop="oldPwd">
                    <el-input v-model.trim="user.oldPwd" type="password" show-password
                              autocomplete="current-password"/>
                </el-form-item>
                <el-form-item :label="$t('setting.new_password')" prop="newPwd">
                    <el-input v-model.trim="user.newPwd" type="password" show-password autocomplete="new-password"/>
                </el-form-item>
                <el-form-item :label="$t('setting.confirm_new_password')" prop="newPwdRe">
                    <el-input v-model.trim="user.newPwdRe" type="password" show-password autocomplete="new-password"
                              @keyup.enter="save"/>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" :loading="loading" @click="save">{{ $t('common.save') }}</el-button>
                </el-form-item>
            </el-form>
        </div>
    </div>
</template>

<script>
import {useUserStore} from "@/store/stores/userStore";
import PageHeader from "@/components/common/PageHeader.vue";

export default {
    name: 'UpdatePwd',
    components: {PageHeader},
    setup() {
        const userStore = useUserStore();
        return {userStore};
    },

    data() {
        return {
            loading: false,
            user: {},
            rules: {
                oldPwd: [
                    {required: true, message: this.$t('setting.original_password'), trigger: 'blur'}
                ],
                newPwd: [
                    {required: true, message: this.$t('setting.new_password'), trigger: 'blur'}
                ],
                newPwdRe: [
                    {required: true, message: this.$t('setting.confirm_new_password'), trigger: 'blur'}
                ]
            }
        }
    },
    methods: {
        save() {
            this.$refs.pwdForm.validate((valid) => {
                if (!valid) {
                    return;
                }
                if (this.user.newPwd !== this.user.newPwdRe) {
                    this.$message.error(this.$t('setting.new_password_error'))
                    return
                }
                this.loading = true
                this.userStore.updatePwd(this.user).then(() => {
                    this.$message.success(this.$t('common.action_success'))
                }).finally(() => {
                    this.loading = false
                })
            });
        }
    }
}
</script>

<style scoped>
.pwd-page {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
}

.form-panel {
    max-width: 560px;
    padding-bottom: 20px;
}
</style>
