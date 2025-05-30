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
    async deleteOrganization(organization) {
        return axios.post('/organization/delete', {id: organization.id})
    },
    async createOrganizationGroup(orgId, groupId) {
        return axios.post('/organization/create_group', {id: orgId, groupId: groupId});
    },
    async dismissOrganizationGroup(organization) {
        return axios.post('/organization/dismiss_group', {id: organization.id})
    },
    async udpatePwd(params) {
        return axios.post('/update_pwd', params)
    },

    downloadTemplateUrl() {
        return instance.defaults.baseURL + '/template';
    },

    uploadTemplateUrl() {
        return instance.defaults.baseURL + '/import';
    }
}
