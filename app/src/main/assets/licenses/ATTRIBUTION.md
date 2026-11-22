# Offline catalog, model, and visual assets

The app bundles the complete 1025 National Pokédex species (Generations I through IX). Names, genera, English, Chinese, and Japanese Pokédex text, types, height, weight, and base stats were fetched from the PokéAPI data repository and supplemented with Simplified Chinese Pokédex descriptions from 52Poké:

- https://github.com/PokeAPI/pokeapi/tree/master/data/v2/csv
- API/data documentation: https://pokeapi.co/docs/v2
- Simplified Chinese descriptions: https://wiki.52poke.com/ (via `scripts/data/patch_chinese_descriptions.py`)

`assets/pokemon-national.json` stores metric values (metres and kilograms). Descriptions use the latest available entry for each language in the upstream data. English, Chinese, and Japanese text can therefore come from different game versions. The catalog is bundled; browsing and speech require no network calls.

Sprites are sourced from PokéAPI: Gen I–V (1–649) use the Generation V Black & White pixel sprite archive; Gen VI–IX (650–1025) use official pixel front renders, with HOME pixel fallbacks.

Cries for all 1025 species come from [PokéAPI/cries](https://github.com/PokeAPI/cries), using `cries/pokemon/latest/{id}.ogg`. The upstream repository credits Pokémon Showdown, Veekun, and The Sounds Resource. 1,022 files are Ogg Vorbis streams, while `pokemon-cry-0025.ogg`, `pokemon-cry-0808.ogg`, and `pokemon-cry-0809.ogg` contain MP3 audio streams with `.ogg` extensions; playback is handled natively by Android's `MediaPlayer`.

The upstream license is preserved in `assets/licenses/PokeAPI-Cries-LICENSE.txt`. It states that audio contents are copyright The Pokémon Company and that the repository is distributed under CC0 1.0; this does not transfer the underlying Pokémon audio rights.

The original iOS upstream MIT license (`zihang-huang/iDex`) is preserved in `assets/licenses/iDex-MIT.txt`.

The on-device recognition model (`assets/national_classifier.onnx`, SHA-256: `d67e748768f2dad11049d960026a2841a3c9061e7c150bc67884d448902b8988`) is an INT8 dynamic-quantized ONNX export of [`skshmjn/Pokemon-classifier-gen9-1025`](https://huggingface.co/skshmjn/Pokemon-classifier-gen9-1025) licensed under Apache License 2.0 (`assets/licenses/Apache-2.0.txt`), generated via `scripts/model/export_1025_onnx.py`.

Pokémon names, text, artwork, sprites, and cries are copyright and trademarks of Nintendo, Creatures Inc., GAME FREAK inc., and The Pokémon Company. Pokedex is an unofficial fan project and is not endorsed by those companies. These assets require appropriate rights review before any commercial distribution.

Three locally bundled pixel font faces in `res/font/` support all interface languages:

- **Press Start 2P**, The Press Start 2P Project Authors, SIL Open Font License 1.1 (`PressStart2P-OFL.txt`). https://github.com/google/fonts/tree/main/ofl/pressstart2p
- **Fusion Pixel**, TakWolf and contributors, SIL Open Font License 1.1 (`FusionPixel-OFL.txt`). Simplified Chinese and Japanese, 12 px proportional OTF, release 2026.10.16. https://github.com/TakWolf/fusion-pixel-font

To rebuild the catalog data, run `python3 scripts/data/build_catalog.py` followed by `python3 scripts/data/patch_chinese_descriptions.py` from the repository root. The compiled app performs no catalog or sprite downloads.
