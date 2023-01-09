<template>
    <div>
        <p class="title">添加成员</p>
        <el-form :inline="true"
                 label-position="right"
                 label-width="100px"
                 :model="employee"
                 size="medium"
                 class="demo-form-inline">
            <el-form-item label="姓名">
                <el-input v-model.trim="employee.name" placeholder="姓名"></el-input>
            </el-form-item>
            <el-form-item label="部门">
                <el-input disabled>
                    <div v-if="checkedDepartments && checkedDepartments.length" slot="prepend">
                        <el-tag
                            v-for="(depart, index) in checkedDepartments"
                            :key="index"
                            closable
                            @close="handleCloseTag(depart)"
                            type="info">
                            {{ depart && depart.name }}
                        </el-tag>
                    </div>
                    <el-button slot="append" type="text" icon="el-icon-edit" @click="onChooseDepartment"></el-button>
                </el-input>
            </el-form-item>
            <el-form-item label="手机号码">
                <el-input v-model="employee.mobile" placeholder="手机号码"></el-input>
            </el-form-item>
            <el-form-item label="邮箱">
                <el-input v-model="employee.email" placeholder="邮箱"></el-input>
            </el-form-item>
            <el-form-item label="职务">
                <el-input v-model="employee.title" placeholder="职务"></el-input>
            </el-form-item>
            <el-form-item label="头像">
                <el-input v-model="employee.portraitUrl" placeholder="头像链接地址">
                    <el-upload
                        slot="append"
                        class="avatar-uploader"
                        action="https://jsonplaceholder.typicode.com/posts/"
                        :show-file-list="false"
                        :on-success="handleAvatarSuccess"
                        :before-upload="beforeAvatarUpload">
                        <img v-if="employee.portraitUrl" :src="employee.portraitUrl" class="avatar">
                        <i v-else class="el-icon-plus avatar-uploader-icon"></i>
                    </el-upload>
                </el-input>
            </el-form-item>
        </el-form>
        <div class="action-container">
            <el-button @click="onCancel">取消</el-button>
            <el-button type="primary" :disabled="!confirmButtonEnable" @click="onConfirm">确定</el-button>
        </div>
    </div>
</template>

<script>

export default {
    name: "AddDepartmentMember",
    props: {
        checkedDepartments: {
            type: Array,
            required: true,
        },
        onCancel: {
            type: Function,
            required: true,
        },
        onChooseDepartment: {
            type: Function,
            required: true,
        },
        onUncheckDepartment: {
            type: Function,
            required: true,
        }
    },
    data() {
        return {
            employee: {},
        }
    },
    computed: {
        confirmButtonEnable() {
            return this.employee.name
                && this.employee.mobile
                && this.employee.email
                && this.employee.title
                && this.checkedDepartments.length > 0;
        }
    },
    methods: {
        handleAvatarSuccess(res, file) {
            this.employee.portraitUrl = URL.createObjectURL(file.raw);
        },
        beforeAvatarUpload(file) {
            const isJPG = file.type === 'image/jpeg';
            const isLt2M = file.size / 1024 / 1024 < 2;

            if (!isJPG) {
                this.$message.error('上传头像图片只能是 JPG 格式!');
            }
            if (!isLt2M) {
                this.$message.error('上传头像图片大小不能超过 2MB!');
            }
            return isJPG && isLt2M;
        },
        handleCloseTag(tag) {
            this.onUncheckDepartment(tag);
        },
        onConfirm() {
            this.checkedDepartments.forEach(department => {
                this.$store.dispatch('createMember', {
                    employee: this.employee,
                    targetOrg: department,
                })
                    .then(res => {
                        console.log('create member success', res)
                    })
                    .catch(err => {
                        console.log('create member error', err)
                    })
            })
            this.onCancel();
        }
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

>>> .el-form-item__content {
    width: 300px;
}

.action-container {
    display: flex;
    justify-content: flex-end;
    margin-right: 10px;
}

.avatar {
    height: 25px;
}


</style>