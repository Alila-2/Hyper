<template>
  <div class="direct-access-container">
    <div class="access-header">
      <h2>系统监控服务</h2>
      <p>正在访问: {{ serviceUrl }}</p>
    </div>
    
    <div class="iframe-wrapper">
      <!-- 同样的iframe实现，只需修改配置 -->
      <div class="loading-state" v-if="loading">
        <el-alert title="正在连接监控服务..." type="info" :closable="false" show-icon>
          <div class="loading-content">
            <i class="el-icon-loading"></i>
            <span>连接到 {{ serviceUrl }}</span>
          </div>
        </el-alert>
      </div>

      <div class="error-state" v-else-if="error">
        <el-alert
          title="连接失败"
          type="error"
          :description="errorMessage"
          show-icon
          :closable="false"
        >
          <el-button type="danger" @click="connectService">重试连接</el-button>
        </el-alert>
      </div>

      <iframe 
        v-show="!loading && !error"
        :src="iframeSrc" 
        @load="onIframeLoad"
        @error="onIframeError"
        class="service-iframe"
        title="系统监控服务"
        allowfullscreen
      ></iframe>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Monitoring',
  data() {
    return {
      serviceConfig: {
        url: '192.168.1.123', // 监控服务IP
        title: '系统监控',
        protocol: 'http'
      },
      iframeSrc: '',
      loading: true,
      error: false,
      errorMessage: ''
    }
  },
  computed: {
    serviceUrl() {
      return `${this.serviceConfig.protocol}://${this.serviceConfig.url}`
    }
  },
  methods: {
    connectService() {
      this.loading = true
      this.error = false
      this.iframeSrc = this.serviceUrl
    },
    onIframeLoad() {
      this.loading = false
      this.error = false
    },
    onIframeError() {
      this.loading = false
      this.error = true
      this.errorMessage = `无法连接到监控服务 ${this.serviceUrl}`
    }
  },
  mounted() {
    this.connectService()
  }
}
</script>