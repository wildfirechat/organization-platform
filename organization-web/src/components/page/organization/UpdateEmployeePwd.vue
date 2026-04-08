<template>
    <div>
        <div v-if="employee">
            <p>修改 <b>{{ employee.name }}</b> 的密码：</p>
            <el-form style="margin: 20px 0;" :model="form" :rules="rules" ref="form">
                <el-form-item prop="password">
                    <el-input 
                        v-model="form.password" 
                        type="password" 
                        placeholder="请输入新密码"
                        show-password>
                    </el-input>
                </el-form-item>
                <el-form-item prop="confirmPassword">
                    <el-input 
                        v-model="form.confirmPassword" 
                        type="password" 
                        placeholder="请确认新密码"
                        show-password>
                    </el-input>
                </el-form-item>
            </el-form>
            <div style="text-align: right; margin-top: 20px;">
                <el-button @click="onCancel">取消</el-button>
                <el-button type="primary" :disabled="!form.password || !form.confirmPassword" @click="confirmUpdate">确定</el-button>
            </div>
        </div>
    </div>
</template>

<script>
import { useOrgStore } from "@/store/stores/orgStore";

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
            default: () => {}
        },
        onCancel: {
            type: Function,
            required: true
        }
    },

    setup() {
        const orgStore = useOrgStore();
        return { orgStore };
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
                    { required: true, message: '请输入新密码', trigger: 'blur' },
                    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
                ],
                confirmPassword: [
                    { required: true, message: '请确认新密码', trigger: 'blur' },
                    { validator: validateConfirmPassword, trigger: 'blur' }
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
</style>
