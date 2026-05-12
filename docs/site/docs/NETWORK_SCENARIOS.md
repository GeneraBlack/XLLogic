# Network Scenarios

Reproduzierbare Ingame-Szenarien fuer Meilenstein 2.

Ziel dieses Dokuments ist nicht eine schoene Showcase-Welt, sondern eine belastbare manuelle Abnahme fuer lokale Discovery-Segmente, Computer-Konflikte und das Redstone-Busnetz mit Farbfiltern.

## Allgemeine Hinweise

- Alle beteiligten Chunks muessen geladen bleiben.
- Ein automatisierter Teil dieser Szenarien laeuft ueber `./gradlew.bat runGameTestServer`.
- Fuer aussagekraeftige Chat-Ausgaben sollten Endpunkte mit Namensschildern stabile Namen bekommen.
- Shift-Rechtsklick auf einen Computer aktualisiert Discovery und listet gefundene Endpunkte.
- Rechtsklick auf Network Cable zeigt eine kompakte Segmentzusammenfassung; Shift-Rechtsklick listet Computer, Endpunkte sowie XLAPI- und Frontier-Boundary-Hints im Detail auf.
- Rechtsklick auf Redstone Bus Cable zeigt die Kanalfluss-Zusammenfassung; Shift-Rechtsklick zeigt zusaetzlich Routenhops, Produzenten, Konsumenten sowie getrennte Blocker fuer Farbfilter, Geraetekanaele und ungeladene Frontiers.
- Shift-Rechtsklick auf Coloured Redstone Cable schaltet den Kanal weiter und zeigt direkt die aktuelle Kanalfluss-Sicht fuer den gewaehlten Kanal.

## Automatisierte Abdeckung

- Die aktuelle GameTest-Klasse liegt unter `src/main/java/de/xllogic/gametest/NetworkGameTests.java`.
- Die gemeinsame leere Testvorlage liegt unter `src/main/resources/data/xllogic/structure/networkgametests.network_empty_9x5x9.nbt`.
- Automatisiert abgedeckt sind derzeit diese Kernfaelle:
	- `discoveryConflictWithoutXlapi`: Szenario 2
	- `discoverySeparatedByXlapiBoundary`: Szenario 3
	- `busReportsDeviceChannelBlocker`: Szenario 4, speziell der device_channel-Blocker-Fall
	- `busReportsFilterBlockerForColoredMismatch`: Szenario 5
- Szenario 1, 6 und 7 bleiben aktuell bewusst als manuelle oder spaetere erweiterte Testfaelle offen.

## Szenario 1: Ein lokales Discovery-Segment

Aufbau:

1. Einen Computer, ein Network Cable, einen Screen und mindestens einen weiteren benennbaren Endpunkt in einem zusammenhaengenden lokalen Segment platzieren.
2. Sicherstellen, dass kein zweiter Computer im selben Kabelsegment haengt.

Pruefung:

1. Shift-Rechtsklick auf den Computer.
2. Rechtsklick und Shift-Rechtsklick auf eines der beteiligten Network Cables.

Erwartung:

- Discovery listet genau einen Computer im Segment.
- Der Screen wird ueber Discovery gebunden, nicht ueber manuelles Pairing.
- Die Network-Cable-Debugausgabe meldet die erwartete Anzahl von Kabeln, Computern und Endpunkten.
- Die Endpoint-Liste enthaelt die benannten lokalen Endpunkte mit korrektem Typ.

## Szenario 2: Lokaler Computer-Konflikt ohne XLAPI-Trennung

Aufbau:

1. Zwei Computer ueber dasselbe durchgehende lokale Network-Cable-Segment verbinden.
2. Optional zusaetzlich Screens oder weitere Endpunkte anhaengen.

Pruefung:

1. Shift-Rechtsklick auf einen der beiden Computer.
2. Shift-Rechtsklick auf ein Cable in der Mitte des Segments.

Erwartung:

- Die Cable-Debugausgabe meldet mehrere Computer im selben Segment.
- Discovery behandelt das lokale Segment als Konfliktfall.
- Wenn XLAPI-Grenzen oder ungeladene Frontier-Kanten angrenzen, erscheinen sie zusaetzlich als Boundary-Hints statt still zu verschwinden.
- Lokale Screen-Zuordnung und serverseitige Script-Ausfuehrung bleiben fuer dieses Segment verweigert oder konfliktbehaftet, bis die Trennung sauber ist.

## Szenario 3: XLAPI trennt zwei lokale Discovery-Segmente

Aufbau:

1. Segment A aufbauen: Computer A, Network Cable, mindestens ein lokaler Endpunkt.
2. Segment B aufbauen: Computer B, Network Cable, mindestens ein lokaler Endpunkt.
3. Beide Segmente nur ueber jeweils einen XLAPI-Block an die jeweilige Seite anschliessen, nicht ueber ein direktes durchgehendes Kabel.

Pruefung:

1. Shift-Rechtsklick auf Computer A und Computer B.
2. Shift-Rechtsklick auf ein Cable in Segment A und eines in Segment B.

Erwartung:

- Beide Computer sehen lokal jeweils nur ihr eigenes Segment.
- Keines der beiden lokalen Segmente meldet einen Multi-Computer-Konflikt.
- Die Discovery-Debugausgabe zeigt den angrenzenden XLAPI-Block explizit als lokale Segmentgrenze.
- XLAPI trennt die lokale Discovery-Sicht, statt ein gemeinsames lokales Segment entstehen zu lassen.

## Szenario 4: Mehrkanal-Bus ueber Redstone Bus Cable

Aufbau:

1. Zwei oder mehr Redstone-I/O-Bloecke ueber Redstone Bus Cable verbinden.
2. Einen Redstone-I/O als OUTPUT konfigurieren und auf einer Seite ein sichtbares Signal treiben.
3. Einen anderen Redstone-I/O als INPUT konfigurieren und auf derselben Bus-Seite denselben Kanal setzen.
4. Optional einen dritten Redstone-I/O mit anderem Kanal auf derselben Busleitung platzieren.

Pruefung:

1. Rechtsklick auf das Buskabel fuer die kompakte Kanaluebersicht.
2. Shift-Rechtsklick auf das Buskabel fuer Routenhops und Produzent-/Konsument-Markierungen.
3. Den Ausgangspegel oder Bus-Kanal aendern und die Eingangsseite erneut pruefen.

Erwartung:

- Der gewaehlte Kanal erscheint in der Bus-Debugausgabe mit Produzent und Konsument.
- Die staerkste Signalstaerke entspricht dem gesetzten Ausgangspegel.
- Ein Redstone-I/O auf anderem Kanal taucht nicht als Konsument fuer diesen Kanal auf.
- Wenn ein benachbarter Redstone-I/O auf der betroffenen Seite einen anderen Kanal nutzt, erscheint dafuer ein expliziter `device_channel`-Blocker statt eines stillen Auslassens.
- Kanalwechsel oder Pegelwechsel aktualisieren die betroffenen INPUT-Bloecke ueber das Busnetz.

## Szenario 5: Farbfilter blockieren benachbarte Kanaele

Aufbau:

1. Eine Busleitung mit mindestens zwei direkt benachbarten Coloured Redstone Cables aufbauen.
2. Das erste Coloured Cable auf Kanal X setzen.
3. Das zweite Coloured Cable auf Kanal Y setzen, wobei X ungleich Y ist.
4. Hinter dem zweiten Cable einen Redstone-I/O-INPUT oder eine weitere Busstrecke anschliessen.

Pruefung:

1. Rechtsklick auf das erste Coloured Cable.
2. Shift-Rechtsklick auf dasselbe Cable fuer Blocker-Hinweise.
3. Den zweiten Farbkanal auf X umstellen und die Pruefung wiederholen.

Erwartung:

- Bei ungleichen Kanaelen zeigt die Debugausgabe einen Blocker fuer den gefilterten Uebergang.
- Das Signal laeuft nicht ueber die Grenze zwischen den beiden Coloured Cables.
- Nach Angleichung beider Kanaele verschwindet der Blocker und der Kanalfluss ist wieder durchgaengig.
- Wenn Teile der Route ausserhalb geladener Chunks liegen, erscheinen diese zusaetzlich als Frontier-Blocker.

## Szenario 6: Gemischter Stamm mit zwei farbgetrennten Abzweigen

Aufbau:

1. Einen Redstone-Bus-Stamm aus Redstone Bus Cable bauen.
2. Zwei Abzweige mit Coloured Redstone Cable an denselben Stamm anschliessen.
3. Abzweig A auf Kanal 3 setzen und mit einem INPUT verbinden.
4. Abzweig B auf Kanal 11 setzen und mit einem zweiten INPUT verbinden.
5. Einen OUTPUT so konfigurieren, dass er nacheinander Kanal 3 und Kanal 11 treiben kann.

Pruefung:

1. Kanal 3 treiben und Bus-/Abzweig-Debugausgaben lesen.
2. Kanal 11 treiben und die Pruefung wiederholen.

Erwartung:

- Kanal 3 erreicht nur den Kanal-3-Abzweig.
- Kanal 11 erreicht nur den Kanal-11-Abzweig.
- Der gemeinsame Bus-Stamm bleibt physisch verbunden, aber die Coloured Cables trennen die logischen Kanaele sauber.

## Szenario 7: Discovery und Bus parallel im selben Aufbau

Aufbau:

1. Einen Computer mit lokalem Discovery-Segment fuer Screen und Endpunkte aufbauen.
2. Im selben Areal zusaetzlich ein getrenntes Busnetz aus Redstone Bus Cable und Redstone-I/O aufbauen.
3. Darauf achten, dass Discovery-Kabel und Bus-Kabel nicht versehentlich als dasselbe physische Netz interpretiert werden koennen.

Pruefung:

1. Discovery ueber den Computer aktualisieren.
2. Bus-Debug auf dem Redstone Bus Cable aufrufen.

Erwartung:

- Discovery findet nur Endpunkte am lokalen Network-Cable-Segment.
- Das Busnetz beeinflusst die Redstone-Kanaele, aber nicht die lokale Endpoint-Liste des Computers.
- Damit bleibt die Trennung zwischen physischem Discovery-Netz und logischem Redstone-Bus nachvollziehbar sichtbar.

## Abnahme-Checkliste fuer Meilenstein 2

- Discovery-Segmente sind ueber sichtbare Network Cables reproduzierbar.
- Mehrere Computer im selben lokalen Segment werden sicher als Konflikt erkannt.
- XLAPI trennt lokale Discovery-Segmente weiterhin sauber.
- Redstone Bus Cable transportiert den konfigurierten Kanal ueber mehrere Hops.
- Coloured Redstone Cable blockiert falsche Kanaele und laesst passende Kanaele durch.
- Gemischte Bus- und Farb-Topologien bleiben ueber die Debugausgaben nachvollziehbar.
- Die vier automatisierten Kernfaelle laufen ueber den GameTest-Server gruen.