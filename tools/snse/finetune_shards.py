#!/usr/bin/env python3
"""LoRA fine-tuning for seven SNSE specialists; expects real before/after code pairs."""
import argparse, json, logging
from pathlib import Path
from typing import Any

import torch
from torch.utils.data import Dataset
from transformers import AutoTokenizer, AutoModelForCausalLM, TrainingArguments, Trainer
from peft import LoraConfig, get_peft_model, PeftModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("snse-finetune")
SHARDS = {
    "sqli": ["CWE-89"],
    "crypto": ["CWE-327", "CWE-326", "CWE-328", "CWE-330"],
    "auth": ["CWE-287", "CWE-306", "CWE-798", "CWE-259"],
    "network": ["CWE-295", "CWE-319", "CWE-297"],
    "storage": ["CWE-312", "CWE-522", "CWE-921"],
    "logging": ["CWE-532", "CWE-117"],
    "deserialization": ["CWE-502", "CWE-400"],
}
BASE_MODEL = "TinyLlama/TinyLlama-1.1B-Chat-v1.0"

class PairDataset(Dataset):
    def __init__(self, records: list[dict[str, Any]], tokenizer, max_len: int):
        self.rows = []
        self.tok, self.max_len = tokenizer, max_len
        for r in records:
            shard = next((name for name, cwes in SHARDS.items() if r["cwe"] in cwes), "specialist")
            prompt = ("### Task\nRepair the vulnerable source while preserving unrelated behavior.\n"
                      f"Specialist: {shard}\nCWE: {r['cwe']}\n"
                      "Return only a JSON object with patch (complete corrected source), confidence (0..1), and rationale. Do not execute code.\n"
                      f"### Vulnerable source\n{r['input']}\n### Corrected source JSON:\n")
            answer = json.dumps({"patch": r["output"], "confidence": 0.85,
                                 "rationale": "Proposed source-level mitigation; validate with tests."},
                                ensure_ascii=False) + tokenizer.eos_token
            p = tokenizer(prompt, add_special_tokens=True, truncation=False)
            a = tokenizer(answer, add_special_tokens=False, truncation=False)
            if len(p["input_ids"]) + len(a["input_ids"]) > max_len:
                continue  # Never train on a truncated patch or incomplete prompt.
            ids = p["input_ids"] + a["input_ids"]
            labels = [-100] * len(p["input_ids"]) + a["input_ids"]
            mask = [1] * len(ids)
            self.rows.append((ids, labels, mask))
    def __len__(self): return len(self.rows)
    def __getitem__(self, i): return self.rows[i]

class PreservePromptMaskCollator:
    """Pad batches without ever unmasking prompt labels."""
    def __init__(self, tokenizer): self.pad = tokenizer.pad_token_id
    def __call__(self, features):
        width = max(len(x[0]) for x in features)
        ids, labels, masks = [], [], []
        for x, y, m in features:
            n = width - len(x)
            ids.append(x + [self.pad] * n)
            labels.append(y + [-100] * n)
            masks.append(m + [0] * n)
        return {"input_ids": torch.tensor(ids), "labels": torch.tensor(labels),
                "attention_mask": torch.tensor(masks)}

def load_dataset(path: Path):
    records = []
    with path.open(encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            if not line.strip(): continue
            row = json.loads(line)
            if not all(isinstance(row.get(k), str) and row[k].strip() for k in ("input", "output", "cwe")):
                raise ValueError(f"Invalid record at line {line_no}: input/output/cwe are required")
            if row["input"].strip() == row["output"].strip(): continue
            records.append(row)
    return records

def finetune(name, cwes, records, out: Path, epochs: int, max_len: int):
    rows = [r for r in records if r["cwe"] in cwes]
    if len(rows) < 50:
        log.warning("%s: only %d valid pairs; skipped (minimum 50)", name, len(rows)); return False
    # Deterministic shuffle avoids source-order leakage into the train/validation split.
    import random
    rows = rows.copy(); random.Random(20261106).shuffle(rows)
    cut = max(1, min(len(rows)-1, int(len(rows)*0.9)))
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)
    if tokenizer.pad_token_id is None: tokenizer.pad_token = tokenizer.eos_token
    dtype = (torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16) if torch.cuda.is_available() else torch.float32
    model = AutoModelForCausalLM.from_pretrained(BASE_MODEL, torch_dtype=dtype)
    config = LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05,
                        target_modules=["q_proj", "v_proj", "k_proj", "o_proj"],
                        bias="none", task_type="CAUSAL_LM")
    model = get_peft_model(model, config)
    train_ds = PairDataset(rows[:cut], tokenizer, max_len)
    val_ds = PairDataset(rows[cut:], tokenizer, max_len)
    if len(train_ds) < 45 or len(val_ds) < 5:
        log.warning("%s: only %d train/%d validation sequences fit without truncation; skipped", name, len(train_ds), len(val_ds))
        return False
    dest = out / name; dest.mkdir(parents=True, exist_ok=True)
    args = TrainingArguments(
        output_dir=str(dest / "checkpoints"), num_train_epochs=epochs,
        per_device_train_batch_size=1, per_device_eval_batch_size=1,
        gradient_accumulation_steps=16, learning_rate=1e-4,
        warmup_ratio=0.03, logging_steps=10, save_strategy="epoch",
        eval_strategy="epoch", save_total_limit=1, fp16=(dtype == torch.float16 and torch.cuda.is_available()),
        bf16=(dtype == torch.bfloat16), report_to="none", remove_unused_columns=False,
        gradient_checkpointing=True, dataloader_pin_memory=torch.cuda.is_available())
    trainer = Trainer(model=model, args=args,
        train_dataset=train_ds,
        eval_dataset=val_ds,
        data_collator=PreservePromptMaskCollator(tokenizer))
    trainer.train()
    model.save_pretrained(dest); tokenizer.save_pretrained(dest)
    log.info("Adapter saved: %s", dest)
    return True

def merge_and_convert(shards_dir: Path, out_dir: Path, llama_dir: Path):
    import subprocess
    out_dir.mkdir(parents=True, exist_ok=True)
    converter = llama_dir / "convert_hf_to_gguf.py"
    if not converter.is_file(): raise FileNotFoundError(converter)
    for name in SHARDS:
        adapter = shards_dir / name
        if not (adapter / "adapter_config.json").is_file(): continue
        base = AutoModelForCausalLM.from_pretrained(BASE_MODEL, torch_dtype=torch.float16)
        merged = PeftModel.from_pretrained(base, adapter).merge_and_unload()
        merged_dir = adapter / "merged"
        merged.save_pretrained(merged_dir, safe_serialization=True)
        AutoTokenizer.from_pretrained(adapter).save_pretrained(merged_dir)
        subprocess.run(["python3", str(converter), str(merged_dir), "--outfile",
                        str(out_dir / f"{name}-f16.gguf"), "--outtype", "f16"], check=True)

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--data", required=True); p.add_argument("--out", default="models/finetuned")
    p.add_argument("--shard", choices=SHARDS); p.add_argument("--merge", action="store_true")
    p.add_argument("--epochs", type=int, default=3); p.add_argument("--max-len", type=int, default=1024)
    p.add_argument("--llama-dir", default="build/snse/llama.cpp")
    a = p.parse_args()
    if a.epochs not in range(1, 11) or a.max_len not in range(256, 8193): p.error("invalid epochs/max-len")
    records = load_dataset(Path(a.data)); out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    selected = {a.shard: SHARDS[a.shard]} if a.shard else SHARDS
    for name, cwes in selected.items(): finetune(name, cwes, records, out, a.epochs, a.max_len)
    if a.merge: merge_and_convert(out, Path("models/f16"), Path(a.llama_dir))
if __name__ == "__main__": main()
