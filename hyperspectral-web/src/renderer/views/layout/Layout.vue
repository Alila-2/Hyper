<template>
  <div class="app-wrapper" :class="classObj">
    <!-- <el-header class="header1"><h1>高速飞行目标实时融合软件</h1></el-header> -->
    <sidebar class="sidebar-container"></sidebar>
    <div class="main-container">
      <navbar v-if="!$route.path.startsWith('/ip-access')"></navbar>
      <app-main></app-main>
    </div>
  </div>
</template>

<script>
import { Navbar, Sidebar, AppMain } from './components'
import ResizeMixin from './mixin/ResizeHandler'
import bg1 from '@/assets/bg1.png'

export default {
  name: 'layout',
  components: {
    Navbar,
    Sidebar,
    AppMain
  },
  data(){
    return {
      bg1
    }
  },
  mixins: [ResizeMixin],
  computed: {
    sidebar() {
      return this.$store.state.app.sidebar
    },
    device() {
      return this.$store.state.app.device
    },
    classObj() {
      return {
        hideSidebar: !this.sidebar.opened,
        withoutAnimation: this.sidebar.withoutAnimation,
        mobile: this.device === 'mobile',
      }
    }
  }
}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
  @import "../../styles/mixin.scss";
  .app-wrapper {
    @include clearfix;
    position: relative;
    height: 100%;
    width: 100%;
  }


  .header1 {
  background-color: #001d24; /* 背景颜色 */
  color: white; /* 文字颜色 */
  text-align: center; /* 标题居中 */
  display: flex;
  justify-content: center;
  align-items: center;
  height: 80px;
}

h1 {
  font-size: 36px; /* 标题大小 */
  font-weight: bold; /* 标题加粗 */
}
</style>


