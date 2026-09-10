<template>
    <header class="app-header">
        <el-button text class="collapse-btn" @click="$emit('toggle-collapse')">
            <el-icon :size="18">
                <Fold v-if="!collapsed"/>
                <Expand v-else/>
            </el-icon>
        </el-button>

        <Breadcrumb class="crumb"/>

        <span class="spacer"></span>

        <el-dropdown trigger="click" @command="onCommand">
            <span class="header-action">
                <el-avatar :size="28" class="avatar" :src="account && account.portraitUrl">
                    {{ initials }}
                </el-avatar>
                <span class="user-name">{{ displayName }}</span>
                <el-icon :size="12">
                    <ArrowDown/>
                </el-icon>
            </span>
            <template #dropdown>
                <el-dropdown-menu>
                    <el-dropdown-item command="pwd" :icon="Lock">修改密码</el-dropdown-item>
                    <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
            </template>
        </el-dropdown>
    </header>
</template>

<script>
import {ArrowDown, Expand, Fold, Lock, SwitchButton} from '@element-plus/icons-vue';
import Breadcrumb from '@/components/common/Breadcrumb.vue';

export default {
    name: 'AppHeader',
    components: {Breadcrumb},
    emits: ['toggle-collapse', 'update-pwd', 'logout'],
    props: {
        collapsed: {
            type: Boolean,
            default: false
        },
        account: {
            type: Object,
            default: null
        }
    },
    data() {
        // 图标以组件形式传给 el-dropdown-item 的 icon 属性，不能走模板里的全局注册
        return {ArrowDown, Expand, Fold, Lock, SwitchButton};
    },
    computed: {
        displayName() {
            return (this.account && (this.account.displayName || this.account.name)) || '—';
        },
        initials() {
            return this.displayName === '—' ? '' : this.displayName.slice(0, 1).toUpperCase();
        }
    },
    methods: {
        onCommand(command) {
            this.$emit(command === 'logout' ? 'logout' : 'update-pwd');
        }
    }
}
</script>

<style scoped>
.app-header {
    display: flex;
    align-items: center;
    gap: 4px;
    height: var(--wf-header-h);
    flex-shrink: 0;
    padding: 0 16px 0 8px;
    background: var(--wf-surface);
    border-bottom: 1px solid var(--wf-border);
}

.collapse-btn {
    width: 32px;
    height: 32px;
    padding: 0;
    color: var(--wf-text-2);
}

.crumb {
    margin-left: 8px;
}

.spacer {
    flex: 1;
}

/* 统一的顶栏交互元素，避免各处用 margin 手工凑垂直居中 */
.header-action {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 34px;
    padding: 0 10px;
    border-radius: var(--wf-radius);
    font-size: 13px;
    color: var(--wf-text-2);
    cursor: pointer;
    outline: none;
    transition: background 0.15s ease;
}

.header-action:hover {
    background: var(--wf-fill);
}

.avatar {
    background: var(--wf-brand);
    color: #fff;
    font-size: 13px;
}

.user-name {
    max-width: 140px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--wf-text-1);
}
</style>
