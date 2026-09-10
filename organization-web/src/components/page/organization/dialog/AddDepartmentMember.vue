<template>
    <div>
        <el-form label-position="right" label-width="90px" :model="employee">
            <el-row :gutter="20">
                <el-col :span="12">
                    <el-form-item label="姓名" required>
                        <el-input v-model.trim="employee.name" placeholder="请输入姓名"/>
                    </el-form-item>
                </el-col>
                <el-col :span="12">
                    <el-form-item label="手机号码" required>
                        <el-input v-model="employee.mobile" placeholder="请输入手机号码"/>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row :gutter="20">
                <el-col :span="12">
                    <el-form-item label="邮箱">
                        <el-input v-model="employee.email" placeholder="请输入邮箱"/>
                    </el-form-item>
                </el-col>
                <el-col :span="12">
                    <el-form-item label="职务">
                        <el-input v-model="employee.title" placeholder="请输入职务"/>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-form-item label="部门" required>
                <PickerField :items="checkedDepartments" placeholder="点击选择所属部门（可多选）"
                             @open="showChooseDepartmentDialog = true" @remove="handleCloseTag"/>
            </el-form-item>
            <el-form-item label="密码">
                <el-input v-model="employee.password" type="password" placeholder="请输入密码" show-password/>
            </el-form-item>
            <el-form-item label="头像">
                <div class="avatar-field">
                    <el-upload
                        class="avatar-uploader"
                        :action="uploadMediaUrl"
                        :with-credentials="true"
                        :show-file-list="false"
                        :on-success="handleAvatarSuccess"
                        :before-upload="beforeAvatarUpload">
                        <img v-if="employee.portraitUrl" :src="employee.portraitUrl" class="avatar" alt=""/>
                        <el-icon v-else class="avatar-uploader-icon">
                            <Plus/>
                        </el-icon>
                    </el-upload>
                    <div class="avatar-side">
                        <el-input v-model="employee.portraitUrl" placeholder="也可以直接填写头像链接地址"/>
                        <p class="field-tip">上传需要先配置 oss，仅支持 jpg / png，且不超过 2MB</p>
                    </div>
                </div>
            </el-form-item>
        </el-form>

        <div class="wf-dialog-actions">
            <el-button @click="onCancel">取消</el-button>
            <el-button type="primary" :loading="submitting" :disabled="!confirmButtonEnable" @click="onConfirm">确定
            </el-button>
        </div>

        <el-dialog v-model="showChooseDepartmentDialog" append-to-body title="选择部门" width="760px">
            <ChooseDepartment
                :on-cancel="() => showChooseDepartmentDialog = false"
                :on-confirm="onCheckDepartment"/>
        </el-dialog>
    </div>
</template>

<script>
import {useOrgStore} from "@/store/stores/orgStore";
import ChooseDepartment from "@/components/page/organization/dialog/ChooseDepartment.vue";
import PickerField from "@/components/common/PickerField.vue";

export default {
    name: "AddDepartmentMember",
    components: {PickerField, ChooseDepartment},
    props: {
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
            employee: {},
            checkedDepartments: [],
            showChooseDepartmentDialog: false,
            submitting: false,
            uploadMediaUrl: '/api/media/upload/'
        }
    },
    computed: {
        confirmButtonEnable() {
            return this.employee.name
                && this.employee.mobile
                && this.checkedDepartments.length > 0;
        }
    },
    methods: {
        handleAvatarSuccess(res, file) {
            if (res.code === 0) {
                this.employee.portraitUrl = res.result.url;
            } else {
                this.$message.error('头像上传失败 ' + res);
            }
            console.log('res, file', res, file)
        },
        beforeAvatarUpload(file) {
            const isJPG = file.type === 'image/jpeg';
            const isPNG = file.type === 'image/png';
            const isLt2M = file.size / 1024 / 1024 < 2;

            if (!isJPG && !isPNG) {
                this.$message.error('上传头像图片只能是 JPG/PNG 格式!');
            }
            if (!isLt2M) {
                this.$message.error('上传头像图片大小不能超过 2MB!');
            }
            return (isJPG || isPNG) && isLt2M;
        },
        handleCloseTag(tag) {
            this.checkedDepartments = this.checkedDepartments.filter(d => d.id !== tag.id);
        },
        onCheckDepartment(departments) {
            this.checkedDepartments = departments;
            this.showChooseDepartmentDialog = false;
        },
        async onConfirm() {
            if (this.checkedDepartments.length === 0) {
                this.$message.error('请选择至少一个部门');
                return;
            }

            this.submitting = true;
            try {
                // 等所有部门都创建完成后再通知外层刷新列表；
                // 每个部门用独立的副本，避免并发时共享的 organizationId 被互相覆盖
                await Promise.all(this.checkedDepartments.map(department =>
                    this.orgStore.createEmployee({
                        employee: {...this.employee},
                        targetOrg: department,
                    })
                ));
                this.$message.success('添加成员成功');
                this.onSuccess();
            } catch (err) {
                console.log('create employee error', err)
            } finally {
                this.submitting = false;
            }
        }
    }
}
</script>

<style scoped>
.avatar-field {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    width: 100%;
}

.avatar-uploader :deep(.el-upload) {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 64px;
    height: 64px;
    overflow: hidden;
    border: 1px dashed var(--el-border-color);
    border-radius: var(--wf-radius);
    background: var(--wf-fill);
    cursor: pointer;
    transition: border-color 0.15s ease;
}

.avatar-uploader :deep(.el-upload:hover) {
    border-color: var(--wf-brand);
}

.avatar-uploader-icon {
    font-size: 20px;
    color: var(--wf-text-4);
}

.avatar {
    width: 64px;
    height: 64px;
    object-fit: cover;
}

.avatar-side {
    flex: 1;
    min-width: 0;
}

.field-tip {
    margin-top: 6px;
    font-size: 12px;
    line-height: 18px;
    color: var(--wf-text-3);
}
</style>
