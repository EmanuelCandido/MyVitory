/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
  // O SDK do Firebase sozinho passa de 500 kB; o painel é uma página só, carregada uma vez.
  build: { chunkSizeWarningLimit: 1000 },
  test: {
    // Os testes das regras do Firestore precisam do emulador: rodam com `npm run test:regras`.
    exclude: ['tests/rules.test.ts', 'node_modules/**'],
  },
})
