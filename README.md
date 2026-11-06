# Crystal Auto Mod

Ein Client-seitiger Minecraft Mod für Version 26.3 (Fabric), der automatisch End-Kristalle platziert und zündet.

## Funktionen

### Kristall-Modus
- Hält der Spieler einen **End-Kristall** in der Hand und macht Rechtsklick auf Bedrock/Obsidian
- Die Mod dreht den Blick minimal so, dass der Kristall gerade im Fadenkreuz liegt und in Reichweite ist
- Führt automatisch Linksklick aus → Kristall explodiert

### Obsidian-Modus
- Hält der Spieler **Obsidian** in der Hand und macht Rechtsklick auf einen Block
- Die Mod platziert den Obsidian, dreht den Blick auf die Oberseite
- Platziert automatisch einen End-Kristall darauf
- Dreht den Blick minimal auf den Kristall
- Führt automatisch Linksklick aus → Kristall explodiert

## Installation & Build

### 1. Repository erstellen
```bash
# Erstelle ein neues GitHub Repository
# Lade alle Dateien aus diesem Ordner hoch (oder klone und kopiere sie hinein)
```

### 2. Pushen
```bash
git init
git add .
git commit -m "Initial commit: Crystal Auto Mod"
git branch -M main
git remote add origin https://github.com/DEIN_USERNAME/DEIN_REPO.git
git push -u origin main
```

### 3. .jar herunterladen
1. Gehe zu deinem Repository auf GitHub
2. Klicke auf **Actions** Tab
3. Warte bis der Build grün ist (oder starte ihn manuell)
4. Klicke auf den erfolgreichen Build
5. Unten bei **Artifacts** → **Crystal-Auto-Mod** herunterladen
6. Entpacke die ZIP → darin liegt `crystal-auto-1.0.0.jar`

### 4. Installation in Minecraft
1. Installiere Fabric Loader für Minecraft 26.3
2. Lege die `.jar` in den `mods` Ordner
3. Starte Minecraft mit dem Fabric Profil

## Konfiguration

Die Config liegt bei: `.minecraft/config/crystal-auto.json`

### Einstellungen
| Option | Standard | Beschreibung |
|--------|----------|--------------|
| `enabled` | `true` | Mod global ein/aus |
| `keybind` | `C` | Taste zum Toggle (Keycode) |
| `crystalModeEnabled` | `true` | Kristall-Modus aktiv |
| `crystalReach` | `4.5` | Reichweite für Kristall-Modus |
| `crystalDelayTicks` | `2` | Verzögerung zwischen Aktionen (Ticks) |
| `crystalAutoAttack` | `true` | Automatisch Linksklick |
| `obsidianModeEnabled` | `true` | Obsidian-Modus aktiv |
| `obsidianReach` | `4.5` | Reichweite für Obsidian-Modus |
| `obsidianDelayTicks` | `3` | Verzögerung zwischen Aktionen (Ticks) |
| `obsidianAutoPlaceCrystal` | `true` | Automatisch Kristall platzieren |
| `obsidianAutoAttack` | `true` | Automatisch Linksklick |
| `maxTurnSpeed` | `30.0` | Max. Drehgeschwindigkeit (Grad/Tick) |
| `requireLineOfSight` | `true` | Sichtprüfung |
| `onlyInCreative` | `false` | Nur im Kreativmodus |
| `showHudStatus` | `true` | Status im Chat anzeigen |

## Steuerung

- **Standard-Taste:** `C` (konfigurierbar in der Config)
- **Toggle:** Drücke die Taste → Mod ein/aus (Chat-Meldung)
- **Kristall-Modus:** End-Kristall in Hand → Rechtsklick auf Bedrock/Obsidian
- **Obsidian-Modus:** Obsidian in Hand → Rechtsklick auf Block

## Multiplayer-Kompatibilität

### ✅ Funktioniert auf Servern

**Ja, die Mod funktioniert im Multiplayer.** Hier ist warum:

1. **Reine Client-Mod** – Kein Server-Teil nötig. Alles läuft über normale Client-Pakete:
   - `CPlayerActionPacket` (Platzieren/Angreifen)
   - `CPlayerRotationPacket` (Blickwinkel)
   - `CSwingArmPacket` (Arm-Swing Animation)

2. **Normale Interaktionsmethoden** – Nutzt `Minecraft.gameMode.interactBlock()` und `attack()` → sendet exakt die gleichen Pakete wie manuelles Spielen.

3. **Keine Cheat-Pakete** – Keine `SetEntityPositionPacket`, keine `CreativeInventoryAction` Pakete, keine Spawn-Pakete manipulieren.

### ⚠️ Latenz (Ping) beachten

- **Problem:** Der Kristall muss server-seitig existieren, bevor der Angriff gesendet wird.
- **Lösung:** Die Mod wartet konfigurierbare Ticks (`crystalDelayTicks` / `obsidianDelayTicks`) nach dem Platzieren, bevor sie angreift.
- **Empfehlung:** Bei >100ms Ping die Delays auf 4-6 Ticks erhöhen.
- **Zusatzprüfung:** Vor dem Angriff prüft die Mod, ob die Entity auf Client-Seite wirklich da ist (`level.getEntitiesOfClass(...)`).

### 🚫 Anti-Cheat & Server-Regeln

| Anti-Cheat | Risiko | Hinweis |
|------------|--------|---------|
| **NoCheatPlus** | Mittel | Erkennt schnelle Drehungen. `maxTurnSpeed` niedrig halten (15-20). |
| **AAC** | Hoch | Sehr empfindlich bei Auto-Clicker/Platzier-Modulen. |
| **Matrix** | Hoch | Erkennt unnatürliche Blickbewegungen. |
| **Sparky/Watchdog** | Niedrig | Hauptsächlich Movement-Checks. |
| **Vanilla-Server** | Kein | Kein Anti-Cheat. |

**Empfehlungen:**
- `maxTurnSpeed` auf **15-20** setzen für natürlicheres Aussehen
- Delays nicht zu niedrig setzen (mindestens 2-3 Ticks)
- Nicht auf Servern nutzen, die explizit "Auto-Crystal" / "Crystal Aura" verbieten
- In PvP-Situationen vorsichtig: Andere Spieler sehen die schnellen Drehungen

## Technische Details

### State Machine
```
IDLE → AIM_BLOCK → PLACE_OBSIDIAN → WAIT_TICK → PLACE_CRYSTAL → WAIT_TICK → AIM_CRYSTAL → ATTACK_CRYSTAL → IDLE
                    ↓
              PLACE_CRYSTAL → WAIT_TICK → AIM_CRYSTAL → ATTACK_CRYSTAL → IDLE
```

### Blickwinkel-Berechnung
- Yaw/Pitch aus Augenposition (`getEyePosition`) und Zielposition
- Glatte Rotation mit `maxTurnSpeed` Begrenzung
- Nur so viel drehen wie nötig (Tolerance: 1.5°-2.0°)

### Reichweitenprüfung
- Echte Interaktionsreichweite: `player.distanceToSqr(entity) <= reach * reach`
- Standard 4.5 Blöcke (Minecraft Standard-Reichweite im Survival)

## Lizenz

MIT License – frei nutzbar, veränderbar, verteilbar.

## Credits

Basierend auf dem Fabric Example Mod Template.
Entwickelt mit Fabric API und Loom.