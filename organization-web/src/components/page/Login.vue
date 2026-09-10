<template>
    <div class="login">
        <!-- 品牌侧 -->
        <section class="login-brand">
            <div class="brand-inner">
                <img class="brand-logo" src="@/assets/logo.svg" alt=""/>
                <h1 class="brand-title">{{ $t('login.title') }}</h1>
                <p class="brand-sub">部门、成员与组织关系的一站式管理后台</p>
                <ul class="brand-points">
                    <li v-for="p in points" :key="p">
                        <el-icon>
                            <Check/>
                        </el-icon>
                        <span>{{ p }}</span>
                    </li>
                </ul>
            </div>
            <div class="brand-glow"></div>
        </section>

        <!-- 表单侧 -->
        <section class="login-panel">
            <div class="login-card">
                <h2 class="login-title">{{ $t('login.login') }}</h2>
                <p class="login-hint">请使用管理员账号登录</p>

                <el-form @submit.prevent :model="loginForm" :rules="rules" ref="loginForm" size="large">
                    <el-form-item prop="username">
                        <el-input v-model.trim="loginForm.username" autocomplete="username"
                                  :placeholder="$t('login.input_user_name_tip')">
                            <template #prefix>
                                <el-icon>
                                    <User/>
                                </el-icon>
                            </template>
                        </el-input>
                    </el-form-item>
                    <el-form-item prop="password">
                        <el-input v-model.trim="loginForm.password" type="password" show-password
                                  autocomplete="current-password"
                                  :placeholder="$t('login.input_password_tip')"
                                  @keyup.enter="submitForm('loginForm')">
                            <template #prefix>
                                <el-icon>
                                    <Lock/>
                                </el-icon>
                            </template>
                        </el-input>
                    </el-form-item>
                    <el-button type="primary" size="large" class="login-btn" :loading="loading"
                               @click="submitForm('loginForm')">
                        {{ $t('login.login') }}
                    </el-button>
                </el-form>
            </div>

            <footer class="login-foot">wildfirechat.net &copy; All rights reserved</footer>
        </section>
    </div>
</template>

<script>
import LoginRequest from "@/model/loginRequest";
import {useUserStore} from "@/store/stores/userStore";

export default {
    name: 'Login',
    data: function () {
        return {
            loading: false,
            points: ['部门树与成员的可视化维护', '批量导入，快速搭建组织架构', '组织官方群一键创建与修复'],
            loginForm: {
                username: '',
                password: ''
            },
            rules: {
                username: [
                    {required: true, message: this.$t('login.input_user_name_tip'), trigger: 'blur'}
                ],
                password: [
                    {required: true, message: this.$t('login.input_password_tip'), trigger: 'blur'}
                ]
            },
        }
    },

    setup() {
        const userStore = useUserStore();
        return {userStore};
    },

    methods: {
        submitForm(formName) {
            let self = this;
            self.loading = true
            this.$refs[formName].validate((valid) => {
                if (valid) {
                    this.userStore.login(new LoginRequest(
                        this.loginForm.username, this.loginForm.password
                    )).then(() => {
                        console.log('login success');
                        this.$router.push({path: '/index'});
                    }).catch(reason => {
                        console.log('login failed', reason);
                        this.$message.error('登录失败' + reason);
                        self.loading = false;
                    })
                } else {
                    self.loading = false
                    return false;
                }
            });
        }
    },
    created() {
        if (localStorage.getItem('authToken')) {
            this.$router.replace({path: '/index'});
        }
    }
}
</script>

<style scoped>
.login {
    display: flex;
    height: 100%;
    background: var(--wf-surface);
}

/* ---------- 品牌侧 ---------- */
.login-brand {
    position: relative;
    display: flex;
    align-items: center;
    flex: 1 1 52%;
    padding: 0 8%;
    overflow: hidden;
    background: linear-gradient(150deg, #12203c 0%, #1a2d54 45%, #0e42d2 130%);
    color: #fff;
}

.brand-inner {
    position: relative;
    z-index: 1;
    max-width: 460px;
}

.brand-logo {
    width: 48px;
    height: 48px;
    border-radius: 11px;
    object-fit: contain;
}

.brand-title {
    margin: 20px 0 0;
    font-size: 32px;
    font-weight: 600;
    line-height: 44px;
    letter-spacing: 0.5px;
}

.brand-sub {
    margin: 12px 0 0;
    font-size: 15px;
    line-height: 24px;
    color: rgba(255, 255, 255, 0.72);
}

.brand-points {
    margin: 36px 0 0;
    padding: 0;
    list-style: none;
}

.brand-points li {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 14px;
    font-size: 14px;
    color: rgba(255, 255, 255, 0.86);
}

.brand-points .el-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    flex-shrink: 0;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.14);
    font-size: 12px;
}

/* 柔光，替代旧版那张平铺 + 模糊的背景图 */
.brand-glow {
    position: absolute;
    right: -160px;
    bottom: -200px;
    width: 560px;
    height: 560px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(64, 128, 255, 0.55) 0%, transparent 68%);
}

/* ---------- 表单侧 ---------- */
.login-panel {
    position: relative;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    flex: 1 1 48%;
    padding: 40px;
}

.login-card {
    width: 100%;
    max-width: 360px;
}

.login-title {
    margin: 0;
    font-size: 24px;
    font-weight: 600;
    color: var(--wf-text-1);
}

.login-hint {
    margin: 6px 0 28px;
    font-size: 13px;
    color: var(--wf-text-3);
}

.login-btn {
    width: 100%;
    margin-top: 8px;
    font-weight: 500;
    letter-spacing: 2px;
}

.login-foot {
    position: absolute;
    bottom: 24px;
    font-size: 12px;
    color: var(--wf-text-4);
}

/* 窄屏隐藏品牌侧 */
@media (max-width: 900px) {
    .login-brand {
        display: none;
    }
}
</style>
