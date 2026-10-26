import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import VxeUITable from 'vxe-table'
import 'element-plus/dist/index.css'
import 'vxe-table/lib/style.css'
import App from './App.vue'
import './styles/index.css'

const app = createApp(App)
app.use(ElementPlus, { locale: zhCn })
app.use(VxeUITable)
app.mount('#app')
