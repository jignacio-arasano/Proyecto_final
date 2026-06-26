import axios from 'axios'

// En desarrollo usa el proxy de Vite (/api → localhost:8080)
// En producción usa la URL del backend en Oracle Cloud (VITE_API_URL)
const baseURL = import.meta.env.VITE_API_URL
  ? `${import.meta.env.VITE_API_URL}/api`
  : '/api'

const api = axios.create({ baseURL })

// La app no tiene pantalla de login: el acceso al frontend se protege a nivel
// de hosting (HTTP Basic en nginx, según la documentación de seguridad del TFG).
// El backend exige JWT en todos los endpoints, así que enviamos un token
// estático de larga duración, inyectado como variable de entorno en el build.
const API_TOKEN = import.meta.env.VITE_API_TOKEN

api.interceptors.request.use(config => {
  if (API_TOKEN) config.headers.Authorization = `Bearer ${API_TOKEN}`
  return config
})

export default api
