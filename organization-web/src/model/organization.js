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
    hasChildren;
    leaf;
    employees;

    buildRenderData(organizationWithChildren) {
        this.label = this.name;
        this.type = 1;
        let manager = organizationWithChildren ? organizationWithChildren.employees.find(e => e.employeeId === this.managerId) : null
        if (manager) {
            this.managerName = manager.name;
        }
        if (!organizationWithChildren) {
            if (this.hasChildren === true) {
                this.leaf = false;
            } else if (this.hasChildren === false) {
                this.leaf = true;
            }
            return;
        }
        if (!this.children) {
            this.children = [];
        } else {
            this.children.length = 0;
        }
        if (!this.employees) {
            this.employees = [];
        } else {
            this.employees.length = 0;
        }
        if (organizationWithChildren.subOrganizations && organizationWithChildren.subOrganizations.length > 0) {
            this.hasChildren = true;
            this.leaf = false;
            organizationWithChildren.subOrganizations.forEach(subOrg => {
                let org = Object.assign(new Organization(), subOrg);
                org.buildRenderData();

                this.children.push(org);
            })
        } else {
            this.hasChildren = false;
            this.leaf = true;
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