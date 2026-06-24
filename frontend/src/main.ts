import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Dashboard from './views/Dashboard.vue'
import CompetitorView from './views/CompetitorView.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Dashboard },
    { path: '/competitor/:name', component: CompetitorView, props: true },
  ],
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
