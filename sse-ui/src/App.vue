<template>
  <SSEUI />
<!--  <SSEUI-WebFlux />-->
</template>

<script>
import SSEUI from './components/SSEUI.vue'
import SSEUIWebFlux from "@/components/SSEUI-WebFlux.vue";

export default {
  name: 'App',
  components: {
    SSEUIWebFlux,
    SSEUI: SSEUI
  }
}
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin-top: 60px;
}
</style>
