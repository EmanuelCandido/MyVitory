/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
  build: {
    // O SDK do Firebase sozinho passa de 500 kB.
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        // Bibliotecas em pedaços próprios: publicar o painel não invalida o Firebase e o React no cache do navegador.
        manualChunks(id) {
          if (/node_modules[\\/](firebase|@firebase|idb)[\\/]/.test(id)) return 'firebase'
          if (/node_modules[\\/](react|react-dom|scheduler)[\\/]/.test(id)) return 'react'
          if (/node_modules[\\/]@dnd-kit[\\/]/.test(id)) return 'dnd'
        },
      },
    },
  },
  test: {
    // Os testes das regras do Firestore precisam do emulador: rodam com `npm run test:regras`.
    exclude: ['tests/rules.test.ts', 'node_modules/**'],
  },
})
