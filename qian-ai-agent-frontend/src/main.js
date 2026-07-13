import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createHead } from '@unhead/vue'

const app = createApp(App)
const head = createHead()

// 🔴 全局 Vue 错误处理，防止未捕获异常白屏
app.config.errorHandler = (err, instance, info) => {
  console.error('[Vue Error]', info, err)
}

app.use(router)
app.use(head)
app.mount('#app')
