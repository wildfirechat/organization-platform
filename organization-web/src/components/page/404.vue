<template>
    <div class="error-page">
        <div class="error-code">404</div>
        <p class="error-title">页面走丢了</p>
        <p class="error-desc">你所访问的页面不存在，可以返回首页继续操作</p>
        <div class="error-handle">
            <el-button @click="goBack">返回上一页</el-button>
            <router-link to="/">
                <el-button type="primary">返回首页</el-button>
            </router-link>
        </div>
    </div>
</template>

<script>
export default {
    name: 'Error404',
    methods: {
        goBack() {
            this.$router.go(-1);
        }
    }
}
</script>

<style scoped>
.error-page {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    width: 100%;
    height: 100%;
    padding: 24px;
    background: var(--wf-bg);
}

.error-code {
    font-size: 120px;
    font-weight: 700;
    line-height: 1;
    letter-spacing: 4px;
    color: #165dff;
}

.error-title {
    margin-top: 24px;
    font-size: 20px;
    font-weight: 600;
    color: var(--wf-text-1);
}

.error-desc {
    margin-top: 8px;
    font-size: 14px;
    color: var(--wf-text-3);
}

.error-handle {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 28px;
}
</style>
