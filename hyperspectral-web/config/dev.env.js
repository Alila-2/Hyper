module.exports = {
  NODE_ENV: '"development"',
    //BASE_API: '"http://localhost:8000"',  // 如果你有具体的后端 API 地址
    BASE_API: '"http://192.168.1.106:8000"',
    proxy: {
        '/api': {
            target: 'http://localhost:8000',  // 后端服务地址
            changeOrigin: true,
            secure: false,
              pathRewrite: {
                '^/api': ''  // 重写路径
            }
        }
    }
  // devServer: {
  //   // 开发服务器的配置
  //   host: 'localhost',
  //   port: 8080, // 默认端口
  //   open: true, // 是否自动打开浏览器
  //   hot: true, // 启用热重载
  //   proxy: {
  //     '/': {
  //       target: 'http://localhost:8000',  // 目标后端地址
  //       changeOrigin: true,  // 是否开启跨域
  //       pathRewrite: {
  //         '^/': '',  // 可选的路径重写，去除请求路径中的 /api
  //       },
  //       logLevel: 'debug',  // 打印调试信息
  //     },
  //   }
  // }
}
