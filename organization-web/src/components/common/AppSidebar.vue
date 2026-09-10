<template>
    <aside class="app-sidebar" :class="{collapsed: collapsed}">
        <div class="brand" @click="$emit('brand-click')">
            <img class="brand-logo" src="@/assets/logo.svg" alt=""/>
            <span v-show="!collapsed" class="brand-name">组织架构管理</span>
        </div>

        <el-scrollbar class="nav-scroll">
            <el-menu
                router
                :default-active="activeIndex"
                :collapse="collapsed"
                :collapse-transition="false"
                class="nav-menu">
                <el-menu-item v-for="item in menus" :key="item.index" :index="item.index">
                    <el-icon>
                        <component :is="item.icon"/>
                    </el-icon>
                    <template #title>{{ item.title }}</template>
                </el-menu-item>
            </el-menu>
        </el-scrollbar>

        <div class="nav-foot" v-show="!collapsed">wildfirechat.net</div>
    </aside>
</template>

<script>
export default {
    name: 'AppSidebar',
    emits: ['brand-click'],
    props: {
        collapsed: {
            type: Boolean,
            default: false
        }
    },
    data() {
        return {
            menus: [
                {index: '/organization/departmentanduser', title: '成员与部门', icon: 'OfficeBuilding'},
                {index: '/organization/departmentanduser/import-member', title: '批量导入', icon: 'Upload'},
                {index: '/organization/logs', title: '操作日志', icon: 'Tickets'}
            ]
        }
    },
    computed: {
        // 菜单里没有的子路由（如 /index）不高亮任何一项，而不是错误地高亮第一项
        activeIndex() {
            return this.$route.path;
        }
    }
}
</script>

<style scoped>
.app-sidebar {
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    width: var(--wf-sider-w);
    height: 100%;
    background: var(--wf-nav-bg);
    transition: width 0.2s ease;
}

.app-sidebar.collapsed {
    width: var(--wf-sider-w-collapsed);
}

.brand {
    display: flex;
    align-items: center;
    gap: 10px;
    height: var(--wf-header-h);
    flex-shrink: 0;
    padding: 0 16px;
    overflow: hidden;
    cursor: pointer;
    border-bottom: 1px solid var(--wf-nav-border);
}

.brand-logo {
    width: 26px;
    height: 26px;
    flex-shrink: 0;
    border-radius: var(--wf-radius-sm);
    object-fit: contain;
}

.brand-name {
    font-size: 15px;
    font-weight: 600;
    color: #fff;
    white-space: nowrap;
}

.nav-scroll {
    flex: 1;
    min-height: 0;
}

.nav-menu {
    --el-menu-bg-color: transparent;
    --el-menu-text-color: var(--wf-nav-text);
    --el-menu-hover-bg-color: rgba(255, 255, 255, 0.06);
    --el-menu-active-color: #fff;
    --el-menu-item-height: 44px;
    --el-menu-base-level-padding: 16px;

    border-right: none;
    padding: 8px;
}

.nav-menu :deep(.el-menu-item) {
    border-radius: var(--wf-radius);
    margin-bottom: 2px;
    font-size: 14px;
}

/* 选中项用品牌色实底，图标和文字一起变白 */
.nav-menu :deep(.el-menu-item.is-active) {
    background: var(--wf-brand);
    color: #fff;
    font-weight: 500;
}

.collapsed .nav-menu {
    padding: 8px 6px;
}

.collapsed .nav-menu :deep(.el-menu-item) {
    padding: 0 !important;
    justify-content: center;
}

.collapsed .nav-menu :deep(.el-icon) {
    margin: 0;
}

.nav-foot {
    flex-shrink: 0;
    padding: 12px 16px;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.28);
    border-top: 1px solid var(--wf-nav-border);
    white-space: nowrap;
}
</style>
