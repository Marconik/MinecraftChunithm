# 2026-10-25 six-song import

The following local HDD songs were packaged with metadata, metadata-free
300x300 covers, validated 48 kHz stereo Ogg Vorbis music, and playable chart
resources:

| ID | Title | Imported charts | Audio duration |
| ---: | --- | --- | ---: |
| 2966 | 月葬 | MASTER 15.5 | 164.864 s |
| 887 | SON OF SUN | MASTER 14.4, ULTIMA 15.1 | 106.816 s |
| 386 | 神威 | MASTER 15.2 | 151.594667 s |
| 393 | Dengeki Tube | MASTER 15.2 | 142.464 s |
| 1086 | 祈 -我ら神祖と共に歩む者なり- | MASTER 15.7 | 177.941333 s |
| 3000 | Linked Tune | MASTER 13.5 | 172.522667 s |

All six MASTER files and SON OF SUN's ULTIMA file passed source statistics,
timing, record-family, Slide and Air association checks without unclassified
records. A scan of every available `A***` package found no ULTIMA entry for
the other five songs; their selector ULTIMA slot remains visible but empty.
No difficulty was synthesized or relabelled.

The source HDD tree remains unchanged. Packaged assets are stored under
`assets/minecraftchunithm/{sounds,textures}/song/<four-digit ID>/`, with song
metadata and charts under `data/minecraftchunithm/songs/<four-digit ID>/`.

To launch the normal development client from the repository root:

```powershell
.\dev.ps1 runClient --offline
```

Inside a world, select the songs from the song panel or use commands such as:

```mcfunction
/chunithm chart play 2966 20 false
/chunithm chart play minecraftchunithm:songs/0887/ultima.json 20 false
```
