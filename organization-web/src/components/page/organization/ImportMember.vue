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
                    :show-file-list="false"
                    :limit="1"
                    :on-change="handleChange"
                    :auto-upload="false"
                    :on-success="onUploadSuccess"
                    multiple>
                    <i class="el-icon-upload"></i>
                    <div>
                        <el-button>选择文件</el-button>
                    </div>
                    <div class="el-upload__text">下载模板并完善信息后，可直接将文件拖拽到此处进行上传</div>
                    <!--                    <div class="el-upload__tip" slot="tip">只能上传xls/xlsx文件，且不超过500kb</div>-->
                </el-upload>
            </div>
            <div style="margin-top: 40px; width: 100%; display: flex; justify-content: flex-end ">
                <el-button @click="onCancel">取消</el-button>
                <el-button type="primary" :disabled="disableSubmitButton" @click="submitUpload">导入</el-button>
            </div>
        </div>
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
        }
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
            this.$refs.upload.submit();
        },
        onUploadSuccess() {
            this.$router.back();
        },
        onCancel(){
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