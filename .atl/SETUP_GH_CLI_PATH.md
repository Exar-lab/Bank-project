# Agregar GitHub CLI (gh) al PATH - Permanentemente

## Opción 1: Windows Environment Variables (GUI) - MÁS FÁCIL

### Pasos:

1. **Abre "Edit the system environment variables"**:
   - Presiona `Win + X` → Busca "Environment Variables"
   - O: Settings → System → About → Advanced system settings → Environment Variables

2. **En la ventana "Environment Variables"**:
   - Click en "New" (bajo "User variables" o "System variables")
   - Variable name: `PATH`
   - Variable value: `C:\Program Files\GitHub CLI`
   - Click "OK" dos veces

3. **Reinicia la terminal/PowerShell/Git Bash**

4. **Verifica**:
   ```bash
   gh --version
   ```

---

## Opción 2: Agregar al PATH existente (si ya tienes PATH)

Si ya tienes variables PATH, no quieres sobrescribir. En su lugar:

1. Abre "Environment Variables" (como arriba)
2. Click en la variable `PATH` existente → "Edit"
3. Click "New" dentro de la lista
4. Agrega: `C:\Program Files\GitHub CLI`
5. Click "OK" dos veces
6. Reinicia terminal

---

## Opción 3: PowerShell Permanente

Si usas PowerShell, puedes agregar esto a tu profile:

```powershell
$env:PATH += ";C:\Program Files\GitHub CLI"
```

Luego edita tu profile PowerShell:
```powershell
code $PROFILE
```

O si usas git bash, agrega a `~/.bashrc`:
```bash
export PATH="/c/Program Files/GitHub CLI:$PATH"
```

---

## Opción 4: Command Prompt / Git Bash (Temporal para esta sesión)

Si solo quieres para esta sesión, ejecuta:

### Git Bash:
```bash
export PATH="/c/Program Files/GitHub CLI:$PATH"
```

### PowerShell:
```powershell
$env:PATH += ";C:\Program Files\GitHub CLI"
```

### Command Prompt:
```cmd
set PATH=%PATH%;C:\Program Files\GitHub CLI
```

---

## ✅ Verify

Una vez hecho, verifica que funciona:

```bash
gh --version
gh auth status
gh pr list
```

---

## Recomendación

**Usa Opción 1** (Windows Environment Variables GUI) - es la más fácil y permanente para Windows.

Luego de hacer eso, reinicia Git Bash/PowerShell y `gh` debería estar disponible en cualquier terminal.
