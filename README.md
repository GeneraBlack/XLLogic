# XL Logic

Ein NeoForge-Prototype fuer Minecraft 1.21.1, der den ComputerCraft-Gedanken mit Python statt Lua aufsetzt.

## Roadmap

- Die sauber dokumentierte Projekt-Roadmap liegt in [docs/ROADMAP.md](docs/ROADMAP.md).
- Eine blockweise Statusmatrix mit Implementierungsgrad und naechsten Schritten liegt in [docs/BLOCK_STATUS_MATRIX.md](docs/BLOCK_STATUS_MATRIX.md).
- Reproduzierbare Ingame-Szenarien fuer Discovery-, Segment- und Farbbus-Topologien stehen in [docs/NETWORK_SCENARIOS.md](docs/NETWORK_SCENARIOS.md).
- Die aktuelle NeoForge-GameTest-Abdeckung prueft Discovery-, Bus- sowie Viewer-Reopen-, Recovery-Draft-Resume-, Recovery-Draft-Divergenz-, Chunk-Unload-, Restart-Reload-, Teleport-, Dimensionswechsel-, Disconnect-, Reconnect- und Mehrspieler-Lease-Kernfaelle mit `./gradlew.bat runGameTestServer`.
- Reproduzierbare Ingame-Szenarien fuer die aktuelle XLAPI-Bridge-Policy stehen in [docs/BRIDGE_POLICY_SCENARIOS.md](docs/BRIDGE_POLICY_SCENARIOS.md).
- Eine erste statische Projekt-Homepage fuer Branding, Feature-Ueberblick, Blockerklaerungen und Python-/Builder-Beispiele liegt unter [docs/site/index.html](docs/site/index.html).

## Was bereits steht

- NeoForge-Grundprojekt fuer 1.21.1 mit NeoForge 21.1.218
- GraalPy als eingebettete Python-Engine
- Ingame-Python-Screen als erster Computer-Prototyp
- Erste registrierte XL-Logic-Blockfamilie mit Creative Tab und BlockItems
- Texteditor mit Cursor, Mehrzeilen-Eingabe, Maus-Cursorplatzierung, Bereichsauswahl, Copy/Cut/Paste, Undo/Redo, Auto-Indent, Scrollen und lokalen Parse-Hinweisen direkt im Editor
- Python-Syntax-Highlighting fuer Keywords, Strings, Kommentare, Zahlen und Dekoratoren sowie kontextbezogene Suggestions fuer Python-Keywords, Builtins, XL-Logic-Globals, bekannte Geraete-APIs, Seiten, Routen und haeufige State-/Preview-Schluessel
- Script-Ausfuehrung per F5 mit Ausgabe-Pane fuer stdout, stderr, strukturierte show_* Outputs und Plan-Schritte in gemeinsamer Reihenfolge
- Konfigurierbares serverseitiges Statement-Budget ueber xllogic-server.toml
- Weitere serverseitige Runtime-Guardrails ueber xllogic-server.toml: Execute-Cooldown pro Computer, maximale Scriptgroesse, Watchdog-Laufzeitlimit sowie stdout-/stderr-Bytegrenzen fuer serverseitige Ausfuehrung
- Exklusives Editor-Lease pro Computer mit Heartbeat, serverseitigem Logout-, Chunk-Unload-, Teleport- und Dimensions-Cleanup, restart-sicher rein transientem Lock-Zustand, sauberer Freigabe beim Schliessen, read-only Fremdansicht fuer parallele Mehrspieler-Zugriffe, getesteter Viewer-Reopen-Uebergabe, klaren clientseitigen Statuswechseln bei Zielverlust, Wiederherstellung und Lock-Uebergabe sowie einer echten Verlust-Policy fuer dauerhaft fehlende Ziele: read-only Viewer schliessen nach einer Grace-Phase automatisch, waehrend aktive Editoren in einen lokalen Recovery-Draft wechseln, der ueber einen Resume-Handshake automatisch wieder an den urspruenglichen Computer andockt, sobald Ziel und Lock verfuegbar sind; wenn lokaler Draft und serverseitiger Scriptstand divergieren, pausiert das Auto-Resume explizit statt still zu ueberschreiben, nutzt einen basisgestuetzten Drei-Wege-Merge mit automatischer Voruebernahme nicht konfligierender Server-Hunks und laesst nur echte Konflikte sichtbar zur bewussten Aufloesung offen
- JUnit-Runtime-Regressionstests fuer boesartige Busy-Loops sowie uebergrosse stdout-/stderr-Ausgaben laufen ueber .\gradlew.bat test
- NeoForge-GameTests fuer Discovery-Konflikt, XLAPI-Segmenttrennung, Farbfilter-Blocker, device_channel-Blocker sowie Editor-Lease-read-only, Viewer-Reopen-Handoff, Recovery-Draft-Resume, Recovery-Draft-Divergenz, Release-Handoff, Timeout-Handoff, Disconnect-Handoff, Reconnect-Reclaim, read-only-Disconnect-Stabilitaet, Teleport-Handoff, Dimensionswechsel-Handoff, Chunk-Unload-Cleanup und Restart-Reload

## Aktuelle Blockfamilie

- Computer: Oeffnet den Python-Screen per Rechtsklick.
- Screen: Persistente Anzeige-Basis als duennes Frontpanel mit klar lesbarer Vorderseite, kleiner Status-LED und passender Kollision. Die LED pulsiert waehrend laufender Scripts und blinkt kurz bei frisch aufgetretenen Fehlern. Screens werden ueber Netzwerkkabel und den Discovery-Modus des Computers gefunden und rendern dessen letzten synchronisierten Runtime-Zustand direkt auf die Vorderseite des Blocks, inklusive Status-Summary, Zeilen, Key-Value-Karten, Tabellen und Plan-Cards. Fuer groessere Inhalte wird automatisch auf mehrere Seiten aufgeteilt, grosse Tabellen oder Cards koennen in einen Fokusmodus mit Detail-Scroll versetzt werden, sichtbare Feld-, Zeilen- und Spaltenbereiche reagieren dabei bereits auf die konkrete Trefferposition, und innerhalb eines fokussierten Ausschnitts bestimmt die exakte Klickposition jetzt Richtung und Schrittweite der Navigation statt nur eines festen Drei-Zonen-Rasters; zusammenhaengende gleich ausgerichtete Screen-Flaechen desselben Computers bilden automatisch einen rechteckigen Multiblock-Screen, unregelmaessige Flaechen werden stabiler in nachvollziehbare Teilrechtecke zerlegt und Innenkanten verschwinden nur noch innerhalb derselben Teilflaeche; Follower-Kacheln zeigen bei ungeladenem Controller einen Hinweis statt leer zu bleiben.
- Network Cable: Echtes lokales Verbindungsmedium mit Kabelgeometrie. Es verbindet Computer und Endpunkte fuer Discovery ueber einen sichtbaren Kabelpfad und zeigt in der Debugansicht jetzt auch XLAPI-Segmentgrenzen sowie ungeladene Frontier-Kanten an.
- XLAPI Block: Persistente Bridge-Basis. Er trennt lokale Computersegmente und kann zusaetzlich ueber definierte Uplink-Gruppen entfernte Endpunkte aus anderen geladenen Segmenten derselben Dimension in den Python-Kontext einblenden sowie strukturierte Status-/Ping-Abfragen und Mailbox-Nachrichten zwischen Computern bereitstellen.
- Redstone I/O: Hat Input/Output-Modus, liest Eingangspegel ein und kann spaeter Ausgangspegel treiben.
- Redstone Bus Cable: Tragt alle 16 Bus-Kanaele zwischen verbundenen Redstone-I/O-Bloecken verlustfrei weiter und unterscheidet in der Debugsicht jetzt Farbfilter-Blocker, seitenseitige Geraetekanal-Konflikte und ungeladene Bus-Frontiers.
- Coloured Redstone Cable: Filtert das Busnetz auf genau einen Kanal und erlaubt damit farbgetrennte Abzweige; benachbarte Coloured-Kabel verbinden sich nur noch, wenn sie denselben Kanal tragen. Shift-Rechtsklick schaltet den Kanal 0 bis 15 weiter.
- Light Sensor: Liefert bereits das lokale Lichtlevel als Comparator-Ausgabe.
- Clock: Liefert bereits eine Tageszeit-Komparator-Ausgabe und zeigt Spiel-/Systemzeit an.
- Rain Sensor: Liefert bereits Regenstatus als Comparator-Ausgabe.
- Material I/O: Proxy fuer angrenzende Inventare und Fluids mit Python-steuerbaren Item- und Fluid-Transfers.
- Crafting I/O: Persistentes 3x3-, 5x5- oder 7x7-Rezeptfrontend, das eine Crafting CPU und Material-I/O-Routen im Netzwerk ansteuern kann.
- Crafting CPU: Internes 3x3-Rezeptmuster mit Python-gesteuerter Crafting-Ausfuehrung gegen angrenzende Inventare.

## Bedienung im Spiel

- Taste P oeffnet den Python-Computer-Prototyp
- Rechtsklick auf den Computer-Block oeffnet den Python-Computer mit dessen erkanntem Netzwerk-Kontext
- Shift-Rechtsklick auf den Computer-Block aktualisiert den Discovery-Modus, listet alle gefundenen Endpunkte im Chat auf, meldet XLAPI-Grenzen und ungeladene Frontier-Kanten des lokalen Segments und weist erreichbare Screens ueber das Netz automatisch zu
- Rechtsklick auf Network Cable zeigt eine kompakte Discovery-Zusammenfassung fuer das lokale Segment inklusive XLAPI-Grenzen und ungeladener Frontiers; Shift-Rechtsklick listet die gefundenen Computer, Endpunkte und Boundary-Hints im Detail auf
- Screen-Blocks werden nicht manuell verlinkt, sondern ueber das verbundene Kabelnetz dem Computer bei der Discovery zugeordnet
- Verlinkte Screen-Blocks zeigen den letzten synchronisierten Laufzustand ihres Computers als Rich-Output-Panel mit Karten- und Tabellenlayout auf der Vorderseite an
- Mehrere verlinkte Screen-Blocks mit gleicher Ausrichtung und demselben Zielcomputer bilden automatisch eine gemeinsame rechteckige Screen-Flaeche; unregelmaessige Formen werden stabiler in Teilrechtecke zerlegt, und sichtbare Kanten zwischen unterschiedlichen Teilflaechen bleiben erhalten
- Gemeinsame Kanten zwischen gleich ausgerichteten Screen-Kacheln werden sauberer gecullt, und wenn der Controller-Chunk fehlt, meldet die betroffene Follower-Kachel das explizit im Panel und bei Interaktion
- Rechtsklick in die obere Frontzone eines verlinkten Screen-Blocks blaettert zu aelterem Output; Shift-Rechtsklick dort blaettert zu neuerem Output zurueck
- Rechtsklick in die mittlere Frontzone fokussiert die dort tatsaechlich sichtbare strukturierte Tabelle oder Card; innerhalb sichtbarer Cards wird dabei die getroffene Feldzeile uebernommen, innerhalb sichtbarer Tabellen die getroffene Zeile und der sichtbare Spaltenbereich
- Solange ein Detail fokussiert ist, loest sich die Bedienung innerhalb des sichtbaren Ausschnitts vom festen Drei-Zonen-Modell: Klicks nahe der Mitte richten den Fokus auf den konkret getroffenen sichtbaren Teilbereich neu aus, Klicks weiter links oder rechts navigieren horizontal, Klicks weiter oben oder unten vertikal; Klicks nahe am Rand springen grob ueber den sichtbaren Ausschnitt, Klicks naeher zur Mitte bewegen fein um einen Schritt
- Shift-Rechtsklick innerhalb des fokussierten sichtbaren Ausschnitts beendet den Fokus und kehrt zur Seitenansicht zurueck; ausserhalb davon bleibt das bisherige Zonen-Fallback fuer Paging und einfache Hinweise erhalten
- Pro lokalem Kabelsegment ist nur ein Computer erlaubt. Mehrere Computer sind nur dann gueltig, wenn sie durch XLAPI-Blocks in getrennte Segmente aufgeteilt sind.
- Lokale Screens und Computerkonflikte bleiben strikt segmentlokal. XLAPI importiert nur zusaetzliche bridged Endpunkte fuer die Laufzeit, nicht fuer die Screen-Zuordnung.
- Beim Oeffnen ueber den Computer-Block werden Script-Inhalt und letzter Laufzustand aus dem Block geladen
- Beim ersten Oeffnen beansprucht ein Spieler exklusiv den Editor-Lock des Computers; weitere Spieler sehen denselben Computer read-only, bis der Lock freigegeben oder per Timeout abgelaufen ist
- Offene read-only Screens halten den Session-Status ebenfalls per Heartbeat aktuell und uebernehmen den Editor-Lock automatisch, sobald der bisherige Editor ihn freigibt, sich ausser Reichweite bewegt, die Dimension wechselt, disconnectet, abstuerzt oder auslaeuft
- Chunk-Unloads und Server-Restarts stellen keinen alten Editor-Lock wieder her; nach Reload muss der Lease frisch beansprucht werden
- Wenn ein offener blockgebundener Screen sein Ziel verliert, wechselt er explizit in einen target-unavailable Zustand, blockiert Schreibaktionen und meldet Zielverlust, Wiederherstellung sowie Lock-Uebergaben direkt im Actionbar-Status; bleibt das Ziel laenger weg, schliesst ein read-only Viewer nach 10s automatisch, waehrend ein bisheriger Editor in einen lokalen Recovery-Draft mit demselben Script-Stand uebergeht und im Hintergrund regelmaessig einen Resume-Handshake versucht, bis der Computer wieder erreichbar und der Editor-Lock wieder frei ist; bei divergierendem Scriptstand pausiert das Auto-Resume klar sichtbar, vergleicht gemeinsame Basis, lokalen Draft und aktuellen Serverstand in einem echten Drei-Wege-Merge, uebernimmt nicht konfligierende Server-Aenderungen bereits vorab und oeffnet nur fuer verbleibende Konflikte eine Compare-/Merge-Seitenleiste mit Hunk-Navigation, selektiver Server-Uebernahme per Ctrl+Right, komplettem Server-Reload per Ctrl+R und bewusstem Publish des aktuell gemergten Drafts per Ctrl+Enter oder Ctrl+Shift+R
- Beim Schliessen des blockgebundenen Screens wird der aktuelle Script-Inhalt in den Computer-Block zurueckgeschrieben und der Editor-Lock explizit freigegeben
- Das Starter-Script zeigt jetzt fuer alle Devices Scope, Remote-Policy und Schreibbarkeit an, damit bridged Endpunkte sofort als read_only oder read_write erkennbar sind.
- F5 fuehrt blockgebundene Scripts serverseitig aus, synchronisiert den autoritativen Laufzustand zurueck in den Screen und nutzt dabei die in xllogic-server.toml konfigurierbaren Runtime-Guardrails fuer Statement-Budget, Execute-Cooldown, maximale Scriptgroesse, Watchdog-Laufzeitlimit und stdout-/stderr-Bytegrenzen
- Im Python-Screen scrollt das Mausrad ueber dem Output-Bereich durch die Laufhistorie; Strg+Bild auf/Bild ab blaettern seitenweise, Strg+Ende springt zur neuesten Ausgabe zurueck
- Read-only Zuschauer koennen im Python-Screen weiter navigieren, kopieren und Output-History lesen, aber nicht schreiben, einfuegen oder ausfuehren
- Shift-Rechtsklick auf den Computer zeigt im Discovery-Debug jetzt zusaetzlich die aktiven Runtime-Guardrails samt aktuellem Cooldown-, Watchdog- und Editor-Lock-Status des Computers an
- Strg+V fuegt Text aus der Zwischenablage ein
- Enter fuegt mit einfacher Python-Einrueckung eine neue Zeile ein
- Shift-Rechtsklick auf Redstone I/O, Material I/O, Crafting I/O und Crafting CPU schaltet die jeweilige Minimal-Konfiguration weiter
- Rechtsklick auf Redstone Bus Cable zeigt eine Kanalfluss-Zusammenfassung mit kompakten Produzent-/Konsument-Markierungen sowie getrennten Zaehlern fuer Filter-, Geraetekanal- und Frontier-Blocker; Shift-Rechtsklick listet zusaetzlich echte Routenhops, diese Blocker-Hinweise und die angebundenen Redstone-I/O-Bloecke mit ihrem Buszustand auf
- Shift-Rechtsklick auf Coloured Redstone Cable schaltet dessen Bus-Kanal von 0 bis 15 weiter
- Rechtsklick mit einem umbenannten Namensschild auf einen Endpunkt benennt dessen Endpoint-Namen fuer das Netzwerk um

## Python API im Computer

- Bei blockgebundenem Oeffnen stehen im Script automatisch computer, endpoints, peripherals, endpoint_names, list_endpoints() und get_endpoint(name) bereit.
- computer enthaelt name, position und endpoint_count.
- endpoints ist eine Liste aus Dictionaries mit api_name, name, type, position, distance, scope, remote, bridge_name und bridge_group.
- peripherals ist ein Dictionary nach api_name, damit Endpunkte direkt ueber einen stabilen Python-Key ansprechbar sind.
- Doppelte Endpoint-Namen werden automatisch als api_name, api_name_2, api_name_3 und so weiter eindeutig gemacht.
- Bridged Endpunkte bekommen zusaetzlich einen remote_-Praefix im api_name, damit lokale und entfernte Geraete nicht still kollidieren.
- Der blockgebundene Screen stellt ausserdem den letzten Laufzustand wieder her, inklusive Erfolg/Fehlschlag, Summary und Output-Zeilen.
- Zusaetzlich stehen jetzt computer_api, world, devices, device_names, list_devices() und get_device(name) als reichhaltige Python-API bereit.
- devices liefert echte Python-Handles auf eure Endpunkte statt nur Metadaten, inklusive Redstone-, Sensor-, Material-I/O-, Crafting- und XLAPI-Funktionen.

### Rich API

- world bietet dimension(), game_time(), day_time(), is_day(), is_night(), is_raining(), is_thundering(), moon_phase() und real_time().
- computer_api bietet available(), name(), position(), endpoint_count(), network_summary(), list_devices() und get_device(name).
- Jedes Device liefert jetzt auch network_scope(), is_remote(), bridge_name(), bridge_group(), remote_policy() und remote_writable(), damit Scripts lokales und ueber XLAPI erreichbares Netz trennen und Remote-Schreibzugriffe vorab erkennen koennen.
- XLAPI-Devices unterstuetzen ausserdem forwarded_messages(), remote_computers(), inbox_count(), peek_messages(limit), poll_messages(limit), send_message(message, channel='default', target=''), send_command(command, payload='', target=''), request_status(target=''), ping(payload='ping', target=''), request_devices(target=''), request_runtime(target='', output_limit=8, plan_limit=6), peek_responses(limit) und poll_responses(limit). Aktuell sind als strukturierte Commands status, ping, devices und runtime eingebaut.
- Redstone-I/O-Devices unterstuetzen get_mode(), set_mode(mode), read(side), write(side, level), channel(side) und set_channel(side, channel). Bus-Kanaele werden jetzt ueber Redstone Bus Cable und Coloured Redstone Cable netzweit transportiert.
- Light Sensor, Rain Sensor und Clock liefern light_level(), is_raining()/rain_level() sowie game_time()/day_time()/real_time().
- Material I/O kann angrenzende Inventare und Fluidtanks je Seite inspizieren und zwischen zwei Seiten verschieben.
- Crafting CPU speichert jetzt ein echtes internes 3x3-Rezept, liefert Preview-Daten und kann gegen angrenzende Input-/Output-Inventare craften.
- Crafting I/O speichert ein groesseres Rezeptgitter, verwaltet ein aktives 3x3-Fenster, kennt benannte Zwischenlager-Routen, kann daraus mehrstufige Plaene mit unterschiedlichen Input-/Output-Routen pro Schritt fuer eine verlinkte Crafting CPU aufbauen und besitzt jetzt eine persistente Plan-Queue mit Schritt-Resume sowie einen umschaltbaren Reservation-Mode: Standardmaessig wird die gesamte verbleibende Queue reserviert (`full_queue`), optional nur der aktuelle Restlauf (`active_cycle`), damit fehlende Materialien oder spaetere Zwischenlager-/Output-Routen je nach Jobgroesse frueh oder bewusst erst zyklusweise blockieren. Blocker und Fehlstarts werden dabei jetzt explizit als `material_missing`, `buffer_full`, `output_full`, `route_missing`, `recipe_invalid`, `cpu_unavailable`, `intermediate_missing` oder `intermediate_contaminated` klassifiziert; der Queue-Job selbst fuehrt ausserdem einen expliziten Gesamtstatus (`resumable`, `blocked`, `failed`, `completed`), verfolgt erwartete Zwischenprodukte ueber benannte Buffer-Routen hinweg, erkennt fremde Items in getrackten Zwischenlagern, leitet `rebuild_plan()` Craft-Zahlen jetzt aus gestapelten Rezeptslots ab und erhaelt dabei vorhandene Routenfenster fuer heterogenere Ketten.
- `queued_plan()` liefert jetzt einen strukturierten Gesamtjob-Snapshot mit `total_cycles`, aktuellem Queue-Fortschritt, `job_status`, `reservation_mode`, `action_hint`, `error_class`, `message`, Input-/Output-Route, `tracked_intermediates`, `reservable_cycles`, `reservable_steps`, `blocked_cycle_index` und `blocked_step_index`, damit Scripts und UI denselben Queue-, Zwischenlager- und Reservierungszustand sehen; fuer typische Folgeaktionen kommen dabei jetzt unter anderem `clean_buffer` und `switch_to_active_cycle` als Action-Hints hinzu.
- Erfolgreiche, blockierte, partielle, fehlgeschlagene und uebersprungene Planschritte werden inklusive expliziter Fehlerklasse in den Computer-Runtime-State und damit auch in die synchronisierte Laufanzeige zurueckgespiegelt; die Runtime-Zusammenfassung leitet daraus jetzt auch einen Gesamtjobstatus fuer den laufenden beziehungsweise letzten Planlauf ab und nutzt dabei bei Bedarf den expliziten Queue-Job-Snapshot.
- Die Python-Umgebung unterstuetzt strukturierte Bildschirm-Ausgaben ueber output.line(...), output.kv(...), output.table(...), output.plan_card(...) sowie show_kv(...), show_table(...) und show_plan_card(...).
- Fuer bridged Endpunkte gilt jetzt eine konservative Remote-Policy: redstone_io ist read_write, screen, light_sensor, rain_sensor, clock, material_io, crafting_io und crafting_cpu sind read_only.
- Endpoint-Umbenennung und XLAPI-Rekonfiguration bleiben lokal; verbotene Remote-Mutationen werfen eine klare IllegalStateException statt still durchzulaufen.
- Die vorgesehenen manuellen Abnahme-Szenarien fuer diese Policy sind in [docs/BRIDGE_POLICY_SCENARIOS.md](docs/BRIDGE_POLICY_SCENARIOS.md) festgehalten.

Beispiel:

```python
print(computer)
print(endpoint_names)
print(get_endpoint(endpoint_names[0]))
for endpoint in endpoints:
	print(endpoint["type"], endpoint["name"], endpoint["position"])
```

Beispiel mit der neuen API:

```python
print(world.dimension(), world.day_time(), world.is_raining())

show_kv("World", {
	"dimension": world.dimension(),
	"day_time": world.day_time(),
	"raining": world.is_raining(),
}, text="Server-backed world state")

for name in list_devices():
	device = get_device(name)
	print(device.type(), device.remote_policy(), device.remote_writable())
	print(device.state())

show_table("Devices", ["API", "Type", "Scope", "Distance"], [
	[name, get_device(name).type(), get_device(name).network_scope(), get_device(name).distance()]
	for name in list_devices()
], text="Current network devices")

redstone = get_device("redstone_io")
if redstone is not None:
	redstone.set_mode("output")
	redstone.write("north", 15)

material = get_device("material_io")
if material is not None:
	print(material.inventory("north"))
	moved = material.transfer_item("north", "south", 0, 16)
	print("moved items:", moved)

cpu = get_device("crafting_cpu")
if cpu is not None:
	cpu.set_recipe_slot(0, "minecraft:oak_planks")
	cpu.set_recipe_slot(1, "minecraft:oak_planks")
	cpu.set_recipe_slot(3, "minecraft:oak_planks")
	cpu.set_recipe_slot(4, "minecraft:oak_planks")
	print(cpu.preview())
	print("crafted:", cpu.craft("west", "east", 1))

crafting = get_device("crafting_io")
if crafting is not None:
	crafting.set_linked_cpu("crafting_cpu")
	crafting.set_material_input_device("material_io")
	crafting.set_material_input_side("north")
	crafting.set_material_output_device("material_io")
	crafting.set_material_output_side("south")
	crafting.set_route("buffer_in", "material_io", "north")
	crafting.set_route("buffer_out", "material_io", "south")
	crafting.set_grid_slot(0, "minecraft:oak_planks")
	crafting.set_grid_slot(1, "minecraft:oak_planks")
	crafting.set_grid_slot(5, "minecraft:oak_planks")
	crafting.set_grid_slot(6, "minecraft:oak_planks")
	print(crafting.linked_preview())
	print("linked crafted:", crafting.craft_linked(1))
	print("plan steps:", crafting.rebuild_plan())
	crafting.set_plan_step(0, 0, 0, 1, "buffer_in", "buffer_out")
	print(crafting.plan())
	show_plan_card("First Step", crafting.plan_step(0), text="Current routed plan step")
	print("planned crafted:", crafting.craft_plan(1))

bridge = get_device("xlapi_block")
if bridge is not None:
	print("remote computers:", bridge.remote_computers())
	print("status request:", bridge.request_status())
	print("ping request:", bridge.ping("hello remote"))
	print("devices request:", bridge.request_devices())
	print("runtime request:", bridge.request_runtime(output_limit=4, plan_limit=2))
	for response in bridge.poll_responses():
		print("bridge response:", response)
	print("delivered:", bridge.send_message("ping from local segment", channel="status"))
	print("inbox before poll:", bridge.inbox_count())
	for message in bridge.poll_messages():
		print("bridge message:", message)
```

Die strukturierte Response fuer devices liefert pro Remote-Computer eine kompakte Device-Liste mit api_name, type, scope, bridge_name, bridge_group, remote_policy und remote_writable. Die strukturierte Response fuer runtime liefert Summary, running/last_success sowie begrenzte output_lines und plan_steps; output_limit und plan_limit lassen sich pro Anfrage mitgeben.

## Netzwerkerkennung

- Der Computer traversiert fuer lokale Discovery aktuell Computer und Network Cable als Netzpfad; XLAPI-Blocks bilden dabei die Grenze zwischen getrennten Computersegmenten.
- Rechtsklick auf Computer und Network Cable liefert jetzt zusaetzliche Discovery-Debug-Ausgaben zu Segmentgroesse, Kabelanzahl und Endpoint-Typen.
- Redstone Bus Cable traegt alle 16 Bus-Kanaele zwischen Redstone-I/O-Bloecken; Coloured Redstone Cable laesst davon jeweils genau einen konfigurierten Kanal durch, und benachbarte Coloured-Kabel verbinden sich nur bei gleichem Kanal.
- Rechtsklick auf Buskabel liefert jetzt Kanalfluss-Debug-Ausgaben mit staerkstem Signal, Routenkabellast und Anzahl von Input-/Output-Endpunkten pro Kanal.
- Bereits der normale Rechtsklick auf Redstone Bus Cable zeigt pro Kanal eine kompakte Produzent-/Konsument-Vorschau; Shift-Rechtsklick zeigt dann die vollstaendige Hop-Vorschau des tatsaechlich erreichten Kabelpfads mit direkten Produzent-/Konsument-Markierungen pro Hop sowie Blocker-Hinweisen, wenn ein Farbfilter-UEbergang den Kanal stoppt.
- Shift-Rechtsklick auf Coloured Redstone Cable zeigt nach einem Kanalwechsel sofort passende Blocker-Hinweise fuer gefilterte Nachbarn an.
- Erfasst werden benennbare Endpunkte wie Screen, XLAPI, Redstone I/O, Light Sensor, Clock, Rain Sensor, Material I/O, Crafting I/O und Crafting CPU.
- Relay-aktive XLAPI-Endpunkte auf dem lokalen Segment koennen zusaetzlich entfernte Endpunkte aus anderen geladenen Segmenten derselben Dimension mit gleicher Uplink-Gruppe als bridged Endpunkte bereitstellen.
- Remote importierte XLAPI-Bloecke selbst werden bewusst nicht in die erreichbare Device-Liste uebernommen; die Bridge-Konfiguration bleibt lokal.
- Pro relay-aktivem XLAPI-Block koennen gueltige entfernte Single-Computer-Segmente derselben Uplink-Gruppe als remote_computers sichtbar werden; ueber dieselbe Bridge koennen einfache Textnachrichten in eine persistente Computer-Mailbox zugestellt und strukturierte status-, ping-, devices- und runtime-Responses in derselben Mailbox gesammelt werden. Bridged redstone_io darf weiterhin remote schreiben, alle anderen aktuell importierten bridged Geraetetypen bleiben read-only.
- Mehrere Computer auf demselben Kabelsegment sind ungueltig. Soll mehr als ein Computer existieren, muessen die Segmente ueber XLAPI-Blocks getrennt werden.

## Warum GraalPy

Jython ist fuer Python-3-lastige Inhalte keine tragfaehige Basis mehr. GraalPy laesst sich auf Java 21 einbetten und passt damit deutlich besser zu NeoForge 1.21.1.

## Naechste sinnvolle Schritte

1. Auf dem neuen basisgestuetzten Drei-Wege-Merge fuer Recovery-Drafts weiter aufbauen und die verbleibenden Runtime-Kantenfaelle, feineren Policy-Stufen und Konflikt-UX-Details absichern.
2. Die neue Bridge-Policy und die strukturierten devices-/runtime-Responses mit expliziten Ingame-Szenarien und spaeteren Bridge-Tests absichern.
3. Crafting I/O als groesseres Rezept-Frontend und Material-/Crafting-Netzwerke zu mehrstufigen Automationsablaeufen ausbauen.
4. Mehrspieler-UX bei Konflikten weiter verfeinern, falls spaeter kooperative oder explizit uebernehmende Editiermodi gewuenscht sind.
5. Die Netz-GameTests spaeter bei Bedarf um Frontier-, Mehrsegment- und Misch-Topologien aus [docs/NETWORK_SCENARIOS.md](docs/NETWORK_SCENARIOS.md) erweitern.

## Toolchain

- Java 21 erforderlich
- NeoForge 21.1.218
- Minecraft 1.21.1
