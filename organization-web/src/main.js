import {createApp} from 'vue';
import App from './App.vue';
import router from './router';
import pinia from './store/pinia';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import {createI18n} from 'vue-i18n';
import 'element-plus/dist/index.css'; // 默认主题

import zhCNMessages from '@/assets/languages/zh-CN.json';
import zhTWMessages from '@/assets/languages/zh-TW.json';
import enMessages from '@/assets/languages/en.json';

const i18n = createI18n({
    // vue-i18n 9 默认使用 composition api，这里保持 legacy 模式，组件内可以继续使用 this.$t
    legacy: true,
    globalInjection: true,
    // 使用localStorage存储语言状态是为了保证页面刷新之后还是保持原来选择的语言状态
    locale: localStorage.getItem('lang') ? localStorage.getItem('lang') : 'zh-CN', // 定义默认语言为中文
    fallbackLocale: 'zh-CN',
    messages: {
        'zh-CN': zhCNMessages,
        'zh-TW': zhTWMessages,
        'en': enMessages
    }
});

const app = createApp(App);

// element-plus 不再提供字体图标，图标以组件形式全局注册，模板里可以直接用 icon="Plus" 或 <el-icon><Plus/></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component);
}

app.use(pinia);
app.use(router);
app.use(i18n);
app.use(ElementPlus, {size: 'small', locale: zhCn});

app.mount('#app');

export default app;
