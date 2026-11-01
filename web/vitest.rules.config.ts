import { defineConfig } from 'vitest/config'

/** Só os testes das regras, contra o emulador do Firestore (`npm run test:regras`). */
export default defineConfig({
  test: {
    include: ['tests/rules.test.ts'],
    testTimeout: 20_000,
  },
})
