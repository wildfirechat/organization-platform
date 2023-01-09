<template>
    <div>
        {{ 'TODO 成员信息, member' + (employee && employee.name) }}

    </div>
</template>

<script>

export default {
    name: "TransferMember",
    props: {
        employee: {
            default: {},
            required: true,
        }
    }
}
</script>

<style scoped>

</style>