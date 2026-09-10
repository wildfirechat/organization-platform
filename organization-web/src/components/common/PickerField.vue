<template>
    <div class="picker" :class="{'is-empty': !items.length}" @click="$emit('open')">
        <div v-if="items.length" class="picker-tags">
            <el-tag v-for="(item, index) in items" :key="index" closable type="info" disable-transitions
                    @close.stop="$emit('remove', item)">
                {{ item[labelKey] }}
            </el-tag>
        </div>
        <span v-else class="picker-placeholder">{{ placeholder }}</span>
        <el-icon class="picker-arrow">
            <ArrowRight/>
        </el-icon>
    </div>
</template>

<script>
export default {
    name: 'PickerField',
    emits: ['open', 'remove'],
    props: {
        items: {
            type: Array,
            default: () => []
        },
        placeholder: {
            type: String,
            default: '点击选择'
        },
        labelKey: {
            type: String,
            default: 'name'
        }
    }
}
</script>

<style scoped>
/* 输入框外观的选择器：旧版是「禁用的 el-input + prepend 标签 + append 按钮」，
   点击区域小且视觉上像是不可用的控件 */
.picker {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    min-height: 32px;
    padding: 3px 8px;
    background: var(--wf-surface);
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);
    cursor: pointer;
    transition: border-color 0.15s ease;
}

.picker:hover {
    border-color: var(--el-border-color-darker);
}

.picker-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    flex: 1;
    min-width: 0;
}

.picker-placeholder {
    flex: 1;
    color: var(--wf-text-4);
}

.picker-arrow {
    flex-shrink: 0;
    color: var(--wf-text-4);
}
</style>
