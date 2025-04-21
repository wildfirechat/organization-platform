import { defineStore } from 'pinia';
import Api from '@/api/api';

export const useUserStore = defineStore('user', {
  state: () => ({
    account: ''
  }),

  actions: {
    setAccount(account) {
      this.account = account;
    },

    login(payload) {
      console.log('login');
      return Api.login(payload);
    },

    async getAccount() {
      const account = await Api.getAccount();
      this.account = account;
    },

    updatePwd(payload) {
      console.log('updatePwd');
      return Api.udpatePwd(payload);
    }
  }
});