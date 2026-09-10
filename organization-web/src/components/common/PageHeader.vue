<template>
    <div class="page-header">
        <div class="page-header-text">
            <h1 class="page-title">{{ title }}</h1>
            <p v-if="description" class="page-desc">{{ description }}</p>
        </div>
        <div v-if="$slots.extra" class="page-header-extra">
            <slot name="extra"/>
        </div>
    </div>
</template>

<script>
export default {
    name: 'PageHeader',
    props: {
        title: {
            type: String,
            required: true
        },
        description: {
            type: String,
            default: ''
        }
    }
}
</script>

<style scoped>
.page-header {
    display: flex;
    align-items: flex-start;
    gap: 16px;
    margin-bottom: 16px;
}

.page-header-text {
    flex: 1;
    min-width: 0;
}

.page-title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    line-height: 28px;
    color: var(--wf-text-1);
}

.page-desc {
    margin: 4px 0 0;
    font-size: 13px;
    line-height: 20px;
    color: var(--wf-text-3);
}

.page-header-extra {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}
</style>
