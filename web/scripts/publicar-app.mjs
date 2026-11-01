// Publica uma versão nova do app para a atleta, sem Play Store e sem cabo:
//
//   npm run publicar-app -- "O que mudou (aparece no aviso do app)"
//
// 1. Gera o APK release (assinado com a chave de keystore.properties: a mesma do app instalado).
// 2. Confere que o versionCode é maior que o publicado (suba em app/build.gradle.kts a cada versão).
// 3. Sobe o APK numa Release do GitHub e publica no Hosting do painel o version.json (versão, link, SHA-256, notas).
// O app procura o version.json ao abrir e uma vez por dia, e oferece "Atualizar".
import { execSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../', import.meta.url))
const web = fileURLToPath(new URL('../', import.meta.url))
const notes = process.argv.slice(2).join(' ').trim()
const run = (cmd, cwd) => execSync(cmd, { cwd, stdio: 'inherit', shell: true })

if (!existsSync(`${root}keystore.properties`)) {
  throw new Error('Falta keystore.properties: sem a chave de sempre, o Android não instala por cima.')
}
const env = Object.fromEntries(
  readFileSync(`${web}.env.local`, 'utf-8')
    .split(/\r?\n/)
    .filter((l) => l.includes('=') && !l.startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
)
const project = env.VITE_FIREBASE_PROJECT_ID
const site = `https://${project}.web.app`

console.log('› Gerando o APK release…')
// Caminho completo: o shell que o npm usa nem sempre procura na pasta atual.
run(`"${root}${process.platform === 'win32' ? 'gradlew.bat' : 'gradlew'}" :app:assembleRelease`, root)

const outDir = `${root}app/build/outputs/apk/release/`
const meta = JSON.parse(readFileSync(`${outDir}output-metadata.json`, 'utf-8')).elements[0]
const { versionCode, versionName, outputFile } = meta

const published = await fetch(`${site}/app/version.json`, { cache: 'no-store' })
  .then((r) => (r.ok ? r.json() : null))
  .catch(() => null)
if (published && published.versionCode >= versionCode) {
  throw new Error(`O publicado já é o ${published.versionName} (${published.versionCode}). Suba versionCode/versionName em app/build.gradle.kts.`)
}

// O Hosting gratuito do Firebase não aceita .apk: o APK vai para as Releases do GitHub (repositório público)
// e o Hosting guarda só o version.json que o app consulta.
const gh = process.platform === 'win32' ? '"C:/Program Files/GitHub CLI/gh.exe"' : 'gh'
const repo = execSync('git remote get-url origin', { cwd: root }).toString().trim().replace(/^.*github\.com[/:]/, '').replace(/\.git$/, '')
const tag = `v${versionName}`
const stage = `${root}app/build/outputs/apk/release/publicar/`
rmSync(stage, { recursive: true, force: true })
mkdirSync(stage, { recursive: true })
// Nome fixo: o link .../releases/latest/download/myvitory.apk sempre leva à versão mais nova.
copyFileSync(outDir + outputFile, `${stage}myvitory.apk`)
const sha256 = createHash('sha256').update(readFileSync(`${stage}myvitory.apk`)).digest('hex')
const notesFile = `${stage}notas.md`
writeFileSync(notesFile, `${notes || 'Versão nova do MyVitory.'}

Instale pelo próprio app (aviso na tela Hoje) ou baixe o myvitory.apk abaixo.
`)
console.log(`› Criando a release ${tag} em ${repo}…`)
run(`${gh} release create ${tag} "${stage}myvitory.apk" --repo ${repo} --title "MyVitory ${versionName}" --notes-file "${notesFile}" --latest`, root)

const appDir = `${web}public/app/`
mkdirSync(appDir, { recursive: true })
const info = {
  versionCode,
  versionName,
  apk: `https://github.com/${repo}/releases/download/${tag}/myvitory.apk`,
  sha256,
  sizeBytes: statSync(`${stage}myvitory.apk`).size,
  notes,
  publishedAt: new Date().toISOString(),
}
writeFileSync(`${appDir}version.json`, `${JSON.stringify(info, null, 2)}
`)

console.log(`› Publicando ${versionName} (${versionCode}) em ${site}…`)
run('npm run build', web)
run(`npx firebase deploy --only hosting --config ../firebase.json --project ${project} --non-interactive`, web)
console.log(`✔ Versão ${versionName} publicada. O app avisa ao abrir (ou em até um dia).`)
console.log(`  Primeira instalação pelo navegador do celular: https://github.com/${repo}/releases/latest/download/myvitory.apk`)
