import {createRouter, createWebHashHistory} from 'vue-router';

const routes = [
    {
        path: '/',
        redirect: '/login'
    },
    {
        path: '/login',
        component: () => import('../components/page/Login.vue')
    },
    {
        path: '/',
        component: () => import('../components/common/Home.vue'),
        meta: {title: '组织架构'},
        children: [
            {
                path: '/index',
                component: () => import('../components/page/organization/DepartmentAndMember.vue'),
                meta: {title: '成员与部门'}
            },
            {
                path: '/organization/departmentanduser',
                component: () => import('../components/page/organization/Member.vue'),
                meta: {title: '成员与部门'},
            },
            {
                path: '/organization/departmentanduser/import-member',
                component: () => import('../components/page/organization/ImportMember.vue'),
                meta: {title: '批量导入成员'}
            },
            {
                path: '/organization/logs',
                component: () => import('../components/page/organization/OperationLog.vue'),
                meta: {title: '操作日志'}
            },
            {
                path: '/updatePwd',
                component: () => import('../components/page/UpdatePwd.vue'),
                meta: {title: '更新密码'}
            },
        ]
    },
    {
        path: '/403',
        component: () => import('../components/page/403.vue')
    },
    {
        path: '/404',
        component: () => import('../components/page/404.vue')
    },
    {
        path: '/:pathMatch(.*)*',
        redirect: '/404'
    }
];

export default createRouter({
    // vue-router 3 默认是 hash 模式，这里保持一致
    history: createWebHashHistory(),
    routes
});
