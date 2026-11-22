#!/usr/bin/env python3
"""Comprehensive audit & verification script for iDex Android dataset & assets."""

import json
import random
from pathlib import Path
from PIL import Image
import numpy as np
import onnxruntime as ort

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"

def test_dataset():
    print("=== 1. Checking pokemon-national.json ===")
    json_path = ASSETS / "pokemon-national.json"
    assert json_path.exists(), "pokemon-national.json missing"
    data = json.loads(json_path.read_text())
    assert len(data) == 1025, f"Expected 1025 entries, found {len(data)}"
    
    seen_ids = set()
    for idx, p in enumerate(data):
        pid = p["id"]
        seen_ids.add(pid)
        assert pid == idx + 1, f"Mismatch id {pid} at index {idx}"
        for field in ["chineseName", "englishName", "japaneseName",
                      "chineseGenus", "englishGenus", "japaneseGenus",
                      "chineseDescription", "englishDescription", "japaneseDescription"]:
            val = p.get(field, "")
            assert isinstance(val, str) and len(val.strip()) > 0, f"Pokemon #{pid} missing {field}"
        assert 1 <= p["generation"] <= 9, f"Invalid generation {p.get('generation')} for #{pid}"
        assert len(p.get("types", [])) > 0, f"Pokemon #{pid} has empty types"
        assert p.get("height", 0) > 0, f"Pokemon #{pid} has zero/negative height"
        assert p.get("weight", 0) > 0, f"Pokemon #{pid} has zero/negative weight"
    
    assert seen_ids == set(range(1, 1026))

    # Verify 100% of Chinese descriptions have Chinese characters and zero English words
    import re
    has_cn = re.compile(r'[\u4e00-\u9fff]')
    has_en = re.compile(r'[a-zA-Z]{3,}')
    for p in data:
        pid = p["id"]
        zh_desc = p.get("chineseDescription", "")
        assert has_cn.search(zh_desc), f"Pokemon #{pid} ({p['englishName']}) lacks Chinese characters"
        assert not has_en.search(zh_desc), f"Pokemon #{pid} ({p['englishName']}) contains English words: {zh_desc}"

    print("✅ All 1025 entries in pokemon-national.json are valid, complete, and 100% Chinese with 0 English words.")

def test_labels():
    print("=== 2. Checking pokemon_labels.json ===")
    labels_path = ASSETS / "pokemon_labels.json"
    assert labels_path.exists(), "pokemon_labels.json missing"
    labels = json.loads(labels_path.read_text())
    assert len(labels) == 1025, f"Expected 1025 labels, found {len(labels)}"
    for idx, item in enumerate(labels):
        assert item["id"] == idx + 1, f"Label id mismatch at index {idx}"
        assert len(item["name"]) > 0, f"Empty label name at index {idx}"
    print("✅ All 1025 labels in pokemon_labels.json are strictly ordered and valid.")

def test_sprites_and_cries():
    print("=== 3. Checking 1025 Sprites and 1025 Cries ===")
    sprites_dir = ASSETS / "sprites"
    cries_dir = ASSETS / "cries"
    
    missing_sprites = []
    missing_cries = []
    
    for pid in range(1, 1026):
        sprite_f = sprites_dir / f"pokemon-{pid:04d}.png"
        if not sprite_f.exists() or sprite_f.stat().st_size == 0:
            missing_sprites.append(pid)
        
        cry_f = cries_dir / f"pokemon-cry-{pid:04d}.ogg"
        if not cry_f.exists() or cry_f.stat().st_size == 0:
            missing_cries.append(pid)
            
    assert not missing_sprites, f"Missing sprites for IDs: {missing_sprites[:10]}"
    assert not missing_cries, f"Missing cries for IDs: {missing_cries[:10]}"
    
    # Sample check 10 sprites can be opened as images
    for pid in random.sample(range(1, 1026), 10):
        img = Image.open(sprites_dir / f"pokemon-{pid:04d}.png")
        assert img.size[0] > 0 and img.size[1] > 0
    
    print("✅ All 1025 PNG sprites and 1025 OGG cries are present and non-empty.")

def test_model_inference():
    print("=== 4. Checking national_classifier.onnx Model Inference ===")
    model_path = ASSETS / "national_classifier.onnx"
    assert model_path.exists(), "national_classifier.onnx missing"
    print(f"Model size: {model_path.stat().st_size / 1e6:.1f} MB")
    
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    inputs = session.get_inputs()
    outputs = session.get_outputs()
    
    assert len(inputs) == 1, f"Expected 1 input, got {len(inputs)}"
    assert inputs[0].name == "image", f"Input name mismatch: {inputs[0].name}"
    assert inputs[0].shape == [1, 3, 224, 224], f"Input shape mismatch: {inputs[0].shape}"
    
    assert len(outputs) == 1, f"Expected 1 output, got {len(outputs)}"
    assert outputs[0].name == "logits", f"Output name mismatch: {outputs[0].name}"
    assert outputs[0].shape == [1, 1025], f"Output shape mismatch: {outputs[0].shape}"
    
    # Test sample sprites from all 9 generations
    sprites_dir = ASSETS / "sprites"
    test_suite = [
        (1, "Bulbasaur", 1),
        (25, "Pikachu", 1),
        (151, "Mew", 1),
        (152, "Chikorita", 2),
        (250, "Ho-Oh", 2),
        (255, "Torchic", 3),
        (384, "Rayquaza", 3),
        (448, "Lucario", 4),
        (493, "Arceus", 4),
        (501, "Oshawott", 5),
        (649, "Genesect", 5),
        (658, "Greninja", 6),
        (721, "Volcanion", 6),
        (722, "Rowlet", 7),
        (778, "Mimikyu", 7),
        (809, "Melmetal", 7),
        (813, "Scorbunny", 8),
        (888, "Zacian", 8),
        (906, "Sprigatito", 9),
        (1008, "Miraidon", 9),
        (1025, "Pecharunt", 9),
    ]
    
    correct = 0
    for pid, expected_name, gen in test_suite:
        sp = sprites_dir / f"pokemon-{pid:04d}.png"
        img = Image.open(sp).convert("RGBA")
        bg = Image.new("RGBA", img.size, (255, 255, 255, 255))
        bg.paste(img, (0, 0), img)
        rgb = bg.convert("RGB").resize((224, 224), Image.Resampling.BILINEAR)
        arr = np.asarray(rgb, dtype=np.float32).transpose(2, 0, 1)[None] / 255.0
        
        logits = session.run(None, {"image": arr})[0][0]
        pred_idx = int(np.argmax(logits))
        pred_dex = pred_idx + 1
        
        # Softmax
        exp_l = np.exp(logits - np.max(logits))
        conf = exp_l[pred_idx] / np.sum(exp_l)
        
        is_match = (pred_dex == pid)
        if is_match:
            correct += 1
        mark = "✅" if is_match else "❌"
        print(f" {mark} Gen {gen} | Dex #{pid:04d} ({expected_name}) -> Predicted #{pred_dex:04d}, Conf: {conf:.1%}")
    
    accuracy = correct / len(test_suite)
    print(f"Test Suite Accuracy: {correct}/{len(test_suite)} ({accuracy:.1%})")
    assert accuracy >= 0.90, f"Accuracy {accuracy:.1%} lower than 90%"
    print("✅ Model inference successfully verified across all 9 generations!")

if __name__ == "__main__":
    test_dataset()
    test_labels()
    test_sprites_and_cries()
    test_model_inference()
    print("\n🎉 ALL AUDIT CHECKS PASSED PERFECTLY!")
