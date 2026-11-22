#!/usr/bin/env python3
"""Export the 1025-class Gen 1-9 Pokémon classifier to ONNX for Android.

Model: skshmjn/Pokemon-classifier-gen9-1025 (ViT-Base fine-tuned on 1025 Pokémon species).
Input: "image" float32 [1, 3, 224, 224], values in 0..1 RGB.
       Normalization (mean=0.5, std=0.5) is embedded inside the ONNX graph.
Output: "logits" float32 [1, 1025], aligned 1:1 with National Dex numbers 1..1025.
"""

import json
from pathlib import Path
import numpy as np
import torch
from PIL import Image
from transformers import ViTForImageClassification, ViTImageProcessor
import onnx
from onnxruntime.quantization import quantize_dynamic, QuantType
import onnxruntime as ort

MODEL_ID = "skshmjn/Pokemon-classifier-gen9-1025"
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"


class NormalizedViT(torch.nn.Module):
    def __init__(self, vit):
        super().__init__()
        self.vit = vit
        # Preprocessor config: mean=[0.5, 0.5, 0.5], std=[0.5, 0.5, 0.5]
        self.register_buffer("mean", torch.tensor([0.5, 0.5, 0.5]).reshape(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor([0.5, 0.5, 0.5]).reshape(1, 3, 1, 1))

    def forward(self, image):
        pixel_values = (image - self.mean) / self.std
        out = self.vit(pixel_values=pixel_values)
        return out.logits


def main():
    print(f"Loading {MODEL_ID}...")
    model = ViTForImageClassification.from_pretrained(MODEL_ID)
    model.eval()

    # Verify label count and dex mapping
    id2label = model.config.id2label
    assert len(id2label) == 1025, f"Expected 1025 classes, got {len(id2label)}"
    print(f"Verified {len(id2label)} classes: 0={id2label[0]}, 1024={id2label[1024]}")

    wrapped = NormalizedViT(model)
    wrapped.eval()

    dummy_input = torch.rand(1, 3, 224, 224, dtype=torch.float32)

    export_path = ASSETS / "national_classifier_fp32.onnx"
    quant_path = ASSETS / "national_classifier.onnx"

    print(f"Exporting FP32 ONNX to {export_path}...")
    torch.onnx.export(
        wrapped,
        dummy_input,
        str(export_path),
        input_names=["image"],
        output_names=["logits"],
        opset_version=17,
        do_constant_folding=True,
        dynamo=False,
    )
    print(f"FP32 exported ({export_path.stat().st_size / 1e6:.1f} MB)")

    print(f"Quantizing to INT8 for mobile edge speed: {quant_path}...")
    quantize_dynamic(
        model_input=str(export_path),
        model_output=str(quant_path),
        weight_type=QuantType.QUInt8,
    )
    print(f"Quantized model saved ({quant_path.stat().st_size / 1e6:.1f} MB)")

    # Generate full 1025 pokemon_labels.json
    labels_list = []
    for idx in range(1025):
        raw_name = id2label[idx]
        labels_list.append({
            "id": idx + 1, # Dex number is index + 1
            "name": raw_name.lower().replace(" ", "-"),
        })

    labels_file = ASSETS / "pokemon_labels.json"
    labels_file.write_text(json.dumps(labels_list, indent=2, ensure_ascii=False) + "\n")
    print(f"Saved {len(labels_list)} labels to {labels_file}")

    # Remove temporary fp32 file to save disk space if quant_path is valid
    if quant_path.exists() and quant_path.stat().st_size > 10_000_000:
        export_path.unlink()
        print("Cleaned up intermediate FP32 file.")

    # Verification on sample sprites
    print("Verifying quantized ONNX model...")
    session = ort.InferenceSession(str(quant_path), providers=["CPUExecutionProvider"])
    sprites_dir = ASSETS / "sprites"
    test_ids = [25, 152, 248, 255, 448, 658, 778, 813, 906, 1008, 1025]
    for pid in test_ids:
        sp = sprites_dir / f"pokemon-{pid:04d}.png"
        if not sp.exists():
            continue
        img = Image.open(sp).convert("RGBA")
        bg = Image.new("RGBA", img.size, (255, 255, 255, 255))
        bg.paste(img, (0, 0), img)
        rgb = bg.convert("RGB").resize((224, 224), Image.Resampling.BILINEAR)
        arr = np.asarray(rgb, dtype=np.float32).transpose(2, 0, 1)[None] / 255.0

        logits = session.run(None, {"image": arr})[0][0]
        top = int(np.argmax(logits))
        pred_dex = top + 1
        name = labels_list[top]["name"]
        print(f"Dex #{pid:04d} -> Predicted #{pred_dex:04d} ({name})")

    print("Done!")


if __name__ == "__main__":
    main()
