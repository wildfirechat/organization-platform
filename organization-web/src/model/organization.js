import Employee from "@/model/employee";

export default class Organization {
    id;
    parentId;
    managerId;
    name;
    description;
    portraitUrl;
    tel;
    office;
    groupId;
    memberCount;
    sort;
    updateDt;
    createDt;

    // for ui
    label;
    type;
    managerName;
    // subDepartments
    children;
    hasChildren = true;
    employees;

    buildRenderData(organizationWithChildren) {
        this.label = this.name;
        this.type = 1;
        let manager = organizationWithChildren ? organizationWithChildren.employees.find(e => e.employeeId === this.managerId) : null
        this.managerName = manager ? manager.name : null;
        this.children = [];
        this.employees = [];
        if (!organizationWithChildren) {
            return;
        } else {
            this.children.length = 0;
        }
        if (organizationWithChildren.subOrganizations && organizationWithChildren.subOrganizations.length > 0) {
            organizationWithChildren.subOrganizations.forEach(subOrg => {
                let org = Object.assign(new Organization(), subOrg);
                org.buildRenderData();

                this.children.push(org);
            })
        }
        if (organizationWithChildren.employees && organizationWithChildren.employees.length > 0) {
            organizationWithChildren.employees.forEach(subE => {
                let e = Object.assign(new Employee(), subE);
                e.buildRenderData();
                this.employees.push(e)
            })
        }
    }
}