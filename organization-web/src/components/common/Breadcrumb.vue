<template>
    <el-breadcrumb class="breadcrumb" separator="/">
        <el-breadcrumb-item v-for="(item, index) in breadcrumbData" :key="item.path">
            <!-- 最后一项是当前页，不可点击 -->
            <span v-if="index === breadcrumbData.length - 1" class="current">{{ item.meta.title }}</span>
            <a v-else class="redirect" @click.prevent="onLinkClick(item)">{{ item.meta.title }}</a>
        </el-breadcrumb-item>
    </el-breadcrumb>
</template>

<script>
export default {
    name: 'Breadcrumb',
    data() {
        return {
            breadcrumbData: []
        }
    },
    watch: {
        $route: {
            handler() {
                this.breadcrumbData = this.$route.matched.filter(item => item.meta && item.meta.title);
            },
            immediate: true
        }
    },
    methods: {
        onLinkClick(item) {
            this.$router.push(item.path ? item.path : '/');
        }
    }
}
</script>

<style scoped>
.breadcrumb {
    display: inline-flex;
    align-items: center;
    font-size: 13px;
    line-height: 1;
}

.breadcrumb .current {
    color: var(--wf-text-1);
    font-weight: 500;
    cursor: text;
}

.breadcrumb .redirect {
    color: var(--wf-text-3);
    font-weight: 400;
}

.breadcrumb .redirect:hover {
    color: var(--wf-brand);
}
</style>
