import axios from './axios.config'
import instance from "./axios.config";

export default {
    async login(params) {
        return axios.post('/login', params)
    },
    async getAccount() {
        return axios.get('/account')
    },
    async getRootOrganization() {
        return axios.post('/organization/root')
    },
    async queryOrganizationWithChildren(orgId) {
        return axios.post('/organization/query_ex', {id: orgId})
    },
    async createEmployee(employee) {
        return axios.post('/employee/create', employee);
    },
    /**
     * 员工变更部门
     * @param employee
     * @return {Promise<axios.AxiosResponse<any>>}
     */
    async moveEmployee(moveEmployeePojo) {
        return axios.post('/employee/move', moveEmployeePojo);
    },
    async queryEmployee(employeeId) {
        return axios.post('/employee/query', {employeeId});
    },
    async deleteEmployee(employeeId, destroyIMUser = true) {
        return axios.post('/employee/delete', {employeeId, destroyIMUser});
    },
    async createOrganization(organization) {
        return axios.post('/organization/create', organization);
    },
    async updateOrganization(organizaiton) {
        return axios.post('/organization/update', organizaiton);
    },
    async setOrganizationManager(id, managerId) {
        return axios.post('/organization/set_manager', {id, managerId});
    },
    async deleteOrganization(organization) {
        return axios.post('/organization/delete', {id: organization.id})
    },
    async createOrganizationGroup(orgId, groupId) {
        return axios.post('/organization/create_group', {id: orgId, groupId: groupId});
    },
    async dismissOrganizationGroup(organization) {
        return axios.post('/organization/dismiss_group', {id: organization.id})
    },
    async repairOrganizationGroup(orgId) {
        return axios.post('/organization/repair_group', {id: orgId})
    },
    async udpatePwd(params) {
        return axios.post('/update_pwd', params)
    },
    async updateEmployee(employee) {
        return axios.post('/employee/update', employee);
    },
    async updateEmployeePassword(employeeId, password) {
        return axios.post('/employee/update_password', {employeeId, password});
    },
    async searchEmployee(organizationId, keyword = '', page = 0, count = 1000, root = false) {
        return axios.post('/employee/search', {organizationId, keyword, page, count, root});
    },

    downloadTemplateUrl() {
        return instance.defaults.baseURL + '/template';
    },

    uploadTemplateUrl() {
        return instance.defaults.baseURL + '/import';
    }
}
