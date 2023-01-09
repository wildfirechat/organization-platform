<template>
    <div>
        {{ 'TODO 变更部门, parent' + (targetDepartment && targetDepartment.name) }}

    </div>
</template>

<script>

export default {
    name: "TransferMember",
    props: {
        targetDepartment: {
            default: {},
            required: true,
        }
    }
}
</script>

<style scoped>

</style>