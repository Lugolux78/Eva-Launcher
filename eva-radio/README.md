# Evadio – Tech Edition 0.2

Een **native Android-radioapp**. Eerst de techniek; alle skins volgen daarna onafhankelijk.

## Wat deze versie doet
- Live zenders zoeken in Nederland, België, Luxemburg, Duitsland en wereldwijd via de publieke Radio Browser API.
- Echte MP3-/AAC- en compatibele HLS-streams afspelen met Android `MediaPlayer`.
- Zenders selecteren, zoeken, als favoriet opslaan en eigen directe stream-URL toevoegen.
- Achtergrondweergave met foreground media-service, audiofocus, wake lock en vergrendelscherm-/notificatiebediening.
- Eenvoudige testinterface; alle kleurvariabelen zitten in `Skin.java`.

**Grenzen:** Radiostations beheren zelf hun streams, dus sommige links kunnen offline zijn, regiobeperkt zijn of een niet-ondersteunde audio-indeling hebben. De Radio Browser API is een onafhankelijke communitydienst zonder beschikbaarheidsgarantie. De daadwerkelijke audioweergave en de werking op fysieke Android-toestellen moeten nog worden getest.

## Bouw een APK

Open deze map in Android Studio of gebruik Android SDK 35, JDK 17 en Gradle 8.9:

```bash
gradle :app:assembleDebug
```

De debug-APK staat vervolgens in `app/build/outputs/apk/debug/app-debug.apk`. De GitHub Actions-workflow gebruikt dezelfde build op een Ubuntu-runner (in een bestaande repo onder `eva-radio/`).

## Technische opdeling
- `RadioService.java`: audioweergave, audiofocus, media-sessie en notificatie.
- `StationRepository.java`: actuele zendercatalogus + API-spiegels.
- `Station.java`: los gegevensmodel.
- `MainActivity.java`: tijdelijke schermen en favoriete zenders.
- `Skin.java`: kleuren en vormgeving, later te vervangen door verschillende skins.

**Fase 2:** skins kunnen het scherm opnieuw opbouwen zonder aan `RadioService` of `StationRepository` te komen.

## Privacy en netwerk
De app vraagt geen locatie- of accounttoegang. Zenderzoekopdrachten gaan naar Radio Browser en de gekozen radiostream naar de streamingserver van die zender. Voor oudere radiostations is onbeveiligde HTTP toegestaan (`usesCleartextTraffic=true`). Vermijd gevoelige persoonlijke gegevens in zoektermen.

## Licentie
Projectbroncode voor eigen gebruik; rechten op stationsnamen, logo's en uitzendingen berusten bij de betreffende rechthebbenden.

## Gewijzigd in 0.2
- Naam en meldingen: **Evadio**.
- Eén AudioFocusRequest blijft behouden bij zenderwissels.
- Elke selectie vervangt de actieve stream en start de gekozen zender.
- Versienummer: 0.2.0-tech.

**Tests:** broncode is aangepast; deze werkomgeving bevat geen Android SDK.
De Android-build en zenderwissels op een echt toestel zijn nog niet geverifieerd.

## 0.3 buildfix
Fixed the WalkmanView.Controller method signature to `onStopPlayback()` so the UI and Android Activity can compile.
Source code includes one real audio service, station selection, favorites, landscape skin and drawable. To install, a signed APK must be built.
