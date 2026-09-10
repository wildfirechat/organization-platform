<template>
    <div v-if="employee">
        <p class="lead">为 <b>{{ employee.name }}</b> 设置新的登录密码：</p>

        <el-form :model="form" :rules="rules" ref="form" label-position="top">
            <el-form-item label="新密码" prop="password">
                <el-input v-model="form.password" type="password" placeholder="请输入新密码，至少 6 位" show-password/>
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入新密码" show-password
                          @keyup.enter="confirmUpdate"/>
            </el-form-item>
        </el-form>

        <div class="wf-dialog-actions">
            <el-button @click="onCancel">取消</el-button>
            <el-button type="primary" :disabled="!form.password || !form.confirmPassword" @click="confirmUpdate">
                确定
            </el-button>
        </div>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";

export default {
    name: "UpdateEmployeePwd",
    props: {
        employee: {
            type: Object,
            required: true
        },
        onSuccess: {
            type: Function,
            required: false,
            default: () => {
            }
        },
        onCancel: {
            type: Function,
            required: true
        }
    },

    setup() {
        const orgStore = useOrgStore();
        return {orgStore};
    },

    data() {
        const validateConfirmPassword = (rule, value, callback) => {
            if (value !== this.form.password) {
                callback(new Error('两次输入的密码不一致'));
            } else {
                callback();
            }
        };

        return {
            form: {
                password: '',
                confirmPassword: ''
            },
            rules: {
                password: [
                    {required: true, message: '请输入新密码', trigger: 'blur'},
                    {min: 6, message: '密码长度不能少于6位', trigger: 'blur'}
                ],
                confirmPassword: [
                    {required: true, message: '请确认新密码', trigger: 'blur'},
                    {validator: validateConfirmPassword, trigger: 'blur'}
                ]
            }
        };
    },

    methods: {
        confirmUpdate() {
            this.$refs.form.validate((valid) => {
                if (valid) {
                    this.orgStore.updateEmployeePassword(this.employee.employeeId, this.form.password)
                        .then(() => {
                            this.$message.success('密码修改成功');
                            this.onSuccess();
                        })
                        .catch((error) => {
                            console.error('修改密码失败', error);
                            this.$message.error('修改密码失败');
                        });
                }
            });
        }
    }
}
</script>

<style scoped>
.lead {
    margin-bottom: 16px;
    color: var(--wf-text-2);
}
</style>
