#!/usr/bin/env python3
"""
build_catalog.py
Downloads PokéAPI National Pokédex data for Pokémon #1 to #1025.
Outputs:
  - app/src/main/assets/pokemon-national.json
  - app/src/main/assets/sprites/pokemon-XXXX.png (96x96 pixel sprites)
  - app/src/main/assets/cries/pokemon-cry-XXXX.ogg (cries)
"""

import os
import io
import csv
import json
import re
import tarfile
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from PIL import Image
import opencc

CACHE_DIR = "/tmp/pokeapi-cache"
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
ASSETS_DIR = os.path.join(PROJECT_ROOT, "app/src/main/assets")
SPRITES_DIR = os.path.join(ASSETS_DIR, "sprites")
CRIES_DIR = os.path.join(ASSETS_DIR, "cries")
OUTPUT_JSON = os.path.join(ASSETS_DIR, "pokemon-national.json")

os.makedirs(CACHE_DIR, exist_ok=True)
os.makedirs(SPRITES_DIR, exist_ok=True)
os.makedirs(CRIES_DIR, exist_ok=True)

USER_AGENT = "iDex-Android-NationalCatalogBuilder/2.0"

t2s_converter = opencc.OpenCC("t2s")

def download_file(url, dest_path):
    if os.path.exists(dest_path) and os.path.getsize(dest_path) > 0:
        return dest_path
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        content = resp.read()
    with open(dest_path, "wb") as f:
        f.write(content)
    return dest_path

def get_csv(name):
    url = f"https://raw.githubusercontent.com/PokeAPI/pokeapi/master/data/v2/csv/{name}"
    local_path = os.path.join(CACHE_DIR, name)
    download_file(url, local_path)
    with open(local_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return list(reader)

def clean_flavor_text(text):
    if not text:
        return ""
    # Replace line breaks, form feeds, and non-breaking spaces
    text = text.replace("\x0c", " ").replace("\n", " ").replace("\r", " ").replace("\u00ad", "")
    text = re.sub(r"\s+", " ", text).strip()
    return text

def build_data():
    print(">>> 1/4 Fetching PokéAPI CSV database...")
    species_rows = get_csv("pokemon_species.csv")
    species_names_rows = get_csv("pokemon_species_names.csv")
    flavor_rows = get_csv("pokemon_species_flavor_text.csv")
    pokemon_rows = get_csv("pokemon.csv")
    pokemon_types_rows = get_csv("pokemon_types.csv")
    types_rows = get_csv("types.csv")
    pokemon_stats_rows = get_csv("pokemon_stats.csv")
    stats_rows = get_csv("stats.csv")

    type_map = {r["id"]: r["identifier"] for r in types_rows}
    stat_map = {r["id"]: r["identifier"] for r in stats_rows}

    # Filter species #1 to #1025
    species_by_id = {}
    for r in species_rows:
        sid = int(r["id"])
        if 1 <= sid <= 1025:
            species_by_id[sid] = r

    # Names and genera by species_id and language_id
    # lang 9 = en, 11 = ja, 1 = ja-hrkt, 12 = zh-hans, 4 = zh-hant
    names_by_species = {}
    for r in species_names_rows:
        sid = int(r["pokemon_species_id"])
        if 1 <= sid <= 1025:
            lid = int(r["local_language_id"])
            if sid not in names_by_species:
                names_by_species[sid] = {}
            names_by_species[sid][lid] = {
                "name": r["name"].strip(),
                "genus": r["genus"].strip()
            }

    # Flavor texts by species_id and language_id, picking highest version_id
    flavor_by_species = {}
    for r in flavor_rows:
        sid = int(r["species_id"])
        if 1 <= sid <= 1025:
            lid = int(r["language_id"])
            vid = int(r["version_id"])
            text = clean_flavor_text(r["flavor_text"])
            if not text:
                continue
            if sid not in flavor_by_species:
                flavor_by_species[sid] = {}
            if lid not in flavor_by_species[sid] or vid > flavor_by_species[sid][lid][0]:
                flavor_by_species[sid][lid] = (vid, text)

    # Pokemon details (height, weight) from default forms
    # Some species have forms, find default pokemon for species
    pokemon_by_species = {}
    for r in pokemon_rows:
        sid = int(r["species_id"])
        if 1 <= sid <= 1025:
            pid = int(r["id"])
            is_default = r["is_default"] == "1"
            if sid not in pokemon_by_species or is_default:
                pokemon_by_species[sid] = {
                    "pokemon_id": pid,
                    "height": round(int(r["height"]) / 10.0, 2),
                    "weight": round(int(r["weight"]) / 10.0, 2),
                }

    # Types by pokemon_id
    types_by_pokemon = {}
    for r in pokemon_types_rows:
        pid = int(r["pokemon_id"])
        slot = int(r["slot"])
        tid = r["type_id"]
        tname = type_map.get(tid, "normal")
        if pid not in types_by_pokemon:
            types_by_pokemon[pid] = []
        types_by_pokemon[pid].append((slot, tname))

    for pid in types_by_pokemon:
        types_by_pokemon[pid].sort(key=lambda x: x[0])
        types_by_pokemon[pid] = [x[1] for x in types_by_pokemon[pid]]

    # Stats by pokemon_id
    stats_by_pokemon = {}
    for r in pokemon_stats_rows:
        pid = int(r["pokemon_id"])
        sid = r["stat_id"]
        val = int(r["base_stat"])
        sname = stat_map.get(sid, "unknown")
        if pid not in stats_by_pokemon:
            stats_by_pokemon[pid] = {}
        stats_by_pokemon[pid][sname] = val

    print(">>> 2/4 Compiling 1025 Pokémon entries...")
    catalog = []
    missing_zh_name = 0
    missing_zh_desc = 0
    missing_ja_desc = 0

    desired_stats = ["hp", "attack", "defense", "special-attack", "special-defense", "speed"]

    for sid in range(1, 1026):
        spec = species_by_id.get(sid, {})
        gen_id = int(spec.get("generation_id", 1))

        # Names
        name_langs = names_by_species.get(sid, {})
        en_entry = name_langs.get(9, {"name": f"Pokemon #{sid}", "genus": "Pokémon"})
        en_name = en_entry["name"]
        en_genus = en_entry["genus"]

        # JA name: 11 (kanji) -> 1 (kana) -> en
        if 11 in name_langs and name_langs[11]["name"]:
            ja_name = name_langs[11]["name"]
            ja_genus = name_langs[11]["genus"]
        elif 1 in name_langs and name_langs[1]["name"]:
            ja_name = name_langs[1]["name"]
            ja_genus = name_langs[1]["genus"]
        else:
            ja_name = en_name
            ja_genus = en_genus

        # ZH name: 12 (simplified) -> 4 (traditional -> simplified) -> en
        if 12 in name_langs and name_langs[12]["name"]:
            zh_name = name_langs[12]["name"]
            zh_genus = name_langs[12]["genus"]
        elif 4 in name_langs and name_langs[4]["name"]:
            zh_name = t2s_converter.convert(name_langs[4]["name"])
            zh_genus = t2s_converter.convert(name_langs[4]["genus"])
        else:
            zh_name = en_name
            zh_genus = en_genus
            missing_zh_name += 1

        # Description
        f_langs = flavor_by_species.get(sid, {})
        en_desc = f_langs.get(9, (0, f"A Pokémon discovered in generation {gen_id}."))[1]

        # JA desc: 11 -> 1 -> en
        if 11 in f_langs:
            ja_desc = f_langs[11][1]
        elif 1 in f_langs:
            ja_desc = f_langs[1][1]
        else:
            ja_desc = en_desc
            missing_ja_desc += 1

        # ZH desc: 12 -> 4 (t2s) -> en
        if 12 in f_langs:
            zh_desc = f_langs[12][1]
        elif 4 in f_langs:
            zh_desc = t2s_converter.convert(f_langs[4][1])
        else:
            zh_desc = en_desc
            missing_zh_desc += 1

        p_info = pokemon_by_species.get(sid, {"pokemon_id": sid, "height": 1.0, "weight": 10.0})
        pid = p_info["pokemon_id"]
        height = p_info["height"]
        weight = p_info["weight"]

        types = types_by_pokemon.get(pid, ["normal"])
        stat_dict = stats_by_pokemon.get(pid, {})
        stats = [{"name": s, "value": stat_dict.get(s, 50)} for s in desired_stats]

        entry = {
            "id": sid,
            "generation": gen_id,
            "englishName": en_name,
            "chineseName": zh_name,
            "japaneseName": ja_name,
            "types": types,
            "height": height,
            "weight": weight,
            "englishDescription": en_desc,
            "chineseDescription": zh_desc,
            "japaneseDescription": ja_desc,
            "englishGenus": en_genus,
            "chineseGenus": zh_genus,
            "japaneseGenus": ja_genus,
            "stats": stats
        }
        catalog.append(entry)

    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
    print(f"Saved {len(catalog)} entries to {OUTPUT_JSON}")

    print(f"Statistics: missing ZH name: {missing_zh_name}, missing ZH desc: {missing_zh_desc}, missing JA desc: {missing_ja_desc}")
    return catalog

def process_cries():
    print(">>> 3/4 Processing Pokémon cries (OGG)...")
    cries_tar_url = "https://codeload.github.com/PokeAPI/cries/tar.gz/refs/heads/main"
    tar_path = os.path.join(CACHE_DIR, "cries-main.tar.gz")
    download_file(cries_tar_url, tar_path)

    # Clean old wav files
    old_wavs = [f for f in os.listdir(CRIES_DIR) if f.endswith(".wav")]
    for w in old_wavs:
        os.remove(os.path.join(CRIES_DIR, w))
    if old_wavs:
        print(f"Removed {len(old_wavs)} legacy .wav cries.")

    extracted = 0
    with tarfile.open(tar_path, "r:gz") as tar:
        for member in tar.getmembers():
            if not member.isfile():
                continue
            # cries-main/cries/pokemon/latest/25.ogg
            parts = member.name.split("/")
            if len(parts) >= 5 and parts[1] == "cries" and parts[2] == "pokemon" and parts[3] == "latest":
                filename = parts[4]
                if filename.endswith(".ogg"):
                    dex_id_str = filename[:-4]
                    if dex_id_str.isdigit():
                        dex_id = int(dex_id_str)
                        if 1 <= dex_id <= 1025:
                            dest_name = f"pokemon-cry-{dex_id:04d}.ogg"
                            dest_file = os.path.join(CRIES_DIR, dest_name)
                            f = tar.extractfile(member)
                            if f:
                                with open(dest_file, "wb") as out:
                                    out.write(f.read())
                                extracted += 1
    print(f"Extracted {extracted}/1025 cries to {CRIES_DIR}")

def download_one_sprite(sid):
    dest_file = os.path.join(SPRITES_DIR, f"pokemon-{sid:04d}.png")
    if os.path.exists(dest_file) and os.path.getsize(dest_file) > 100:
        return sid, True

    # Priority 1: Gen V BW sprite for 1-649
    urls = []
    if 1 <= sid <= 649:
        urls.append(f"https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/versions/generation-v/black-white/{sid}.png")
    urls.append(f"https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/{sid}.png")
    urls.append(f"https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/home/{sid}.png")

    for url in urls:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=15) as resp:
                data = resp.read()
                if len(data) > 100:
                    img = Image.open(io.BytesIO(data))
                    # Resize to 96x96 nearest neighbor if home sprite
                    if "home" in url or img.size != (96, 96):
                        img = img.resize((96, 96), Image.Resampling.NEAREST)
                    img.save(dest_file, "PNG")
                    return sid, True
        except Exception:
            continue
    return sid, False

def process_sprites():
    print(">>> 4/4 Downloading Pokémon sprites (1025 PNGs)...")
    # Clean old 3-digit sprites
    old_sprites = [f for f in os.listdir(SPRITES_DIR) if re.match(r"^pokemon-\d{3}\.png$", f)]
    for s in old_sprites:
        os.remove(os.path.join(SPRITES_DIR, s))
    if old_sprites:
        print(f"Removed {len(old_sprites)} legacy 3-digit sprites.")

    success_count = 0
    missing = []
    with ThreadPoolExecutor(max_workers=16) as pool:
        futures = {pool.submit(download_one_sprite, sid): sid for sid in range(1, 1026)}
        for fut in as_completed(futures):
            sid, ok = fut.result()
            if ok:
                success_count += 1
            else:
                missing.append(sid)

    print(f"Downloaded {success_count}/1025 sprites. Missing: {len(missing)}")
    if missing:
        print(f"Missing sprite IDs: {missing[:10]}...")

if __name__ == "__main__":
    build_data()
    process_cries()
    process_sprites()
    print(">>> B1 DATA PIPELINE COMPLETE!")
