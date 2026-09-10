<template>
    <div class="tab-page">
        <PageHeader title="成员与部门" description="维护部门树与部门下的直属成员，支持组织官方群的创建与修复">
            <template #extra>
                <el-radio-group v-model="activeIndex">
                    <el-radio-button label="成员" value="1"/>
                    <el-radio-button label="部门" value="2"/>
                </el-radio-group>
            </template>
        </PageHeader>

        <member v-show="activeIndex === '1'" :show-header="false"/>
        <department v-show="activeIndex === '2'" :show-header="false"/>
    </div>
</template>

<script>
import Member from "@/components/page/organization/Member.vue";
import Department from "@/components/page/organization/Department.vue";
import PageHeader from "@/components/common/PageHeader.vue";

export default {
    name: "departmentAndUser",
    components: {PageHeader, Department, Member},
    data() {
        return {
            activeIndex: '1',
        }
    }
}
</script>

<style scoped>
.tab-page {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
}
</style>
