<template>
    <div>
        <p class="title">更新用户信息</p>
        <el-form label-position="right" size="medium" class="demo-form-inline" label-width="100px">
            <el-row :gutter="20">
                <el-col :span="12">
                    <el-form-item label="用户ID">
                        <el-input v-model="form.employeeId" disabled></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="12">
                    <el-form-item label="姓名">
                        <el-input v-model.trim="form.name" placeholder="请输入姓名"></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row :gutter="20">
                <el-col :span="12">
                    <el-form-item label="职位">
                        <el-input v-model.trim="form.title" placeholder="请输入职位"></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="12">
                    <el-form-item label="级别">
                        <el-input-number v-model="form.level" :min="0" style="width: 100%"></el-input-number>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row :gutter="20">
                <el-col :span="12">
                    <el-form-item label="手机号">
                        <el-input v-model.trim="form.mobile" placeholder="请输入手机号"></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="12">
                    <el-form-item label="邮箱">
                        <el-input v-model.trim="form.email" placeholder="请输入邮箱"></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row :gutter="20">
                <el-col :span="12">
                    <el-form-item label="分机号">
                        <el-input v-model.trim="form.ext" placeholder="请输入分机号"></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="12">
                    <el-form-item label="办公地点">
                        <el-input v-model.trim="form.office" placeholder="请输入办公地点"></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row :gutter="20">
                <el-col :span="12">
                    <el-form-item label="城市">
                        <el-input v-model.trim="form.city" placeholder="请输入城市"></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="12">
                    <el-form-item label="工号">
                        <el-input v-model.trim="form.jobNumber" placeholder="请输入工号"></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row :gutter="20">
                <el-col :span="12">
                    <el-form-item label="入职时间">
                        <el-input v-model.trim="form.joinTime" placeholder="请输入入职时间"></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="12">
                    <el-form-item label="排序">
                        <el-input-number v-model="form.sort" style="width: 100%"></el-input-number>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row :gutter="20">
                <el-col :span="12">
                    <el-form-item label="类型">
                        <el-input-number v-model="form.type" :min="0" style="width: 100%"></el-input-number>
                    </el-form-item>
                </el-col>
                <el-col :span="12">
                    <el-form-item label="性别">
                        <el-select v-model="form.gender" placeholder="请选择性别" style="width: 100%">
                            <el-option label="未知" :value="0"></el-option>
                            <el-option label="男" :value="1"></el-option>
                            <el-option label="女" :value="2"></el-option>
                        </el-select>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row :gutter="20">
                <el-col :span="24">
                    <el-form-item label="头像URL">
                        <el-input v-model.trim="form.portraitUrl" placeholder="请输入头像URL"></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
        </el-form>
        <div class="action-container">
            <el-button @click="onCancel">取消</el-button>
            <el-button type="primary" @click="onConfirm">确定</el-button>
        </div>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";

export default {
    name: "UpdateEmployee",
    props: {
        employee: {
            type: Object,
            required: true,
        },
        onCancel: {
            type: Function,
            required: true,
        },
        onSuccess: {
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
            form: {
                employeeId: this.employee.employeeId,
                name: this.employee.name,
                title: this.employee.title,
                level: this.employee.level || 0,
                mobile: this.employee.mobile,
                email: this.employee.email,
                ext: this.employee.ext,
                office: this.employee.office,
                city: this.employee.city,
                portraitUrl: this.employee.portraitUrl,
                jobNumber: this.employee.jobNumber,
                joinTime: this.employee.joinTime,
                type: this.employee.type || 0,
                gender: this.employee.gender || 0,
                sort: this.employee.sort || 0,
                organizationId: this.employee.organizationId || 0,
                createDt: this.employee.createDt || 0,
                updateDt: this.employee.updateDt || 0,
            }
        }
    },
    methods: {
        async onConfirm() {
            if (!this.form.name) {
                this.$message.error('姓名不能为空');
                return;
            }
            try {
                await this.orgStore.updateEmployee(this.form);
                this.$message.success('更新成功');
                this.onSuccess();
            } catch (e) {
                console.error('update employee error', e);
                this.$message.error('更新失败');
            }
        }
    }
}
</script>

<style scoped>
.title {
    font-size: 16px;
    color: #1f2329;
    margin-bottom: 20px;
}

.action-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
}
</style>
