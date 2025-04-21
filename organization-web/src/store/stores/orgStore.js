import { defineStore } from 'pinia';
import Api from '@/api/api';
import OrganizationWithChildren from "@/model/organizationWithChildren";
import Organization from "@/model/organization";

export const useOrgStore = defineStore('org', {
  state: () => ({
    rootOrganizations: []
  }),

  actions: {
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
    },

    async getRootOrganizationsWithChildren() {
      let rootOrgs = await Api.getRootOrganization();
      this.rootOrganizations = [];
      for (let i = 0; i < rootOrgs.length; i++) {
        let rootOrg = rootOrgs[i];
        let org = Object.assign(new Organization(), rootOrg);
        this.rootOrganizations.push(org);
        let tmp = await Api.queryOrganizationWithChildren(rootOrg.id);
        let orgWC = Object.assign(new OrganizationWithChildren(), tmp);
        org._orgWithChildren = orgWC;
        org.buildRenderData(orgWC);
        console.log('query root organizationWithChildren', this.rootOrganizations);
      }
    },

    async queryOrganizationWithChildren(org) {
      let result = await Api.queryOrganizationWithChildren(org.id);
      let orgWC = Object.assign(new OrganizationWithChildren(), result);
      org._orgWithChildren = orgWC;
      org.buildRenderData(orgWC);
      console.log('queryOrganizationWithChildren', org.id);
    },

    async createEmployee({employee, targetOrg}) {
      employee.organizationId = targetOrg.id;
      await Api.createEmployee(employee);
      this.queryOrganizationWithChildren(targetOrg);
    },

      async transferEmployee(employeeId, targetOrgIds) {
          await Api.moveEmployee({
              employeeId: employeeId,
              organizations: targetOrgIds,
          });
          this.getRootOrganizationsWithChildren()
      },

    async createOrganization({parentOrganization, organization, createGroup}) {
      organization.parentId = parentOrganization.id;
      let result = await Api.createOrganization(organization);
      if (createGroup) {
        await Api.createOrganizationGroup(result.organizationId);
      }
    },

    async updateOrganization(organization) {
      await Api.updateOrganization(organization);
    },

    async removeOrganization({organization, dismissGroup}) {
      await Api.deleteOrganization(organization);
      if (dismissGroup && organization.groupId) {
        try {
          await Api.dismissOrganizationGroup(organization);
        } catch (e) {
          console.error('dismissGroup error', e);
        }
      }
    },

    async queryEmployee({employeeId}) {
      await Api.queryEmployee(employeeId);
    },

    async deleteEmployee({employeeId, destroyIMUser}) {
      await Api.deleteEmployee(employeeId, destroyIMUser);
    }
  }
});