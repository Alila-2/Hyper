import Vue from 'vue'
import Router from 'vue-router'

// in development-env not use lazy-loading, because lazy-loading too many pages will cause webpack hot update too slow. so only in production use lazy-loading;
// detail: https://panjiachen.github.io/vue-element-admin-site/#/lazy-loading

Vue.use(Router)

/* Layout */
import Layout from '../views/layout/Layout'

/**
* hidden: true                   if `hidden:true` will not show in the sidebar(default is false)
* alwaysShow: true               if set true, will always show the root menu, whatever its child routes length
*                                if not set alwaysShow, only more than one route under the children
*                                it will becomes nested mode, otherwise not show the root menu
* redirect: noredirect           if `redirect:noredirect` will no redirct in the breadcrumb
* name:'router-name'             the name is used by <keep-alive> (must set!!!)
* meta : {
    title: 'title'               the name show in submenu and breadcrumb (recommend set)
    icon: 'svg-name'             the icon show in the sidebar,
  }
**/
export const constantRouterMap = [
  { path: '/login', component: () => import('@/views/login/index'), hidden: true },
  { path: '/404', component: () => import('@/views/404'), hidden: true },

  {
    path: '/',
    component: Layout,
    redirect: '/login',
    name: 'login',
    hidden: true,
    children: [{
      path: 'login',
      component: () => import('@/views/login/index')
    }]
  },
  {
        path: '/ip-access',
        component: Layout,
        redirect: '/ip-access/viewer',
        name: 'ImageService',
        meta: { title: '相机控制', icon: 'monitor' },
        children: [
            {
                path: 'viewer',
                name: 'IpFrameView',
                component: () => import('@/views/ip-access/index_1101'),
                meta: { title: '数据采集控制', icon: 'monitor' }

            },

        ]
    },
  {
      path: '/index',
      component: Layout,
      redirect: '/index/introduction',
      name: 'index',
      meta: { title: '数据加载', icon: 'example' },
      children: [
        {
          path: 'introduction',
          name: 'Introduction',
          component: () => import('@/views/dashboard/introduction2'),
                meta: { title: '高光谱可视化', icon: 'content', keepAlive: true }
        },
      ]
    },
    {
        path: '/fusion',
        component: Layout,
        redirect: '/fusion/image',
        name: 'fusion',
        meta: { title: '融合成像', icon: 'detection' },
        children: [
            {
                path: 'fusion',
                name: 'Fusion',
                component: () => import('@/views/fusion/image'),
                meta: { title: '融合计算成像', icon: 'detection', keepAlive: true }
            },
        ]
    },

    {
      path: '/detect',
      component: Layout,
      redirect: '/detect/img_denoising',
      name: 'detected',
      meta: { title: '目标探测识别', icon: 'detected' },
      children: [
        {
          path: 'img_denoising',

          name: 'ImageDenoising',
            component: () => import('@/views/detect/detect_real_img'), // src/views/image_pre/img_denoising.vue
          meta: { title: '目标探测识别', icon: 'detect', keepAlive: true }
         }
      ]
    },
    {
        path: '/fusion_detect',
        component: Layout,
        redirect: '/fusion_detect/index',
        name: 'index',
        meta: { title: '融合成像探测', icon: 'example' },
        children: [
            /*{
                path: 'index',
                name: 'Index',
                component: () => import('@/views/fusion_detect/index'),
                meta: {title: '在线融合探测', icon: 'content' }
            },*/
            {
                path: 'index2',
                name: 'Index2',
                component: () => import('@/views/fusion_detect/index5'),
                meta: { title: '融合成像探测', icon: 'content', keepAlive: true }
            }
        ]
    },
    {
        path: '/video',
        component: Layout,
        redirect: '/video/index',
        name: 'index',
        meta: { title: '光谱库', icon: 'example' },
        children: [
            {
                path: 'index',
                name: 'Index',
                component: () => import('@/views/video/index'),
                meta: { title: '典型目标光谱', icon: 'content', keepAlive: true }
            },
        ]
    },

  { path: '*', redirect: '/404', hidden: true }
]

export default new Router({
  // mode: 'history', //后端支持可开
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRouterMap
})
