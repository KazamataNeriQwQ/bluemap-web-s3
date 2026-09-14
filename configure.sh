#!/bin/sh
set -eu

: "${MAP_DATA_ROOT:?Set MAP_DATA_ROOT to your public CDN URL}"
: "${MAP_IDS:?Set MAP_IDS to comma-separated BlueMap map IDs}"
export LIVE_DATA_ROOT="${LIVE_DATA_ROOT:-$MAP_DATA_ROOT}"

# Generate JSON safely, without changing the official webapp.
jq -en '
  def root:
    if test("^https?://[^/\\s?#]+(/[^\\s?#]*)?$")
    then sub("/+$"; "")
    else error("Data roots must be absolute HTTP(S) URLs without queries or fragments") end;
  (env.MAP_IDS | split(",") | map(gsub("^\\s+|\\s+$"; ""))) as $maps |
  if ($maps | all(test("^[A-Za-z0-9_-]+$"))) then
    {
      version: "5.24",
      useCookies: true,
      mapDataRoot: (env.MAP_DATA_ROOT | root),
      liveDataRoot: (env.LIVE_DATA_ROOT | root),
      maps: $maps,
      clientDecompression: true
    }
  else error("MAP_IDS must contain comma-separated map IDs (letters, numbers, _ or -)") end
' > /usr/share/nginx/html/settings.json
