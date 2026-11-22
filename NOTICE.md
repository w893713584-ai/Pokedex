# Third-Party Notices & Attribution

## 1. Original Project (Upstream)
- **Project**: [zihang-huang/iDex](https://github.com/zihang-huang/iDex) (iOS SwiftUI)
- **Author**: Zihang Huang (`zihang-huang`)
- **License**: MIT License (`app/src/main/assets/licenses/iDex-MIT.txt`)
- `iDex-Android` is a native Android (Kotlin + Jetpack Compose) port inspired by and derived from the architecture, retro Pokédex interaction design, and sound cues of `zihang-huang/iDex`.

## 2. Machine Learning Model
- **Base Model**: [`skshmjn/Pokemon-classifier-gen9-1025`](https://huggingface.co/skshmjn/Pokemon-classifier-gen9-1025) (ViT-Base fine-tuned on 1,025 Pokémon species)
- **License**: Apache License 2.0 (`app/src/main/assets/licenses/Apache-2.0.txt`)
- **Modifications**: Exported to ONNX (`opset=17`) with input normalization (`mean=0.5`, `std=0.5`) embedded in the graph and dynamically quantized to INT8 (`QUInt8`) via `scripts/model/export_1025_onnx.py`.
- **Packaged Artifact**: `app/src/main/assets/national_classifier.onnx`
  - **SHA-256**: `d67e748768f2dad11049d960026a2841a3c9061e7c150bc67884d448902b8988`

## 3. Pokédex Data, Sprites & Cries
- **Pokédex Metadata, Names & Stats**: [PokéAPI](https://github.com/PokeAPI/pokeapi) (`data/v2/csv`).
- **Simplified Chinese Pokédex Descriptions**: Cleaned and supplemented using public entries from [52Poké (神奇宝贝百科)](https://wiki.52poke.com/) via `scripts/data/patch_chinese_descriptions.py`.
- **Sprites**: PokéAPI sprite archive (Generation V Black & White pixel sprites for `#0001–#0649`, official pixel front renders / HOME pixel sprites for `#0650–#1025`).
- **Cries**: [PokéAPI/cries](https://github.com/PokeAPI/cries) (`app/src/main/assets/licenses/PokeAPI-Cries-LICENSE.txt`). Note: 1,022 files in `app/src/main/assets/cries/` are Ogg Vorbis streams; three entries (`pokemon-cry-0025.ogg`, `pokemon-cry-0808.ogg`, `pokemon-cry-0809.ogg`) store MP3 audio streams with `.ogg` extensions and are decoded natively by Android's `MediaPlayer`.

## 4. Bundled Fonts
- **Press Start 2P** — The Press Start 2P Project Authors, SIL Open Font License 1.1 (`app/src/main/assets/licenses/PressStart2P-OFL.txt`).
- **Fusion Pixel (12px proportional)** — TakWolf and contributors, SIL Open Font License 1.1 (`app/src/main/assets/licenses/FusionPixel-OFL.txt`, `https://github.com/TakWolf/fusion-pixel-font`).

## 5. Intellectual Property Disclaimer
Pokémon and all related character names, sprites, artwork, and cries are trademarks and copyrights of **Nintendo**, **Creatures Inc.**, **GAME FREAK inc.**, and **The Pokémon Company**.
`iDex-Android` is an unofficial, non-commercial fan project and is not affiliated with, endorsed by, or sponsored by any of those companies.
