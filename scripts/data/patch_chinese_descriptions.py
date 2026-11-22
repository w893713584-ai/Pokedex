#!/usr/bin/env python3
"""Patch all 1025 Pokemon in pokemon-national.json with 100% official Simplified Chinese descriptions."""

import glob
import json
import re
from pathlib import Path
import opencc

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"
NATIONAL_JSON = ASSETS / "pokemon-national.json"
DATASET_DIR = Path("/tmp/pokemon-dataset-zh/data/pokemon")

t2s = opencc.OpenCC("t2s")

# Official Pokedex flavor texts for the 16 species whose 52poke entries were split across forms
FALLBACK_16 = {
    417: "偶尔能看到把储存了静电啪吱啪吱作响的毛球和喜欢的树果一起藏在树洞里。",
    496: "像在地面滑行一样奔跑。用敏捷的动作戏弄敌人，然后用藤鞭给出一击。",
    651: "用厚厚的甲壳反弹敌人的攻击，再用锐利的尖刺进行反击。",
    652: "拥有能承受炸弹爆炸的顽强防御力。会用自己的身体保护伙伴。",
    668: "群体中拥有最大火焰鬃毛的雄性是首领。雌性会齐心协力捕捉猎物。",
    723: "投掷像刀刃般锋利的羽毛准确无误地命中对手。非常注重羽毛的清洁。",
    724: "将藏在翅膀里的箭羽射向对手。哪怕是100米外的猎物也能精准贯穿。",
    726: "脖子根部长着火焰袋。情绪高涨时体温升高，会发出铃铛般的声音。",
    727: "斗志高昂时，体内制造的火焰就会从肚脐和腰部周围喷射出来。",
    729: "通过跳舞制造一个个水气球。随着节奏让气球破裂来攻击敌人。",
    732: "会用坚硬的鸟喙在树木上凿洞储藏树果，也能通过敲击发出声音传递心情。",
    745: "以敏捷的动作戏弄猎物。在与敌人擦身而过的一瞬间用岩石鬃毛狠狠刺向对手。",
    803: "究极异兽的一种。从头部的毒针里喷出毒液，发出咯咯的笑声。",
    804: "体内寄宿着无数的毒素。下半身巨大的毒针能发射高压毒液。",
    805: "看上去像是一堵石墙，但每一块石头都是拥有自我意识的生命体。",
    806: "会把自己的头部像烟花一样引爆来惊吓对手，并趁机夺取对手的生命能量。",
}


def clean_text(text: str) -> str:
    if not text:
        return ""
    text = t2s.convert(text)
    text = text.replace("\x0c", " ").replace("\n", " ").replace("\r", " ").replace("\u00ad", "")
    text = text.replace("牠", "它").replace("祂", "它")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def main():
    print(f"Loading {NATIONAL_JSON}...")
    with open(NATIONAL_JSON, "r", encoding="utf-8") as f:
        catalog = json.load(f)

    assert len(catalog) == 1025, f"Expected 1025 entries, got {len(catalog)}"

    print(f"Loading Chinese dataset from {DATASET_DIR}...")
    files = glob.glob(str(DATASET_DIR / "*.json"))
    by_id = {}
    for f in files:
        with open(f, "r", encoding="utf-8") as fp:
            d = json.load(fp)
            pid = int(d.get("pokedex_id", 0))
            if pid > 0:
                by_id[pid] = d

    assert len(by_id) == 1025, f"Expected 1025 files in dataset, got {len(by_id)}"

    patched_count = 0
    has_cn = re.compile(r"[\u4e00-\u9fff]")
    has_en = re.compile(r"[a-zA-Z]{3,}")

    for p in catalog:
        pid = p["id"]
        old_desc = p.get("chineseDescription", "")
        had_english = bool(has_en.search(old_desc) or not has_cn.search(old_desc))

        if pid in FALLBACK_16:
            new_desc = FALLBACK_16[pid]
        else:
            d = by_id[pid]
            entries = d.get("pokedex_entries", [])
            all_texts = []
            for gen in entries:
                for v in gen.get("versions", []):
                    t = v.get("text", "").strip()
                    if t:
                        all_texts.append(t)
            if all_texts:
                # Latest official game version description
                new_desc = all_texts[-1]
            else:
                new_desc = d.get("description", "")

        new_desc = clean_text(new_desc)
        p["chineseDescription"] = new_desc

        if had_english:
            patched_count += 1

    print(f"Patched {patched_count} entries that previously had English descriptions.")

    # Validation: ALL 1025 MUST be 100% Chinese with NO English words
    for p in catalog:
        pid = p["id"]
        desc = p["chineseDescription"]
        assert has_cn.search(desc), f"Pokemon #{pid} ({p['englishName']}) lacks Chinese characters: {desc}"
        assert not has_en.search(desc), f"Pokemon #{pid} ({p['englishName']}) contains English words: {desc}"
        assert len(desc) >= 8, f"Pokemon #{pid} description too short: {desc}"

    print("✅ All 1025 Chinese descriptions strictly validated: 0 English words, 100% Chinese!")

    # Sample inspect Snivy #495 and neighbors
    for test_id in [494, 495, 496, 497]:
        tp = catalog[test_id - 1]
        print(f"#{tp['id']:04d} {tp['chineseName']}: {tp['chineseDescription']}")

    with open(NATIONAL_JSON, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)

    print(f"Successfully saved updated catalog to {NATIONAL_JSON} ({NATIONAL_JSON.stat().st_size / 1e6:.2f} MB)")


if __name__ == "__main__":
    main()
