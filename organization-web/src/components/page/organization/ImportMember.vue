<template>
    <div style="width: 100%; padding-top: 20px; display: flex; flex-direction: row; justify-content: center; ">
        <div style="width: 830px;">
            <p style="color: #1f2329; font-size: 16px; font-weight: 500; margin-bottom: 20px">批量导入新成员</p>
            <div style="background: #f8f9fa; padding: 20px">
                <p style="font-size: 14px; color: #1f2329">1. 下载导入模板</p>
                <p style="font-size: 14px; margin: 2px 0 6px; color: #646a73">根据提示信息完善表格内容</p>
                <el-button icon="el-icon-download" @click="downloadTemplate">下载空的模板表格</el-button>
            </div>
            <div style="background: #f8f9fa; margin-top: 20px; padding: 20px">
                <p style="font-size: 14px; color: #1f2329; padding-bottom: 20px">2. 上传完善后的模板</p>
                <el-upload
                    ref="upload"
                    class="uploader"
                    drag
                    :action="uploadUrl"
                    :with-credentials="true"
                    accept=".xls,.xlsx"
                    :show-file-list="true"
                    :limit="1"
                    :on-change="handleChange"
                    :auto-upload="false"
                    :on-success="onUploadSuccess"
                    :on-error="onUploadError"
                    multiple>
                    <i class="el-icon-upload"></i>
                    <div>
                        <el-button>选择文件</el-button>
                    </div>
                    <div class="el-upload__text">下载模板并完善信息后，可直接将文件拖拽到此处进行上传</div>
                </el-upload>
            </div>
            <div style="margin-top: 40px; width: 100%; display: flex; justify-content: flex-end ">
                <el-button @click="onCancel">取消</el-button>
                <el-button type="primary" :disabled="disableSubmitButton" :loading="submitting" @click="submitUpload">导入</el-button>
            </div>
        </div>

        <el-dialog
            title="导入进度"
            :visible.sync="progressVisible"
            :close-on-click-modal="true"
            :close-on-press-escape="true"
            :show-close="true"
            width="500px">
            <div style="padding: 10px 0;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 10px;">
                    <span>状态：{{ statusText }}</span>
                    <span>{{ job.processed }} / {{ job.total }}</span>
                </div>
                <el-progress :percentage="progressPercentage" :status="progressStatus"></el-progress>
                <div style="margin-top: 15px; display: flex; justify-content: space-around;">
                    <div>成功：{{ job.successCount }}</div>
                    <div>失败：{{ job.failCount }}</div>
                </div>
            </div>
        </el-dialog>

        <el-dialog
            title="导入失败明细"
            :visible.sync="failDetailsVisible"
            width="600px">
            <div style="margin-bottom: 15px;">
                导入完成，成功 {{ job.successCount }} 人，失败 {{ job.failCount }} 人。失败人员如下：
            </div>
            <el-table :data="job.failDetails" border stripe height="400">
                <el-table-column type="index" label="序号" width="60"></el-table-column>
                <el-table-column prop="" label="失败信息">
                    <template slot-scope="scope">
                        {{ scope.row }}
                    </template>
                </el-table-column>
            </el-table>
            <div slot="footer">
                <el-button type="primary" @click="closeFailDetails">确定</el-button>
            </div>
        </el-dialog>
    </div>

</template>

<script>
import api from "@/api/api";

export default {
    name: "ImportMember",
    data() {
        return {
            uploadUrl: api.uploadTemplateUrl(),
            disableSubmitButton: true,
            submitting: false,
            progressVisible: false,
            failDetailsVisible: false,
            pollTimer: null,
            job: {
                status: 'PENDING',
                total: 0,
                processed: 0,
                successCount: 0,
                failCount: 0,
                errorMessage: '',
                failDetails: []
            }
        }
    },

    computed: {
        progressPercentage() {
            if (!this.job.total) {
                return 0;
            }
            return Math.min(100, Math.floor(this.job.processed * 100 / this.job.total));
        },
        progressStatus() {
            if (this.job.status === 'FAILED') {
                return 'exception';
            }
            if (this.job.status === 'SUCCESS') {
                return 'success';
            }
            return null;
        },
        statusText() {
            switch (this.job.status) {
                case 'PENDING':
                    return '等待处理';
                case 'PROCESSING':
                    if (this.job.processed === 0) {
                        return '正在解析数据';
                    }
                    if (this.job.total > 0 && this.job.processed < this.job.departmentCount) {
                        return '正在保存部门';
                    }
                    if (this.job.total > 0 && this.job.processed < this.job.total) {
                        return '正在创建员工';
                    }
                    return '正在完善部门信息';
                case 'SUCCESS':
                    return '导入完成';
                case 'FAILED':
                    return '导入失败';
                default:
                    return '未知';
            }
        }
    },

    beforeDestroy() {
        this.stopPolling();
    },

    methods: {
        handleChange(file, fileList) {
            if (fileList.length > 1) {
                fileList.splice(0, 1);
            }
            this.disableSubmitButton = fileList.length === 0;
        },
        downloadTemplate() {
            const a = document.createElement('a')
            let url = api.downloadTemplateUrl();
            a.setAttribute('download', '组织结构导入模板.xlsx')
            a.setAttribute('target', '_blank')
            a.setAttribute('href', url)
            a.click()
        },
        submitUpload() {
            this.submitting = true;
            this.$refs.upload.submit();
        },
        onUploadSuccess(response) {
            this.submitting = false;
            if (response && response.code === 0) {
                const jobId = response.result;
                if (jobId) {
                    this.startPolling(jobId);
                } else {
                    this.$message.error('导入任务创建失败');
                    this.resetUpload();
                }
            } else {
                let msg = response && (response.result || response.message) || '导入失败';
                this.$message.error(msg);
                this.resetUpload();
            }
        },
        onUploadError(err) {
            this.submitting = false;
            let msg = '导入失败，请检查网络或稍后重试';
            if (err && err.message) {
                msg = err.message;
            }
            this.$message.error(msg);
            this.resetUpload();
        },
        resetUpload() {
            this.$refs.upload.clearFiles();
            this.disableSubmitButton = true;
        },
        startPolling(jobId) {
            this.progressVisible = true;
            this.job = {
                status: 'PENDING',
                total: 0,
                processed: 0,
                successCount: 0,
                failCount: 0,
                errorMessage: '',
                failDetails: []
            };
            this.queryJob(jobId);
            this.pollTimer = setInterval(() => {
                this.queryJob(jobId);
            }, 1000);
        },
        stopPolling() {
            if (this.pollTimer) {
                clearInterval(this.pollTimer);
                this.pollTimer = null;
            }
        },
        async queryJob(jobId) {
            try {
                const job = await api.queryImportJob(jobId);
                if (job) {
                    this.job = job;
                    if (this.job.status === 'SUCCESS' || this.job.status === 'FAILED') {
                        this.stopPolling();
                        this.progressVisible = false;
                        if (this.job.status === 'SUCCESS') {
                            if (this.job.failCount > 0 && this.job.failDetails && this.job.failDetails.length > 0) {
                                this.failDetailsVisible = true;
                            } else {
                                this.$message.success(`导入完成，成功 ${this.job.successCount} 人`);
                                this.$router.back();
                                setTimeout(() => {
                                    window.location.reload();
                                }, 100);
                            }
                        } else {
                            this.$message.error(this.job.errorMessage || '导入失败');
                            this.resetUpload();
                        }
                    }
                }
            } catch (e) {
                console.error('query import job failed', e);
            }
        },
        closeFailDetails() {
            this.failDetailsVisible = false;
            this.$router.back();
            setTimeout(() => {
                window.location.reload();
            }, 100);
        },
        onCancel(){
            this.stopPolling();
            this.$router.back();
        }
    }
}
</script>

<style scoped>

.uploader {
    width: 100%;
}

>>> .el-upload {
    width: 100%;
}

>>> .el-upload-dragger {
    width: 100%;
    height: 210px;
}

.el-upload__text {
    color: #d0d3d6;
    font-size: 14px;
    padding-top: 5px;
}

</style>
