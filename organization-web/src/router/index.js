import Vue from 'vue';
import Router from 'vue-router';

Vue.use(Router);

export default new Router({
    routes: [
        {
            path: '/',
            redirect: '/login'
        },
        {
            path: '/login',
            component: resolve => require(['../components/page/Login.vue'], resolve)
        },
        {
            path: '/',
            component: resolve => require(['../components/common/Home.vue'], resolve),
            meta: {title: '组织架构'},
            children: [
                // {
                //     path: '/index',
                //     component: resolve => require(['../components/page/Index.vue'], resolve),
                //     meta: {title: '野火组织架构管理系统'}
                // },
                {
                    path: '/index',
                    component: resolve => require(['../components/page/organization/DepartmentAndMember.vue'], resolve),
                    meta: {title: '成员与部门'}
                },
                {
                    path: '/organization/departmentanduser',
                    component: resolve => require(['../components/page/organization/Member.vue'], resolve),
                    meta: {title: '成员与部门'},
                    // children: [
                    //     {
                    //         path: '/organization/departmentanduser/import-member',
                    //         component: resolve => require(['../components/page/organization/ImportMember.vue'], resolve),
                    //         meta: {title: '批量导入成员'}
                    //     },
                    // ]
                },
                {
                    path: '/organization/departmentanduser/import-member',
                    component: resolve => require(['../components/page/organization/ImportMember.vue'], resolve),
                    meta: {title: '批量导入成员'}
                },
                {
                    path: '/updatePwd',
                    component: resolve => require(['../components/page/UpdatePwd.vue'], resolve),
                    meta: {title: '更新密码'}
                },
            ]
        },
        {
            path: '/403',
            component: resolve => require(['../components/page/403.vue'], resolve)
        },
        {
            path: '/404',
            component: resolve => require(['../components/page/404.vue'], resolve)
        },
        {
            path: '*',
            redirect: '/404'
        }
    ]
})
