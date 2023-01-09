<template>
    <div>
        {{ 'TODO 编辑部门, org ' + (organization && organization.name) }}

    </div>
</template>

<script>

export default {
    name: "TransferMember",
    props: {
        organization: {
            default: {},
            required: true,
        }
    }
}
</script>

<style scoped>

</style>