<template>
    <el-container style="height: 100%">
        <el-header style="padding: 0">
            <el-menu :default-active="activeIndex" class="el-menu-demo" mode="horizontal" @select="handleSelect">
                <el-menu-item index="1">成员</el-menu-item>
                <el-menu-item index="2">部门</el-menu-item>
            </el-menu>
        </el-header>
        <el-main>
            <member v-show="activeIndex === '1'"/>
            <department v-show="activeIndex === '2'"/>
        </el-main>
    </el-container>

</template>

<script>
import Member from "@/components/page/organization/Member";
import Department from "@/components/page/organization/Department.vue";

export default {
    name: "departmentAndUser",
    components: {Department, Member},
    data() {
        return {
            activeIndex: '1',
        }
    },
    methods: {
        handleSelect(key) {
            this.activeIndex = key;
        }
    }
}
</script>

<style scoped>

</style>