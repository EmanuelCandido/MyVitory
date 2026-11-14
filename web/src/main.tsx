import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import { motionLevel } from './lib/device'
import './styles.css'

// Antes do primeiro render: o CSS e os hooks de animação leem o nível em <html data-motion>.
document.documentElement.dataset.motion = motionLevel()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
