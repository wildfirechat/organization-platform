const webpack = require('webpack');

module.exports = {
    productionSourceMap: false,
    configureWebpack: {
        plugins: [
            // 关闭 vue / vue-i18n 的 bundler 特性开关警告
            new webpack.DefinePlugin({
                __VUE_OPTIONS_API__: JSON.stringify(true),
                __VUE_PROD_DEVTOOLS__: JSON.stringify(false),
                __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: JSON.stringify(false),
                __VUE_I18N_FULL_INSTALL__: JSON.stringify(true),
                __VUE_I18N_LEGACY_API__: JSON.stringify(true),
                __INTLIFY_PROD_DEVTOOLS__: JSON.stringify(false),
            })
        ]
    }
}
