export default class Employee {
    employeeId;
    organizationId;
    name;
    title;
    level;
    mobile;
    email;
    ext;
    office;
    city;
    portraitUrl;
    jobNumber;
    joinTime;
    type;
    gender;
    sort;
    createDt;
    updateDt;

    // for ui
    label;

    buildRenderData() {
        this.label = this.name;
    }
}