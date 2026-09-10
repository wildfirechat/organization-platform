<template>
    <div class="import-page">
        <PageHeader title="批量导入成员" description="用模板表格一次性导入部门与成员，适合首次搭建组织架构"/>

        <div class="import-body">
            <section class="step">
                <div class="step-index">1</div>
                <div class="step-content">
                    <p class="step-title">下载导入模板</p>
                    <p class="step-desc">按模板中的提示信息填写部门与成员，注意不要改动表头</p>
                    <el-button :icon="Download" @click="downloadTemplate">下载空的模板表格</el-button>
                </div>
            </section>

            <section class="step">
                <div class="step-index">2</div>
                <div class="step-content">
                    <p class="step-title">上传完善后的模板</p>
                    <p class="step-desc">支持 .xls / .xlsx，单次仅能上传一个文件</p>
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
                        :on-error="onUploadError">
                        <el-icon class="el-icon--upload">
                            <UploadFilled/>
                        </el-icon>
                        <div class="el-upload__text">将文件拖到此处，或<em>点击选择文件</em></div>
                        <template #tip>
                            <div class="upload-tip">请先下载模板并完善信息后再上传</div>
                        </template>
                    </el-upload>
                </div>
            </section>

            <div class="import-actions">
                <el-button @click="onCancel">取消</el-button>
                <el-button type="primary" :disabled="disableSubmitButton" :loading="submitting" @click="submitUpload">
                    开始导入
                </el-button>
            </div>
        </div>

        <el-dialog
            title="导入进度"
            v-model="progressVisible"
            :close-on-click-modal="true"
            :close-on-press-escape="true"
            :show-close="true"
            width="460px">
            <div class="progress-head">
                <span>状态：{{ statusText }}</span>
                <span class="wf-num wf-muted">{{ job.processed }} / {{ job.total }}</span>
            </div>
            <el-progress :percentage="progressPercentage" :status="progressStatus" :stroke-width="10"/>
            <div class="progress-stat">
                <div class="stat">
                    <span class="stat-label">成功</span>
                    <span class="stat-value wf-num success">{{ job.successCount }}</span>
                </div>
                <div class="stat">
                    <span class="stat-label">失败</span>
                    <span class="stat-value wf-num danger">{{ job.failCount }}</span>
                </div>
            </div>
        </el-dialog>

        <el-dialog
            title="导入失败明细"
            v-model="failDetailsVisible"
            width="600px">
            <el-alert type="warning" :closable="false" show-icon
                      :title="`导入完成，成功 ${job.successCount} 人，失败 ${job.failCount} 人`"
                      description="以下人员未能导入，请修正后重新上传"/>
            <el-table :data="job.failDetails" height="360" class="fail-table" empty-text="暂无失败明细">
                <el-table-column type="index" label="序号" width="70" align="center"/>
                <el-table-column label="失败信息">
                    <template v-slot="scope">
                        {{ scope.row }}
                    </template>
                </el-table-column>
            </el-table>
            <template #footer>
                <div class="wf-actions">
                    <el-button type="primary" @click="closeFailDetails">知道了</el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script>
import api from "@/api/api";
import {Download} from '@element-plus/icons-vue';
import PageHeader from "@/components/common/PageHeader.vue";

export default {
    name: "ImportMember",
    components: {PageHeader},
    data() {
        return {
            Download,
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
            return '';
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

    beforeUnmount() {
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
.import-page {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
}

.import-body {
    width: 100%;
    max-width: 760px;
}

/* 步骤卡片：序号 + 说明 + 操作，取代旧版的灰底 div 堆叠 */
.step {
    display: flex;
    gap: 14px;
    padding: 20px;
    margin-bottom: 16px;
    background: var(--wf-surface);
    border-radius: var(--wf-radius-lg);
    box-shadow: var(--wf-shadow-sm);
}

.step-index {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    flex-shrink: 0;
    border-radius: 50%;
    background: var(--wf-brand-bg);
    color: var(--wf-brand);
    font-size: 13px;
    font-weight: 600;
}

.step-content {
    flex: 1;
    min-width: 0;
}

.step-title {
    font-size: 15px;
    font-weight: 600;
    line-height: 24px;
    color: var(--wf-text-1);
}

.step-desc {
    margin: 4px 0 14px;
    font-size: 13px;
    color: var(--wf-text-3);
}

.uploader {
    width: 100%;
}

:deep(.el-upload) {
    width: 100%;
}

:deep(.el-upload-dragger) {
    width: 100%;
    padding: 36px 20px;
}

:deep(.el-upload__text) {
    color: var(--wf-text-2);
    font-size: 14px;
}

.upload-tip {
    margin-top: 8px;
    font-size: 12px;
    color: var(--wf-text-3);
}

.import-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
}

/* ---------- 导入进度 ---------- */
.progress-head {
    display: flex;
    justify-content: space-between;
    margin-bottom: 12px;
    font-size: 13px;
    color: var(--wf-text-2);
}

.progress-stat {
    display: flex;
    gap: 12px;
    margin-top: 20px;
}

.stat {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    flex: 1;
    padding: 12px 0;
    border-radius: var(--wf-radius);
    background: var(--wf-fill);
}

.stat-label {
    font-size: 12px;
    color: var(--wf-text-3);
}

.stat-value {
    font-size: 20px;
    font-weight: 600;
}

.stat-value.success {
    color: var(--wf-success);
}

.stat-value.danger {
    color: var(--wf-danger);
}

.fail-table {
    margin-top: 16px;
}
</style>
