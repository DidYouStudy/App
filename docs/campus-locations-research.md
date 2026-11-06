# EPFL campus room locations: research for issue 219

Findings on how to obtain room coordinates for the bundled `campus_locations.json`
(Sprint task "Campus Locations JSON & Repository"). This is a research record, not
shipped code. Nothing here is final.

## Short answer

- Room shapes and floors come from the EPFL geoportal search API, one room per request.
- Coordinates are in Swiss LV95 (EPSG:2056) and must be converted to WGS84 latitude/longitude.
- There is no complete room list on a public endpoint. A list of room names has to come from somewhere else.
- A single point per room (latitude, longitude) is enough for the repository API in the issue.

## Data sources

| Need | Source | Result |
|------|--------|--------|
| One room's shape and floor | `GET https://plan.epfl.ch/search?query=CM1121&limit=5&partitionlimit=5&interface=main&routing=false` | GeoJSON `FeatureCollection`, one `MultiPolygon` in LV95, plus `label` ("CM 1 121") and `floor` |
| Many rooms at once | WFS `https://plan.epfl.ch/mapserv_proxy?ogcserver=MapServer_JPEG&SERVICE=WFS&VERSION=1.1.0&REQUEST=GetFeature&TYPENAME=locaux_generaux&SRSNAME=EPSG:2056` | 4,536 rooms in one request (GML only, GeoJSON is refused). Fields: `room_abr`, `bat_abr`, `room_uti_a` (usage), `room_m2`. No floor. Misses most teaching rooms. |
| Room names | flep/occupancy `POST .../api/rooms/find_free_rooms` | Not usable: returns `403 Origin not allowed` |
| Ready-made list | `polysource-projects/lesswalkmorework` `rooms.json` | 168 rooms from October 2023. The repo has no license, so it was used to read names only, not copied |

The search API cannot list rooms. Queries like `BC`, `CM` or `INM` return the building (or people),
and `BC 0` returns nothing. `Auditoire` returns the 26 auditoriums.

## Coordinate conversion (LV95 to WGS84)

Swisstopo approximate formula (about 1 m accuracy). The blog post snippet multiplies by 10000
at the end, which is a typo; the correct factor is `100 / 36`.

```python
# Swiss LV95 (EPSG:2056) easting/northing -> WGS84 latitude/longitude.
def lv95_to_wgs84(e, n):
    y = (e - 2600000) / 1_000_000   # offset from Bern, in millions of metres
    x = (n - 1200000) / 1_000_000
    lon = 2.6779094 + 4.728982*y + 0.791484*y*x + 0.1306*y*x*x - 0.0436*y**3
    lat = 16.9023892 + 3.238272*x - 0.270978*y*y - 0.002528*x*x - 0.0447*y*y*x - 0.0140*x**3
    return lat * 100 / 36, lon * 100 / 36   # units of 10000", then to degrees
```

Check: CM 1 121 converted this way equals the blog author's output to about 1e-15 degrees.

## Results

- 168 reference rooms regenerated from the search API: 168 of 168 matched by exact label, 0 missing.
- The WFS layer holds only 48 of those 168 rooms (for example INM 202 and CM 1 121 are absent).
- Merging the WFS rooms, the 168 searched rooms and the 26 auditoriums gives 4,681 rooms, of which 290 have a study-related usage.

| Usage of the 290 study rooms | Rooms |
|------------------------------|------:|
| SALLE DE COURS | 97 |
| SALLE TP | 67 |
| AUDITOIRE | 54 |
| CONFERENCES | 48 |
| TUTORAT | 12 |
| CONF MULTIMEDIA | 7 |
| BIBLIOTHEQUE | 4 |
| REUNION | 1 |

`floor` is known only for rooms found through the search API (about 190 of the 290).

## Proposed record format

```json
{
  "name": "CM 1 121",
  "code": "CM1121",
  "zone": "CM",
  "type": "SALLE DE COURS",
  "floor": 1,
  "latitude": 46.520676,
  "longitude": 6.5656156
}
```

- `name` is the display label, `code` is the compact form used for case-insensitive lookup.
- `zone` is the building prefix (`BC`, `CM`, `INM`), which is what `findByZone` needs.
- `latitude`/`longitude` is the mean of the polygon corners, one point per room.
  For a concave room this point can fall outside the room; fine for small rooms.
- `floor` is nullable.
- An optional `polygon` (list of `[lat, lon]`) can be added later. A parser that ignores unknown keys is not broken by it.

Size: 290 study rooms as points is about 36 KB. The 168 rooms with polygons are about 91 KB,
and the same 168 without polygons about 21 KB.

## Open points

- **Complete room list.** A reliable list needs a source from EPFL or the team.
- **Terms of use.** `plan.epfl.ch/search` and the WFS endpoint are undocumented. Fine for generating a file once, not something the app should call at runtime.
- **Map tiles.** `tile.openstreetmap.org` rejects requests without a proper referrer and its policy forbids app use. swisstopo tiles (`wmts.geo.admin.ch`) worked for a local test page and are free with a "© swisstopo" credit.
- **Dependencies.** `gradle/libs.versions.toml` and `app/build.gradle.kts` have no JSON-serialization library and no map SDK yet.
