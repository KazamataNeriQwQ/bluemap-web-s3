Unmodified BlueMap 5.24 webapp in Docker, serving BlueMap 5.7 + BlueMapS3Storage 1.5.1 maps directly from an R2 CDN.

## Setup

Mount a directory containing your `settings.json` at `/config:ro` (see `compose.yaml`). Set `mapDataRoot` and `liveDataRoot` to your public CDN URL, `clientDecompression: true`, and `maps` to your map IDs. Mounted settings take precedence and are served unchanged.

Alternatively, copy `.env.example` to `.env`: optional `MAP_DATA_ROOT` sets the CDN URL, `MAP_IDS` lists comma-separated map config filenames (without `.conf`), and `LIVE_DATA_ROOT` defaults to `MAP_DATA_ROOT`. With neither configuration, the map list is empty. No bucket keys in the container.

```sh
docker compose up -d --build
```

Open `http://localhost:8080`.

R2 bucket → CORS policy (no Cloudflare Worker needed):

```json
[{"AllowedOrigins":["*"],"AllowedMethods":["GET","HEAD"],"AllowedHeaders":["*"],"ExposeHeaders":["ETag"],"MaxAgeSeconds":3600}]
```

CDN [Cache Rule](https://developers.cloudflare.com/cache/how-to/configure-cache-status-code/), paths containing `/tiles/`: Edge TTL `200–299 = 240s`, `404 = no-cache (0)`; Browser TTL **Bypass cache**. Purge existing cache. Keep `/live/` uncached. Reload the map to see terrain updates; TTL is independent of Minecraft saves.

Install `bluemap-5.7-fabric.jar` and matching Fabric API in `mods/`; put `BlueMapS3Storage-1.5.1.jar` in `config/bluemap/packs/`.

Minecraft JVM flag (avoids S3Storage 1.5.1's AWS CRT/R2 signing failure):

```text
-Ds3.spi.client.custom-headers.enabled=true
```

Under `config/bluemap/`, change:

```hocon
# core.conf
accept-download: true

# storages/s3.conf
storage-type: "themeinerlp:s3"
bucket-name: "YOUR_BUCKET"
endpoint-url: "https://YOUR_ACCOUNT_ID.r2.cloudflarestorage.com"
access-key-id: "YOUR_KEY_ID"
secret-access-key: "YOUR_SECRET"
region: "auto"
force-path-style: true
compression: "gzip"
root-path: "/"

# maps/<map-id>.conf (each map)
storage: "s3"

# plugin.conf
write-markers-interval: 10
write-players-interval: 10

# webserver.conf and webapp.conf
enabled: false
```

Restart Minecraft. BlueMap can generate the map list in its local webapp `settings.json` when `webapp.conf` is enabled; S3Storage does not upload that file. Share or sync it to `/config/settings.json` for server-managed maps, with CDN roots configured in `webapp.conf`. BlueMap 5.7 does not emit `clientDecompression`; add `"clientDecompression": true` to each synced copy. Missing tiles outside rendered terrain may return 404; BlueMap skips them.
