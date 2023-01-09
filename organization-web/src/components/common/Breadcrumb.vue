<template>
    <el-breadcrumb class="breadcrumb" separator=">">
        <transition-group name="breadcrumb">
            <el-breadcrumb-item
                v-for="(item, index) in breadcrumbData"
                :key="item.path"
            >
                <!-- 不可点击项 -->
                <span v-if="index === breadcrumbData.length - 1" class="no-redirect">{{
                        item.meta.title
                    }}</span>
                <!-- 可点击项 -->
                <a v-else class="redirect" @click.prevent="onLinkClick(item)">{{
                        item.meta.title
                    }}</a>
            </el-breadcrumb-item>
        </transition-group>
    </el-breadcrumb>
</template>

<script>
export default {
    data() {
        return {
            breadcrumbData: [],
        }
    },
    mounted() {

    },
    watch: {
        $route: {
            handler() {
                this.getBreadcrumbData();
            },
            immediate: true,
        }
    },
    methods: {
        getBreadcrumbData() {
            this.breadcrumbData = this.$route.matched.filter(
                item => {
                    return item.meta && item.meta.title;
                }
            )
            console.log('getBreadcrumbData', this.breadcrumbData);
        },

        onLinkClick(item) {
            console.log(item)
            this.$router.push(item.path ? item.path : '/')
        }
    }
}
</script>

<style lang="css" scoped>
.breadcrumb {
    display: inline-block;
    font-size: 14px;
    line-height: 60px;
}

.breadcrumb .no-redirect {
    color: #97a8be;
    cursor: text;
}

.breadcrumb .redirect {
    color: #666;
    font-weight: 600;
}
</style>