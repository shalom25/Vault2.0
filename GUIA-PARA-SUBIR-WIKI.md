# 🔰 CÓMO ACTIVAR Y SUBIR LA WIKI A `shalom25/Vault2.0`

## 🚨 PASO 0 (IMPRESCINDIBLE) — ACTIVA LA WIKI EN GITHUB

**Sin este paso NADA FUNCIONA, porque el repo `*.wiki.git` todavía NO EXISTE.**

1. Abre: https://github.com/shalom25/Vault2.0/settings
2. Baja hasta el apartado **Features**
3. Marca la casilla ✅ **`[x] Wiki`**
4. Ahora entra en la pestaña **Wiki** (https://github.com/shalom25/Vault2.0/wiki)
5. Haz clic en el botón verde **Create the first page** y escribe lo que sea (solo para inicializar la wiki). Pulsa **Save Page**.
6. Cierra la página. ¡Ya tienes tu `.wiki.git` creado!

> Si no haces esto, obtendrás el error:
> `ERROR: Repository not found.  The requested repository does not exist, or you do not have permission.`

---

## 🚀 PASO 1 · SUBIR LA WIKI (3 FORMAS, ELIGE LA 1)

### ✅ FORMA 1 (MÁS FÁCIL Y RECOMENDADA) — `gh CLI` (sin pegar tokens)

**1.-** Instala GitHub CLI: https://cli.github.com/ (descarga .msi Windows, instalar, siguiente...)  
**2.-** Cierra y abre de nuevo PowerShell / Símbolo del sistema (para que coja el PATH)  
**3.-** Ejecuta y sigue las instrucciones (usa login por navegador web):
```powershell
gh auth login
# -> GitHub.com -> HTTPS -> Login with browser -> pega el código de 8 letras/digitos
```
**4.-** Haz **doble clic** en:
```
📄  d:\trae_projects\vault\deploy-github-wiki.bat
```
¡Listo! Ya tienes la wiki.

---

### ✅ FORMA 2 — CON PERSONAL ACCESS TOKEN (si no quieres instalar gh CLI)

1. Entra en: **https://github.com/settings/tokens?type=beta**  
2. Pulsa **Generate new token** (Fine-grained)
3. Nombre: `Vault2.0 Wiki Access`
4. Expiración: 90 días o lo que quieras
5. **Repository access** → Only select repositories → **`shalom25/Vault2.0`**
6. **Permissions → Repository permissions**:
   - **Contents** → `Read and write`
   - **Wikis** → `Read and write` ✅ ⭐ (IMPORTANTE)
   - **Metadata** → ya estará en Read-only, déjalo.
7. Pulsa **Generate token** y COPIA el token (empieza por `github_pat_...`)
8. Ahora haz **doble clic** en:
```
📄  d:\trae_projects\vault\deploy-github-wiki.bat
```
Te pedirá que pegues el token → lo pegas → ENTER. Hecho.

---

### ✅ FORMA 3 (MANUAL, si no quieres scripts)
```powershell
# 1. Clona tu wiki vacia (siempre la URL termina en .wiki.git)
git clone https://github.com/shalom25/Vault2.0.wiki.git
cd Vault2.0.wiki

# 2. Borra lo que haya (si solo tenia el Home.md inicial)
del /q *.md

# 3. Copia TODO el contenido de nuestra build aplanada
#    (usa EXPLORADOR si te es más fácil: entra en .github-wiki-build\,
#     selecciona TODO, copiar y pegar dentro de la carpeta del repo wiki)
xcopy /E /I /Y d:\trae_projects\vault\.github-wiki-build\* .

# 4. Sube
git add -A
git commit -m "Wiki Vault 2.1.0 completa"
git push
```

---

## 🔧 REGRESARÁS AL PROYECTO Y QUIERES ACTUALIZAR LA WIKI?

1. Haz cambios en los **archivos originales** de `d:\trae_projects\vault\wiki\` (SIEMPRE editas los de subcarpetas, no los aplanados).
2. Ejecuta (regenera `.github-wiki-build\`):
   ```powershell
   powershell -ExecutionPolicy Bypass -File d:\trae_projects\vault\prebuild-wiki.ps1
   ```
3. Doble clic en `deploy-github-wiki.bat`.

---

## 📑 ARCHIVOS INVOLUCRADOS (todos en [d:\trae_projects\vault\](file:///d:/trae_projects/vault/))

| Archivo | Para qué sirve |
|---|---|
| [deploy-github-wiki.bat](file:///d:/trae_projects/vault/deploy-github-wiki.bat) | ⭐ Ejecutable — **doble clic** y sube la wiki a GitHub |
| [prebuild-wiki.ps1](file:///d:/trae_projects/vault/prebuild-wiki.ps1) | Regenera la build aplanada (cuando editas `wiki/`) |
| [wiki/README.md](file:///d:/trae_projects/vault/wiki/README.md) | Documentación completa Mintlify + GitHub Wiki |
| [wiki/_Sidebar.md](file:///d:/trae_projects/vault/wiki/_Sidebar.md) | Menú lateral de la GitHub Wiki |
| [wiki/_Footer.md](file:///d:/trae_projects/vault/wiki/_Footer.md) | Pie de página |
| [.github-wiki-build\](file:///d:/trae_projects/vault/.github-wiki-build) | 📦 Build FINAL (44 Markdowns aplanados + Home.md) |

---

## ✅ COMO COMPROBAR QUE FUNCIONÓ

Abre: **https://github.com/shalom25/Vault2.0/wiki**

Deberías ver:
- 🏠 Home con el Hero Vault v2.1.0 y las 3 tarjetas (Bank System / Physical Notes / Multi-currency).
- 📚 **Menú lateral a la izquierda** con el árbol de secciones (gracias a `_Sidebar.md`).
- 40+ páginas en el **Pages dropdown** superior derecho.
- Footer con Modrinth y Discord.
