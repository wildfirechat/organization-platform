import Api from '@/api/api'
import OrganizationWithChildren from "@/model/organizationWithChildren";
import Organization from "@/model/organization";

export default {
    state: {
        rootOrganizations: [],
        _findOrganization(org, orgId) {
            if (org.id === orgId) {
                return org;
            } else if (org.children && org.children.length) {
                for (let i = 0; i < org.children.length; i++) {
                    let child = org.children[i];
                    let result = this._findOrganization(child, orgId);
                    if (result) {
                        return result;
                    }
                }
            } else {
                return null;
            }
        }
    },

    mutations: {},
    actions: {
        async getRootOrganizationsWithChildren({state}) {
            let rootOrgs = await Api.getRootOrganization()
            state.rootOrganizations = [];
            for (let i = 0; i < rootOrgs.length; i++) {
                let rootOrg = rootOrgs[i];
                let org = Object.assign(new Organization(), rootOrg);
                state.rootOrganizations.push(org);
                let tmp = await Api.queryOrganizationWithChildren(rootOrg.id);
                let orgWC = Object.assign(new OrganizationWithChildren(), tmp);
                org._orgWithChildren = orgWC;
                org.buildRenderData(orgWC);
                console.log('query root organizationWithChildren', state.rootOrganizations);
            }
        },

        async queryOrganizationWithChildren({state}, org) {
            let result = await Api.queryOrganizationWithChildren(org.id);
            let orgWC = Object.assign(new OrganizationWithChildren(), result);
            org._orgWithChildren = orgWC;
            org.buildRenderData(orgWC);
            console.log('queryOrganizationWithChildren', org.id, state)
        },

        async createEmployee({dispatch}, {employee, targetOrg}) {
            employee.organizationId = targetOrg.id;
            await Api.createEmployee(employee);
            dispatch('queryOrganizationWithChildren', targetOrg)
        },

        async createOrganization({dispatch}, {parentOrganization, organization, createGroup}) {
            organization.parentId = parentOrganization.id;
            let result = await Api.createOrganization(organization);
            // dispatch('queryOrganizationWithChildren', parentOrganization);
            if (createGroup) {
                await Api.createOrganizationGroup(result.organizationId);
            }
        },

        async updateOrganization({dispatch}, organization) {
            await Api.updateOrganization(organization);
        },

        async removeOrganization({state}, {organization, dismissGroup}) {
            await Api.deleteOrganization(organization);
            // let parent = state._findOrganization(state.rootOrganizations[0], organization.parentId)
            // dispatch('queryOrganizationWithChildren', parent);
            if (dismissGroup && organization.groupId) {
                try {
                    await Api.dismissOrganizationGroup(organization);
                } catch (e) {
                    console.error('dismissGroup error', e);
                }
            }
        },

        async queryEmployee({state}, {employeeId}) {
            await Api.queryEmployee(employeeId);
            // TODO 更新当前部门？
        },

        async deleteEmployee({state}, {employeeId, destroyIMUser}) {
            await Api.deleteEmployee(employeeId, destroyIMUser);
            // TODO 更新当前部门？
        }

    }
}