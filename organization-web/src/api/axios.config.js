import Axios from 'axios'
import {ElMessage} from 'element-plus'
import router from '../router'
// axios实例
const instance = Axios.create({
    // 针对实际情况进行修改
    //baseURL: 'http://localhost:8880/api',
    baseURL: '/api',
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json;charset=utf-8',
        'Accept': 'application/json;charset=utf-8',
        'authToken': localStorage.getItem('authToken'),
    }
})
instance.interceptors.request.use(request => {
    request.headers['authToken'] = localStorage.getItem('authToken');
    console.log('request', request);
    return request;
})

instance.interceptors.response.use(response => {
    let {code, message, result} = response.data
    if (code === 0) {
        if (response.config.url === '/login') {
            let authToken = response.headers['authtoken'] ? response.headers['authtoken'] : response.headers['authToken']
            localStorage.setItem('authToken', authToken);
        }
    } else {
        if (response.config.url !== '/login' && code === 13) {
            localStorage.removeItem('authToken');
            ElMessage.error('请先登陆')
            router.replace('/login')
        }
        ElMessage.error(message);

        return Promise.reject({code});
    }
    return result
});
export default instance