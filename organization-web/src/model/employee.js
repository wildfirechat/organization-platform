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
    // 是否高管（组织后台「高管管理」维护）
    executive;

    // for ui
    label;

    buildRenderData() {
        this.label = this.name;
    }
}