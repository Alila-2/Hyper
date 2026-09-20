<template>
  <section class="app-main">
    <transition name="fade" mode="out-in">
      <keep-alive>
        <router-view v-if="$route.meta.keepAlive"></router-view>
      </keep-alive>
    </transition>
    <transition name="fade" mode="out-in">
      <router-view v-if="!$route.meta.keepAlive"></router-view>
    </transition>
  </section>
</template>

<script>
import bg1 from '@/assets/bg1.png'


export default {
  name: 'AppMain',
  data() {
    return {bg1}
  }
}
</script>
